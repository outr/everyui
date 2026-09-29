package everyui.compiler

import scala.quoted.*
import scala.tasty.inspector.*
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import scala.collection.mutable

/** everyui's Roku compiler: Scala 3 TASTy → a Roku channel.
 *
 *  A channel is a `manifest`, a BrightScript `Main()` under `source` that boots
 *  an `roSGScreen`, and SceneGraph components under `components`: the generated
 *  `AppScene`, `App.brs` (the translated program) and `ScalaRuntime.brs` (the
 *  Scala semantics BrightScript lacks — equality and identity, collections,
 *  Option/Try, closures, exceptions, the object model's dispatch).
 *
 *  BrightScript has NO lexical closures, so a Scala lambda is DEFUNCTIONALISED:
 *  each becomes a top-level `function closureN(env, args)` plus a value
 *  `{ fn: closureN, env: { self: self } }`, invoked through the runtime
 *  `RtInvoke(cl, args)`. User classes become assoc arrays carrying their
 *  method function-refs (`self.build`), methods become `Class_method(self, …)`.
 *
 *  Unhandled trees emit a tracked `RtTodo` so the channel always builds
 *  (`tools/emit-gaps.py` counts them).
 */
class RokuEmitter(
  outDir: Path,
  sourceRoot: Option[Path] = None,
  projectName: String = "app"
) extends Emitter {

  private val out      = new StringBuilder   // emitted app BrightScript
  private val closures = new StringBuilder    // defunctionalised lambda bodies
  private val todos    = mutable.ListBuffer[String]()

  def unsupported: List[String] = todos.toList.distinct

  def inspect(using q: Quotes)(tastys: List[Tasty[q.type]]): Unit = {
    import q.reflect.*

    // ── helpers ──────────────────────────────────────────────────────────
    def hasNative(s: Symbol): Boolean =
      s.exists && s.annotations.exists(a => a.tpe.typeSymbol.fullName == "everyui.compiler.api.native")

    val mainFqns = Set("scala.main", "scala.annotation.main", "scala.annotation.newMain")
    def isMain(s: Symbol): Boolean =
      s.exists && s.annotations.exists(a => mainFqns.contains(a.tpe.typeSymbol.fullName))

    def isBuiltin(s: Symbol): Boolean =
      s.exists && (s.fullName.startsWith("scala.") || s.fullName.startsWith("java."))

    def unwrap(t: Term): Term = t match {
      case Inlined(_, _, e) => unwrap(e)
      case Typed(e, _)      => unwrap(e)
      case _                => t
    }

    def isUnit(t: Term): Boolean = unwrap(t) match {
      case Literal(UnitConstant()) => true
      case Block(Nil, e)           => isUnit(e)
      case _                       => false
    }

    def isSuper(t: Term): Boolean = t match {
      case Super(_, _) => true
      case _           => false
    }

    // BrightScript string literal: escape `"` by doubling; splice newlines, carriage returns and tabs, which a device
    // refuses inside a literal (brighterscript does not).
    def brStr(s: String): String = {
      val esc = s.replace("\"", "\"\"").replace("\n", "\" + Chr(10) + \"").replace("\r", "\" + Chr(13) + \"")
        .replace("\t", "\" + Chr(9) + \"")
      "\"" + esc + "\""
    }

    /** What `case x: SomeClass` tests against: the class's own name and its ancestors', between bars, so a subclass
      * answers to a parent's name. The universal ones are left out — everything is one of those. */
    val universal: Set[String] = Set("Object", "Any", "AnyRef", "Matchable", "Serializable")

    def isaTag(sym: Symbol): String = {
      val names = (try sym.typeRef.baseClasses catch case _: Throwable => List(sym))
        .filter(bc => bc.exists && !universal.contains(bc.name)).map(_.name).distinct
      if (names.isEmpty) "" else names.mkString("|", "|", "|")
    }

    def emitConst(c: Constant): String = c match {
      case IntConstant(i)     => i.toString
      case LongConstant(l)    => l.toString
      case DoubleConstant(d)  => d.toString
      case FloatConstant(f)   => f.toString
      case StringConstant(s)  => brStr(s)
      case BooleanConstant(b) => b.toString
      case CharConstant(ch)   => brStr(ch.toString)
      case NullConstant()     => "invalid"
      case UnitConstant()     => "invalid"
      case other              => brStr(other.value.toString)
    }

    /** Where a tree is in the Scala sources, as ` (path:line)` (the path from the source root where it is under it), for
      * the gaps the build lists. */
    def where(t: Tree): String =
      try {
        val path = Path.of(t.pos.sourceFile.path)
        val shown = sourceRoot.filter(r => path.startsWith(r)).map(r => r.relativize(path)).getOrElse(path)
        s" ($shown:${t.pos.startLine + 1})"
      }
      catch { case _: Throwable => "" }

    def todo(t: Tree): String = {
      val label = t.getClass.getSimpleName
      todos += s"unsupported: $label${where(t)}"
      s"RtTodo(${brStr(label)})"
    }

    // Class-emission context (mirrors JsEmitter): the class whose method we're
    // emitting; members resolve through the `self` param.
    var currentClass: Symbol = Symbol.noSymbol
    // The method whose body is being emitted: a `return` from it is a BrightScript `return`. (A return out of a
    // lambda to the method around it is deprecated in Scala 3 and not written here, so a `return` is the method's own.)
    var returnTarget: Symbol = Symbol.noSymbol
    def inCurrentClass(owner: Symbol): Boolean =
      currentClass.exists && owner.exists && currentClass.typeRef.baseClasses.contains(owner)

    // User-authored `object` → a set of top-level `Obj_member` functions.
    val moduleSyms = mutable.LinkedHashMap[Symbol, String]()
    // Names of emitted user CLASSES. A companion object flattens into the same
    // namespace as its class, so `Val.set` (instance) and `Val.set` (companion)
    // both want to be `Val_set` — the instance one then calls itself. Where a
    // class of the same name exists, the object's members take an `Obj` infix.
    // Only where there IS a collision, so output for everything else is
    // unchanged.
    val userClassNames = mutable.Set[String]()
    // Object-model state (see "object model" below). Declared up here, not
    // beside the defs that use them: Scala forbids a local def forward-
    // referencing a later def across an intervening local val.
    val userTypeDefs = mutable.LinkedHashMap[Symbol, ClassDef]()
    val objectModelTypes = mutable.Set[Symbol]()
    // `object`s that extend a user class or trait (including case objects): emitted as a singleton instance
    // of an object-model type, reached through `<Name>__module()`.
    val modelModules = mutable.Set[Symbol]()
    // While a parent constructor's arguments are emitted, the class's own constructor parameters are still
    // locals of `<Type>__init` — their fields are assigned after the parent init runs.
    var inParentArgs = false
    val userMethodSyms = mutable.Set[Symbol]()
    // Reachability (see "reachability" below): every ClassDef anywhere in the input, local and anonymous ones
    // included; the classes something constructs (or their ancestors); the members those classes reach. Until
    // the analysis has run, everything is kept.
    val allClassDefs = mutable.LinkedHashMap[Symbol, ClassDef]()
    val reachableClasses = mutable.Set[Symbol]()
    val reachableMembers = mutable.Set[Symbol]()
    var pruning = false
    def keepClass(s: Symbol): Boolean = !pruning || reachableClasses.contains(s)
    def keepMember(s: Symbol): Boolean = !pruning || reachableMembers.contains(s)
    /** A module's lazy val is emitted only when something names it (an icon table has thousands), or names what it
      * overrides (`font.family` on a trait an object implements); a class's always, since a trait slot may reach it. */
    def liveLazy(s: Symbol): Boolean =
      !s.owner.flags.is(Flags.Module) || keepMember(s) || s.allOverriddenSymbols.exists(keepMember)
    val aaIfaceMethods = Set("addreplace", "lookup", "lookupci", "doesexist", "delete", "clear",
      "keys", "items", "setmodecasesensitive", "append", "count", "reset", "next", "isnext", "isempty")
    val primitiveFqns = Set("scala.Int", "scala.Long", "scala.Double", "scala.Float", "scala.Short",
      "scala.Byte", "scala.Char", "scala.Boolean", "java.lang.String", "scala.Predef.String",
      "scala.Null", "scala.Nothing")
    // A-normalisation: statements hoisted out of value position wait in the
    // innermost statement context until that statement is written.
    val pendingStack = mutable.Stack[mutable.ListBuffer[String]]()
    var anfCounter = 0
    // Local `var`s captured by a closure / thunk / local class: boxed as
    // `{ v: … }` so the capture and the enclosing scope share one cell.
    val boxedVars = mutable.Set[Symbol]()
    // Local (anonymous) classes: generated name, enclosing class, captures.
    val typeNameOverride = mutable.Map[Symbol, String]()
    val anonOuter = mutable.Map[Symbol, Symbol]()
    val anonCaptures = mutable.Map[Symbol, List[Symbol]]()
    var anonCounter = 0
    // Runtime classes a user or anonymous class may extend, by the name of the
    // runtime's `<X>__install` / `<X>_init` pair.
    val shimParents = Map("java.lang.ThreadLocal" -> "ThreadLocal")
    /** A Scala name (backticks allow `Open Sans`, `600`) as a BrightScript identifier. */
    def brIdent(name: String): String = {
      val cleaned = name.map(c => if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') c else '_')
      if (cleaned.nonEmpty && cleaned.head >= '0' && cleaned.head <= '9') "_" + cleaned else cleaned
    }
    def modulePrefix(s: Symbol): String = {
      val raw = s.name.stripSuffix("$")
      val base = brIdent(raw)
      if (userClassNames.contains(raw)) base + "Obj" else base
    }
    def isEnumLike(s: Symbol): Boolean =
      s.exists && (s.flags.is(Flags.Enum)
        || (s.companionClass.exists && s.companionClass.flags.is(Flags.Enum))
        || (s.companionModule.exists && s.companionModule.flags.is(Flags.Enum)))
    def isUserModule(cd: ClassDef): Boolean = {
      val s = cd.symbol
      // A case class's companion is emitted too (its `given rw`, its own defs); its synthetic apply/unapply are not
      // user members, and its default getters are inlined at the call site.
      s.exists && s.flags.is(Flags.Module) && !s.flags.is(Flags.Case) && !hasNative(s)
        && !isEnumLike(s) && !s.name.stripSuffix("$").endsWith("$package") && !isBuiltin(s)
    }

    def isUserClassSym(s: Symbol): Boolean =
      s.exists && s.isClassDef && !s.flags.is(Flags.Module) && !s.flags.is(Flags.Case)
        && !s.flags.is(Flags.Trait) && !s.flags.is(Flags.Enum) && !hasNative(s) && !isBuiltin(s)
    def isUserClass(cd: ClassDef): Boolean = isUserClassSym(cd.symbol)

    def userMember(s: Symbol): Boolean =
      s.exists && !s.flags.is(Flags.Synthetic) && !s.flags.is(Flags.Artifact)

    def isFacade(s: Symbol): Boolean =
      s.exists && (hasNative(s) || hasNative(s.owner))
    def isFacadeCall(fn: Term): Boolean = isFacade(fn.symbol)

    def repeatedElemsOpt(t: Term): Option[List[Term]] = unwrap(t) match {
      case Repeated(es, _) => Some(es)
      case _               => None
    }
    def flattenVarargs(args: List[Term]): List[Term] =
      args.flatMap(a => repeatedElemsOpt(a).getOrElse(List(a)))

    def stripTypeApply(f: Term): Term = f match {
      case TypeApply(inner, _) => stripTypeApply(inner)
      case _                   => f
    }

    /** A sequence written out in full — `List(a, b)`, and the others that mean the same thing here, since every one
      * of them is a BrightScript array once emitted. */
    val seqNames = Set("List", "Seq", "Vector", "IndexedSeq", "Array")
    def isListApply(fn: Term): Boolean = fn match {
      case Select(q, "apply") =>
        seqNames.contains(q.symbol.name) || (q match { case Ident(n) => seqNames.contains(n); case _ => false })
      case _ => false
    }

    /** Whether the argument stands for a field `copy` was not given: a default getter, or — where the arguments had
      * to be put in order first — a local val that was handed one. Only `copy` may leave such a field out; an
      * ordinary call still passes every parameter it has. */
    def isCopyDefaultArg(t: Term): Boolean = isDefaultArg(t) || (unwrap(t) match {
      case id: Ident => fromDefaultGetter(id)
      case _         => false
    })

    /** Whether `id` stands for a local val that was given a default getter's answer. */
    def fromDefaultGetter(id: Ident): Boolean =
      try id.symbol.tree match {
        case vd: ValDef => vd.rhs.exists(r => r match {
          case Select(_, n) => n.contains("$default$")
          case _            => false
        })
        case _ => false
      }
      catch case _: Throwable => false

    def isDefaultArg(t: Term): Boolean = unwrap(t) match {
      case Select(_, n) if n.contains("$default$") => true
      case Ident(n) if n.contains("$default$")     => true
      case Apply(f, _)                             => isDefaultArg(f)
      case TypeApply(f, _)                         => isDefaultArg(f)
      case NamedArg(_, v)                          => isDefaultArg(v)
      case _                                       => false
    }

    // BrightScript identifiers can't contain `$`, and a set of words are
    // reserved (a Scala local named `next`, or scalac's `_$1` params, collide).
    val brReserved = Set(
      "next", "then", "to", "step", "for", "each", "in", "sub", "function", "end",
      "if", "else", "elseif", "while", "return", "dim", "stop", "goto", "and", "or",
      "not", "mod", "true", "false", "invalid", "m", "rem", "print", "let", "exit",
      "as", "void", "integer", "float", "double", "string", "object", "boolean",
      "dynamic", "type", "box", "run", "eval", "line_num", "pos", "tab"
    )
    // Operator characters → BrightScript-legal identifier fragments. BrightScript
    // identifiers are letters/digits/underscore only (no `$`), so a Scala
    // operator method like `:=` becomes `_colon_eq`, `+=` → `_plus_eq`,
    // `:::` → `_colon_colon_colon`. Applied at every member def and call site so
    // the mangled names line up.
    val brOpFrag: Map[Char, String] = Map(
      '~' -> "_tilde", '=' -> "_eq", '<' -> "_less", '>' -> "_greater",
      '!' -> "_bang", '#' -> "_hash", '%' -> "_percent", '^' -> "_up",
      '&' -> "_amp", '|' -> "_bar", '*' -> "_star", '/' -> "_slash",
      '+' -> "_plus", '-' -> "_minus", ':' -> "_colon", '\\' -> "_bslash",
      '?' -> "_qmark", '@' -> "_at"
    )
    def brMangleOps(name: String): String =
      if (name.exists(brOpFrag.contains))
        name.iterator.map(c => brOpFrag.getOrElse(c, c.toString)).mkString
      else name

    def brSafe(name: String): String = {
      val s = brIdent(brMangleOps(name.replace("$", "_")))
      if (brReserved(s.toLowerCase)) s + "_" else s
    }

    /** A local's or a parameter's name: as `brSafe`, and clear of the runtime's functions (see `runtimeFnNames`). A
      * member keeps its `brSafe` name, which the runtime and dispatch reach by string. */
    def brLocal(name: String): String = {
      val s = brSafe(name)
      if (runtimeFnNames(s.toLowerCase)) s + "_" else s
    }
    /** A term that lives in a method or a val's initialiser (a local, a parameter), not in a class. */
    def isLocalSym(s: Symbol): Boolean =
      s.exists && s.isTerm && s.owner.exists && !s.owner.isClassDef && !s.owner.isPackageDef
    /** A `def` inside a block. It is emitted as a closure held in a local of its name (see `emitStats`), and called
      * through it, so it reads what it captures as a lambda does. */
    def isLocalDef(s: Symbol): Boolean =
      s.exists && s.isDefDef && s.name != "$anonfun" && s.owner.exists && !s.owner.isClassDef
        && (s.owner.flags.is(Flags.Method) || s.owner.isValDef)

    /** BrightScript has ONE name per function, Scala allows overloads
     *  (`Transaction.commit()` and `Transaction.commit(v)`). Overloaded members
     *  get an arity suffix so each variant keeps its own function; a name that
     *  is not overloaded is untouched, so nothing else in the output moves.
     */
    def overloadSuffix(s: Symbol): String =
      if (!s.exists || !s.owner.exists) ""
      else {
        val sameName =
          try s.owner.declaredMethods.count(_.name == s.name)
          catch case _: Throwable => 1
        if (sameName <= 1) ""
        else "__" + s.paramSymss.flatten.count(_.isTerm).toString
      }

    /** The emitted name of a class/object member, disambiguated if overloaded. */
    def memberName(s: Symbol, name: String): String = brSafe(name) + overloadSuffix(s)


    def termParams(pcs: List[ParamClause]): List[String] =
      pcs.collect { case c: TermParamClause => c }.flatMap(_.params).map(p => brLocal(p.name))

    // A BrightScript function takes at most 32 parameters. Past `Packed` the rest travel as one array, `__more`, on both
    // sides alike (a definition and every call count the same arguments, defaults included), so a case class of sixty
    // fields is still one call. Counted without the `self` a method adds.
    val Packed = 30
    def packed(ps: List[String]): Boolean = ps.size > Packed + 1
    def packParams(ps: List[String]): List[String] = if (packed(ps)) ps.take(Packed) :+ "__more" else ps
    def unpackParams(ps: List[String]): String =
      if (packed(ps)) ps.drop(Packed).zipWithIndex.map((p, i) => s"  $p = __more[$i]\n").mkString else ""
    def packArgs(as: List[String]): List[String] =
      if (packed(as)) as.take(Packed) :+ as.drop(Packed).mkString("[", ", ", "]") else as
    def hasTermClause(pcs: List[ParamClause]): Boolean =
      pcs.exists { case _: TermParamClause => true; case _ => false }

    val binOps = Map(
      "==" -> "=", "!=" -> "<>", "+" -> "+", "-" -> "-", "*" -> "*",
      "/" -> "/", "%" -> "mod", "<" -> "<", ">" -> ">", "<=" -> "<=", ">=" -> ">=",
      "&&" -> "and", "||" -> "or"
    )

    def isCollType(t: Term): Boolean = {
      val s = t.tpe.dealias.typeSymbol
      // an Array is a BrightScript array as a List is, and reads the same way (`parts(1)`, `parts.length`)
      s.exists && (s.fullName.startsWith("scala.collection.") || s.fullName == "scala.Array")
    }

    // A function VALUE (`T => R`, i.e. scala.FunctionN). Such values are
    // defunctionalised {fn, env} closures and must be invoked via RtInvoke.
    def isFunctionType(tpe: TypeRepr): Boolean = {
      val s = tpe.widen.dealias.typeSymbol
      s.exists && s.fullName.matches("scala\\.Function\\d+")
    }

    // Option lowers to a plain nullable (Some(x)→x, None→invalid), so its ops
    // (map/flatMap/fold/getOrElse/…) become invalid-checks via runtime helpers.
    def isOptionType(t: Term): Boolean = {
      val s = t.tpe.widen.dealias.typeSymbol
      // and an Option's WithFilter, which a guard in a for-comprehension over Options makes (`if` then `map`): read as a
      // collection it was filtered and mapped as one, and came out an array that the next step took apart as a pair
      s.exists && (s.fullName == "scala.Option"
        || s.fullName.endsWith(".Some") || s.fullName.endsWith(".None") || s.fullName == "scala.Option.WithFilter")
    }
    def isTryType(t: Term): Boolean = {
      val s = t.tpe.widen.dealias.typeSymbol
      s.exists && (s.fullName == "scala.util.Try"
        || s.fullName.endsWith(".Success") || s.fullName.endsWith(".Failure"))
    }
    def isEitherType(t: Term): Boolean = {
      val s = t.tpe.widen.dealias.typeSymbol
      s.exists && (s.fullName == "scala.util.Either"
        || s.fullName.endsWith(".Right") || s.fullName.endsWith(".Left"))
    }

    // `opt.fold(ifEmpty)(f)` — two arg clauses (a leading type-arg on `fold`):
    // returns (receiver, ifEmpty, f) when `receiver` is an Option.
    def optionFold(x: Term): Option[(Term, Term, Term)] = {
      def peel(y: Term): Term = y match { case TypeApply(z, _) => peel(z); case _ => y }
      x match {
        case Apply(inner, List(f)) =>
          peel(inner) match {
            case Apply(sel, List(ifEmpty)) =>
              peel(sel) match {
                case Select(recv, "fold") if isOptionType(recv) => Some((recv, ifEmpty, f))
                case _ => None
              }
            case _ => None
          }
        case _ => None
      }
    }

    // Case-class construction → an assoc `{ _cls, field: … }` (the `_cls` lets
    // `match` / the runtime dispatch on type). Only for actual construction.
    def caseApply(resultTpe: TypeRepr): Option[List[String]] = {
      val ts = resultTpe.typeSymbol
      if (ts.exists && ts.flags.is(Flags.Case) && !isBuiltin(ts)) Some(ts.caseFields.map(_.name)) else None
    }
    def isCaseCtor(fn: Term, resultTpe: TypeRepr): Boolean = fn match {
      case Select(New(_), _) => true
      // only the companion's own generated apply: one the class declares beside it (Gradient's `apply(angle,
      // colors*)`) is a method with a body of its own, and emitted as the constructor it read its arguments as fields
      case Select(q, "apply") =>
        val comp = resultTpe.typeSymbol.companionModule
        comp.exists && q.symbol.exists && q.symbol == comp && fn.symbol.flags.is(Flags.Synthetic)
      case _ => false
    }

    // ── closures (defunctionalisation) ──────────────────────────────────────
    var closureCounter = 0
    var matchCounter = 0
    def asClosure(t: Term): Option[DefDef] = {
      def peel(x: Term): Term = x match {
        case Inlined(_, _, e) => peel(e)
        case Typed(e, _)      => peel(e)
        case Block(Nil, e)    => peel(e)
        case _                => x
      }
      peel(t) match {
        case Block(List(dd @ DefDef(_, _, _, _)), Closure(_, _)) => Some(dd)
        case _                                                   => None
      }
    }

    // Emit a lambda as a top-level `function closureN(env, args)` and return the
    // `{ fn: closureN, env: { self: self } }` value. `env.self` is the enclosing
    // instance (invalid at top level); params bind from the `args` array.
    // Free variables of a lambda: Idents referencing an enclosing def's
    // param/local (not `self`-members, not its own params/locals, not
    // modules/facades). BrightScript can't capture, so these ride in the env.
    def freeVarsIn(body: Tree, ownParams: Set[Symbol]): List[(String, Symbol)] = {
      val bound = mutable.Set[Symbol]() ++ ownParams
      // a local def inside the body is its own, wherever in its block it is named from
      val defined = new TreeTraverser {
        override def traverseTree(t: Tree)(owner: Symbol): Unit = {
          t match {
            case dd: DefDef => bound += dd.symbol
            case _ =>
          }
          super.traverseTree(t)(owner)
        }
      }
      defined.traverseTree(body)(Symbol.noSymbol)
      val found = mutable.LinkedHashMap[Symbol, String]()
      val trav = new TreeTraverser {
        override def traverseTree(t: Tree)(owner: Symbol): Unit = {
          t match {
            case v: ValDef => bound += v.symbol
            // a name a pattern binds (`case (name, e) =>`) is local to the case, not captured from outside
            case b: Bind => bound += b.symbol
            case id: Ident =>
              val s = id.symbol
              val parentParam = inParentArgs && s.exists && s.flags.is(Flags.ParamAccessor) && currentClass.exists && s.owner == currentClass
              if (parentParam && !bound.contains(s)) found.getOrElseUpdate(s, brLocal(id.name))
              else if (s.exists && s.isTerm && (!s.isDefDef || isLocalDef(s)) && !bound.contains(s)
                 && !isFacade(s) && !moduleSyms.contains(s.owner)
                 && s.owner.exists && !s.owner.isClassDef && (s.owner.flags.is(Flags.Method) || s.owner.isValDef)
                 && anonCaptureRef(s).isEmpty)
              found.getOrElseUpdate(s, brLocal(id.name))
            case sel @ Select(q: This, n) if inParentArgs && sel.symbol.exists && sel.symbol.flags.is(Flags.ParamAccessor)
                && currentClass.exists && sel.symbol.owner == currentClass && !bound.contains(sel.symbol) =>
              found.getOrElseUpdate(sel.symbol, brLocal(n))
            case _ =>
          }
          super.traverseTree(t)(owner)
        }
      }
      trav.traverseTree(body)(Symbol.noSymbol)
      found.toList.map((s, nm) => (nm, s))
    }

    def freeVars(dd: DefDef): List[(String, Symbol)] = {
      val own = dd.paramss.collect { case c: TermParamClause => c }.flatMap(_.params).map(_.symbol).toSet
      dd.rhs.map(r => freeVarsIn(r, own)).getOrElse(Nil)
    }

    /** `omit`: captures left out of the environment for now — a local def not yet made, the closure itself — which
      * `emitStats` puts in once they exist. */
    def emitClosure(dd: DefDef, omit: Set[Symbol] = Set.empty): String = {
      val n = closureCounter; closureCounter += 1
      val name = s"closure$n"
      val params = termParams(dd.paramss)
      val fvs = freeVars(dd)
      // Env keys are prefixed (`fv_`) so a captured local that arg-hoist
      // inlining later rewrites at the call site (value token) can't also
      // clobber the env KEY: `{ fv_d: (self.demos[i]) }` stays valid.
      val binds =
        (fvs.map { case (nm, _) => s"  $nm = env.fv_$nm" } ++
         params.zipWithIndex.map { case (p, i) => s"  $p = args[$i]" }).mkString("\n")
      // A value-returning lambda must RETURN its expression; a Unit lambda falls
      // through to `return invalid`.
      val wantRet = !(dd.returnTpt.tpe =:= TypeRepr.of[Unit])
      val body = dd.rhs.map(b => emitBody(b, wantRet)).getOrElse("")
      closures.append(s"function $name(env as object, args as object) as dynamic\n")
      closures.append("  self = env.self\n")
      if (binds.nonEmpty) closures.append(binds).append("\n")
      if (body.nonEmpty) closures.append(body).append("\n")
      if (!wantRet) closures.append("  return invalid\n")
      closures.append("end function\n\n")
      // `self` only where the body uses it: a closure that carried it regardless held the object it was written in for as
      // long as the closure lived, so a binding to a theme's colour kept a whole screensaver scene, pictures and all
      val envSelf = if (currentClass.exists && RokuEmitter.usesSelf(body)) "self" else "invalid"
      // A captured box / thunk travels as itself (raw name), never unboxed.
      val envFvs = fvs.filterNot((_, s) => omit.contains(s)).map { case (nm, _) => s", fv_$nm: $nm" }.mkString
      s"{ fn: $name, env: { self: $envSelf$envFvs } }"
    }

    /** A by-name argument: a zero-arg closure over the expression, forced by
     *  the callee at each use. Passing a by-name parameter straight through
     *  hands over the existing thunk instead of wrapping it again. */
    def emitThunk(body0: Term): String = {
      val body = unwrap(body0)
      body match {
        // a by-name parameter handed on as it is passes its own thunk; a class's by-name parameter is a field, so
        // inside one of its methods the thunk is the field's (a bare name there is an undeclared local)
        case id: Ident if isByNameParam(id.symbol) && anonCaptureRef(id.symbol).isEmpty =>
          val field = !inParentArgs && id.symbol.flags.is(Flags.ParamAccessor) && currentClass.exists &&
            inCurrentClass(id.symbol.owner)
          if (field) s"self.${fieldKey(id.symbol)}" else brLocal(id.name)
        case _ =>
          val n = closureCounter; closureCounter += 1
          val name = s"closure$n"
          val fvs = freeVarsIn(body, Set.empty)
          val binds = fvs.map { case (nm, _) => s"  $nm = env.fv_$nm" }.mkString("\n")
          val unitBody = body.tpe.widen =:= TypeRepr.of[Unit]
          val code = emitInto(body, if (unitBody) "" else skReturn)
          closures.append(s"function $name(env as object, args as object) as dynamic\n")
          closures.append("  self = env.self\n")
          if (binds.nonEmpty) closures.append(binds).append("\n")
          if (code.trim.nonEmpty) closures.append(code).append("\n")
          closures.append("  return invalid\n")
          closures.append("end function\n\n")
          val envSelf = if (currentClass.exists && RokuEmitter.usesSelf(code)) "self" else "invalid"
          val envFvs = fvs.map { case (nm, _) => s", fv_$nm: $nm" }.mkString
          s"{ fn: $name, env: { self: $envSelf$envFvs } }"
      }
    }

    def emitArgVal(t: Term): String = asClosure(t) match {
      case Some(dd) => emitClosure(dd)
      case None     => emitTerm(t)
    }

    // Positional args for user calls / ctors. Omitted defaults are REAL
    // arguments to an emitted function (BrightScript checks arity), so they
    // are emitted — not dropped as they are for facade constructor options.
    def emitArgList(args: List[Term], callee: Symbol = Symbol.noSymbol): List[String] =
      packArgs(emitOrdered(argParts(args, callee)).filter(_ != dropArg))
    def emitArgs(args: List[Term], callee: Symbol = Symbol.noSymbol): String =
      emitArgList(args, callee).mkString(", ")

    // A @native class construction → one options assoc keyed by parameter
    // name, which the platform runtime's constructor reads by field.
    def emitFacadeCtorArgs(ctor: Symbol, args: List[Term]): String = {
      val params = if (ctor.exists) ctor.paramSymss.flatten.filter(_.isTerm) else Nil
      val parts = mutable.ListBuffer[String]()
      var i = 0
      args.foreach { a =>
        if (!isDefaultArg(a))
          a match {
            case NamedArg(n, v) => parts += s"$n: ${emitArgVal(v)}"
            case other =>
              params.lift(i) match {
                case Some(p) => parts += s"${p.name}: ${emitArgVal(other)}"
                case None    => parts += emitArgVal(other)
              }
          }
        i += 1
      }
      "{ " + parts.mkString(", ") + " }"
    }

    def emitFacadeArgs(args: List[Term]): String = {
      val kept = args.filterNot(isDefaultArg)
      val pos   = mutable.ListBuffer[String]()
      val named = mutable.ListBuffer[String]()
      kept.foreach {
        case NamedArg(n, v) => named += s"$n: ${emitArgVal(v)}"
        case other          => pos += emitArgVal(other)
      }
      if (named.isEmpty) pos.mkString(", ")
      else if (pos.isEmpty) "{ " + named.mkString(", ") + " }"
      else pos.mkString(", ") + ", { " + named.mkString(", ") + " }"
    }

    // ── object model ─────────────────────────────────────────────────────────
    // A user class flattens to an assoc array, which has no vtable of its own.
    // Types that take part in inheritance — traits, classes with user bases,
    // classes something else extends, case classes with methods — get a real
    // object model:
    //   * `C_new(args)` allocates, installs every vtable SLOT for C's whole
    //     linearization (most-derived implementation wins), then runs
    //     `C_init(self, args)`;
    //   * `C_init` chains like a JVM constructor: superclass init, then each
    //     mixed-in trait init (guarded so a diamond initialises once), then C's
    //     own fields and constructor statements in body order;
    //   * a call to one of their members dispatches through the slot —
    //     `recv.m(recv, …)`, the convention the runtime already uses for
    //     `recv.m(recv, …)` — so an override in a subclass or mixin actually runs.
    // Everything outside that set emits exactly as before.
    def isUserTypeSym(s: Symbol): Boolean =
      s.exists && s.isClassDef && !s.flags.is(Flags.Module) && !hasNative(s) && !isBuiltin(s) && !isEnumLike(s)
    def isModel(s: Symbol): Boolean = s.exists && objectModelTypes.contains(s)
    def typeName(s: Symbol): String = typeNameOverride.getOrElse(s, brIdent(s.name))

    // BrightScript roAssociativeArray INTERFACE methods. `aa.clear()` calls the
    // built-in ifAssociativeArray.Clear, never a member named `clear`, so a
    // vtable slot must not use one of these names.
    def arityOf(s: Symbol): Int = s.paramSymss.flatten.count(_.isTerm)
    def overriddenUser(s: Symbol): List[Symbol] =
      try s.allOverriddenSymbols.toList.filter(o => o.exists && userTypeDefs.contains(o.owner))
      catch case _: Throwable => Nil
    // The slot a member lives in is named after its TOPMOST user declaration,
    // so every override in a hierarchy lands in the same slot.
    def topDecl(s: Symbol): Symbol = {
      val chain = overriddenUser(s)
      if (chain.isEmpty) s else chain.last
    }
    def slotName(s: Symbol): String = {
      val top = topDecl(s)
      val base = brSafe(top.name)
      val owner = top.owner
      val arities =
        if (owner.exists && userTypeDefs.contains(owner))
          owner.typeRef.baseClasses.filter(userTypeDefs.contains)
            .flatMap(b => try b.declaredMethods.filter(_.name == top.name) catch case _: Throwable => Nil)
            .map(arityOf).distinct
        else Nil
      val named0 = if (arities.size > 1) base + "__" + arityOf(top) else base
      // A method named like a FIELD of the same object (case class `AnimateIn(easing: Easing)` with
      // `def easing(e)`) would share the field's key and be overwritten by its value.
      val fieldClash = top.isDefDef && owner.exists && userTypeDefs.contains(owner) &&
        owner.typeRef.baseClasses.filter(userTypeDefs.contains).exists(b =>
          try b.declaredFields.exists(f => f.name == top.name) catch case _: Throwable => false)
      val named = if (fieldClash) named0 + "__m" else named0
      if (aaIfaceMethods.contains(named.toLowerCase)) named + "_" else named
    }

    // A `val` (or case-class param) that overrides a `def` — `override val
    // priority` in FunctionReaction over `def priority` in Reaction. Its slot
    // must hold a getter, so the value itself is stored under `__v_<name>`.
    def isVirtualField(s: Symbol): Boolean =
      s.exists && !s.isDefDef && s.owner.exists && isModel(s.owner) && !s.flags.is(Flags.Lazy)
        && overriddenUser(s).exists(_.isDefDef)
    /** The user base class that declares a member named like the constructor parameter `p` (other than a parameter it
      * passes on, which holds the same value, or a member it overrides), if any. */
    def shadowedBy(p: Symbol): Option[Symbol] =
      try p.owner.typeRef.baseClasses.drop(1).find { b =>
        userTypeDefs.contains(b) && b.declarations.exists(d => d.name == p.name && !d.flags.is(Flags.ParamAccessor))
      }.filter(_ => p.allOverriddenSymbols.isEmpty && !p.flags.is(Flags.Override))
      catch case _: Throwable => None
    def fieldKey(s: Symbol): String =
      if (isVirtualField(s)) "__v_" + brSafe(s.name) else brSafe(s.name)
    def isModelMember(s: Symbol): Boolean =
      s.exists && s.owner.exists && isModel(s.owner) && !s.isClassConstructor
        && ((s.isDefDef && !s.flags.is(Flags.FieldAccessor)) || s.flags.is(Flags.Lazy) || isVirtualField(s))
    def isStaticModelMember(s: Symbol): Boolean =
      s.flags.is(Flags.Private) || s.flags.is(Flags.Final)
    def modelImplName(s: Symbol): String =
      if (s.flags.is(Flags.Lazy)) s"${typeName(s.owner)}_${brSafe(s.name)}__lazy"
      else if (isVirtualField(s)) s"${typeName(s.owner)}_${brSafe(s.name)}__get"
      else s"${typeName(s.owner)}_${memberName(s, s.name)}"

    def emittableMethod(dd: DefDef): Boolean =
      dd.rhs.isDefined && dd.name != "<init>" && !dd.symbol.flags.is(Flags.FieldAccessor)
        && (userMember(dd.symbol) || dd.name.contains("$default$")) && keepMember(dd.symbol)

    // A receiver that is safe to evaluate twice (`recv.m(recv, …)`): a local,
    // `this`, a literal, or a plain field chain. Anything else — a call, a
    // cached module val, a lazy getter — goes through `RtVcallN` instead.
    def isSimpleRecv(t: Term): Boolean = unwrap(t) match {
      case This(_) | Literal(_) => true
      case id: Ident =>
        val sy = id.symbol
        sy.exists && !sy.isDefDef && !isModelMember(sy) && !(sy.owner.exists && sy.owner.flags.is(Flags.Module))
      case sel @ Select(q, _) =>
        val sy = sel.symbol
        sy.exists && !sy.isDefDef && !isModelMember(sy) && !(sy.owner.exists && sy.owner.flags.is(Flags.Module))
          && isSimpleRecv(q)
      case _ => false
    }

    def emitModelCall(recv: Option[Term], sy: Symbol, args: List[Term]): String = {
      val recvPart: () => String = () => recv match {
        case None                 => "self"
        case Some(r) if isThis(r) => "self"
        case Some(r)              => emitTerm(r)
      }
      val all = emitOrdered(recvPart :: argParts(args, sy)).filter(_ != dropArg)
      val recvStr = all.head
      val argList = packArgs(all.tail)
      val tail = argList.map(", " + _).mkString
      if (isStaticModelMember(sy)) s"${modelImplName(sy)}($recvStr$tail)"
      else {
        val simple = recv match {
          case None    => true
          case Some(r) => isThis(r) || isSimpleRecv(r) || isTrivialExpr(recvStr)
        }
        if (simple || argList.size > 8) s"$recvStr.${slotName(sy)}($recvStr$tail)"
        else s"RtVcall${argList.size}($recvStr, ${brStr(slotName(sy))}$tail)"
      }
    }

    // ── arguments ────────────────────────────────────────────────────────────
    def isTermClause(c: List[Symbol]): Boolean = c.headOption.forall(_.isTerm)
    def isByNameParam(p: Symbol): Boolean =
      if (!p.exists || p.isDefDef) false
      else
        try p.tree match {
          case vd: ValDef => vd.tpt.tpe match { case ByNameType(_) => true; case _ => false }
          case _          => false
        }
        catch case _: Throwable => false
    def userEmittedCallee(sy: Symbol): Boolean =
      sy.exists && sy.owner.exists && (userTypeDefs.contains(sy.owner) || moduleSyms.contains(sy.owner))
    def isUserDefaultOwner(o: Symbol): Boolean =
      o.exists && (userTypeDefs.contains(o) || moduleSyms.contains(o)
        || (o.flags.is(Flags.Module) && o.companionClass.exists && userTypeDefs.contains(o.companionClass)))
    def dropArg: String = "__RT_DROP__"
    def isTrivialExpr(e: String): Boolean =
      e.matches("[A-Za-z_][A-Za-z0-9_]*") || e.matches("-?[0-9][0-9.eE+#-]*") || e.matches("\"(?:[^\"]|\"\")*\"")

    /** Evaluate a call's parts left to right. If a later part hoists
     *  statements (A-normalisation), every EARLIER non-trivial part is pinned
     *  into a temp ahead of those statements, so Scala's evaluation order holds. */
    def emitOrdered(parts: List[() => String]): List[String] = {
      val outs = mutable.ArrayBuffer[String]()
      parts.foreach { part =>
        val before = if (pendingStack.nonEmpty) pendingStack.top.size else 0
        val v = part()
        if (pendingStack.nonEmpty && pendingStack.top.size > before) {
          var at = before
          for (j <- outs.indices)
            if (outs(j) != dropArg && !isTrivialExpr(outs(j))) {
              anfCounter += 1
              val tmp = s"_a$anfCounter"
              pendingStack.top.insert(at, s"  $tmp = ${outs(j)}")
              at += 1
              outs(j) = tmp
            }
        }
        outs += v
      }
      outs.toList
    }

    /** One deferred emitter per argument. A by-name parameter receives a
     *  zero-arg closure (a thunk); an omitted default is a real argument for a
     *  function the emitter writes, and dropped for anything else (a facade,
     *  a runtime function), whose runtime supplies it. */
    def isRepeatedParam(p: Symbol): Boolean =
      try p.tree match {
        case vd: ValDef =>
          val tp = vd.tpt.tpe
          tp.typeSymbol.name.contains("repeated") || tp.show.endsWith("*")
            || (tp match { case AppliedType(t, _) => t.typeSymbol.name.contains("repeated"); case _ => false })
        case _ => false
      }
      catch case _: Throwable => false
    def isSplice(t: Term): Boolean = t match {
      case Typed(_, tpt) => tpt.tpe.typeSymbol.name == "<repeated>" && repeatedElemsOpt(t).isEmpty
      case _             => false
    }
    /** A varargs argument as one array: its elements, or the spliced sequence itself (`xs*`). */
    def varargsArray(args: List[Term]): String =
      if (args.size == 1 && isSplice(args.head)) emitTerm(unwrap(args.head))
      else "[" + flattenVarargs(args).map(emitArgVal).mkString(", ") + "]"

    def argParts(args: List[Term], callee: Symbol): List[() => String] = {
      val params =
        if ((userEmittedCallee(callee) || isLocalDef(callee)) && callee.isDefDef) callee.paramSymss.filter(isTermClause).flatten
        else Nil
      val repeatedAt = {
        val byParam = params.indexWhere(isRepeatedParam)
        if (byParam >= 0) byParam
        else if (params.nonEmpty) args.indexWhere(a => repeatedElemsOpt(a).isDefined || isSplice(a))
        else -1
      }
      if (repeatedAt >= 0) {
        val fixed = argParts(args.take(repeatedAt), Symbol.noSymbol).zipWithIndex.map { case (part, i) =>
          val arg = args(i)
          val inner = arg match { case NamedArg(_, v) => v; case other => other }
          if (params.lift(i).exists(isByNameParam) && !isDefaultArg(arg)) (() => emitThunk(inner)) else part
        }
        return fixed :+ (() => varargsArray(args.drop(repeatedAt)))
      }
      val flat = flattenVarargs(args)
      flat.zipWithIndex.map { case (arg, i) => () =>
        val inner = arg match { case NamedArg(_, v) => v; case other => other }
        val byName = params.lift(i).exists(isByNameParam)
        if (isDefaultArg(arg)) {
          val gs = unwrap(inner).symbol
          val owner = if (gs.exists && gs.owner.exists) gs.owner else Symbol.noSymbol
          // a local def's default is a local def beside it, called as one
          if (!isUserDefaultOwner(owner) && !isLocalDef(gs)) dropArg
          else {
            val term = if (owner.flags.is(Flags.Module)) defaultGetterRhs(gs).getOrElse(inner) else inner
            if (byName) emitThunk(term) else emitArgVal(term)
          }
        }
        else if (byName && isStableValue(inner) && !byNameIsFunction(params.lift(i))) emitArgVal(inner)
        else if (byName) emitThunk(inner)
        else emitArgVal(inner)
      }
    }

    def isStableValue(t: Term): Boolean = unwrap(t) match {
      case Literal(_) => true
      case _: This    => true
      case id: Ident =>
        val sy = id.symbol
        sy.exists && sy.isValDef && !sy.flags.is(Flags.Mutable) && !sy.flags.is(Flags.Lazy)
          && !boxedVars.contains(sy) && !isByNameParam(sy) && !isModelMember(sy)
          && !(sy.owner.exists && sy.owner.flags.is(Flags.Module))
      case _ => false
    }
    def byNameIsFunction(p: Option[Symbol]): Boolean = p.exists { ps =>
      try ps.tree match {
        case vd: ValDef => vd.tpt.tpe match {
          case ByNameType(u) => isFunctionType(u)
          case _             => false
        }
        case _ => true
      }
      catch case _: Throwable => true
    }

    def clauseParamsOf(ap: Apply): List[Symbol] = {
      def depth(f: Term): Int = stripTypeApply(f) match {
        case Apply(g, _) => 1 + depth(g)
        case _           => 0
      }
      val sy = innermostFnSym(ap.fun)
      if (!sy.exists || !sy.isDefDef) Nil
      else sy.paramSymss.filter(isTermClause).lift(depth(ap.fun)).getOrElse(Nil)
    }

    // ── `this`, outer instances and captures of local classes ────────────────
    def thisRef(target: Symbol): String =
      if (!currentClass.exists || !target.exists) "self"
      else {
        var c = currentClass
        var ref = "self"
        var guard = 0
        while (c.exists && !(c == target || c.typeRef.baseClasses.contains(target)) && anonOuter.contains(c) && guard < 16) {
          ref = ref + ".__outer"
          c = anonOuter(c)
          guard += 1
        }
        ref
      }
    def anonCaptureRef(sy: Symbol): Option[String] = {
      var c = currentClass
      var ref = "self"
      var guard = 0
      var found: Option[String] = None
      while (found.isEmpty && c.exists && guard < 16) {
        if (anonCaptures.get(c).exists(_.contains(sy))) found = Some(s"$ref.__fv_${brSafe(sy.name)}")
        else anonOuter.get(c) match {
          case Some(o) =>
            ref = ref + ".__outer"
            c = o
          case None => c = Symbol.noSymbol
        }
        guard += 1
      }
      found
    }
    def captureValue(sy: Symbol): String = anonCaptureRef(sy).getOrElse(brLocal(sy.name))
    def isOwnedWithin(sy: Symbol, cls: Symbol): Boolean =
      try {
        var o = sy.owner
        var g = 0
        var hit = false
        while (!hit && o.exists && !o.isPackageDef && g < 64) {
          if (o == cls) hit = true
          o = o.owner
          g += 1
        }
        hit
      }
      catch case _: Throwable => false

    // ── shapes ───────────────────────────────────────────────────────────────
    /** A call into something the emitter does not write (a facade, a runtime
     *  function): its hoisted default-arg temps are stripped. */
    def dropsDefaults(e: Term): Boolean = {
      def innermost(t: Term): Term = unwrap(t) match {
        case Apply(f, _)     => innermost(f)
        case TypeApply(f, _) => innermost(f)
        case other           => other
      }
      unwrap(e) match {
        case ap: Apply =>
          innermost(ap) match {
            case Select(New(tpt), _) =>
              val ts = tpt.tpe.typeSymbol
              !(userTypeDefs.contains(ts) || isUserClassSym(ts))
            case f => !userEmittedCallee(f.symbol)
          }
        case _ => false
      }
    }
    def isLocalClassInstantiation(cd: ClassDef, e: Term): Boolean = {
      def peel(t: Term): Term = t match {
        case Apply(f, _)     => peel(f)
        case TypeApply(f, _) => peel(f)
        case other           => other
      }
      unwrap(e) match {
        case ap: Apply =>
          peel(ap) match {
            case Select(New(tpt), _) => tpt.tpe.typeSymbol == cd.symbol
            case _                   => false
          }
        case _ => false
      }
    }
    /** Side-effect free and cheap — safe to evaluate unconditionally, as
     *  `_iif(c, a, b)` does for both arms. A CALL never is: reactify's
     *  `if useA() then a() else b()` must not register `b` as a dependency. */
    def pureValue(t: Term): Boolean = unwrap(t) match {
      case Literal(_) => true
      case _: This    => true
      case id @ Ident(n) =>
        val sy = id.symbol
        n == "Nil" || n == "None" ||
          (sy.exists && !sy.isDefDef && !isModelMember(sy) && !isByNameParam(sy)
            && !(sy.owner.exists && sy.owner.flags.is(Flags.Module)))
      case sel @ Select(q, _) =>
        val sy = sel.symbol
        sy.exists && !sy.isDefDef && !isModelMember(sy) && !(sy.owner.exists && sy.owner.flags.is(Flags.Module)) && pureValue(q)
      case Apply(Select(l, op), List(r)) if binOps.contains(op) && isPrimLike(l) && isPrimLike(r) =>
        pureValue(l) && pureValue(r)
      case Select(q, "unary_!" | "unary_$bang" | "unary_-" | "unary_$minus") => pureValue(q)
      case _ => false
    }

    // ── stdlib recognisers ───────────────────────────────────────────────────
    def ownerFqn(sy: Symbol): String =
      if (sy.exists && sy.owner.exists) sy.owner.fullName.stripSuffix("$") else ""
    def ownerIs(fn: Term, fqn: String): Boolean = ownerFqn(fn.symbol) == fqn
    def fnIs(fn: Term, owner: String, name: String): Boolean =
      fn.symbol.exists && fn.symbol.name == name && ownerIs(fn, owner)
    def fqnOf(tp: TypeRepr): String = {
      val sy = tp.widen.dealias.typeSymbol
      if (sy.exists) sy.fullName else ""
    }
    def isSetTpe(tp: TypeRepr): Boolean = {
      val f = fqnOf(tp)
      f.startsWith("scala.collection.immutable.Set") || f == "scala.collection.Set"
        || f.startsWith("scala.collection.immutable.HashSet") || f.startsWith("scala.collection.immutable.ListSet")
    }
    def isMapTpe(tp: TypeRepr): Boolean = {
      val f = fqnOf(tp)
      f.startsWith("scala.collection.immutable.Map") || f == "scala.collection.Map"
        || f.startsWith("scala.collection.immutable.HashMap") || f.startsWith("scala.collection.immutable.ListMap")
    }
    /** What a collection operator takes in, as the runtime's array: a Map's pairs, an Option's value or nothing, and
      * any other collection as it is (walked as an array, a Map gave its keys alone and an Option its value's fields). */
    def emitElems(t: Term): String =
      if (isMapTpe(t.tpe)) s"_mapToList(${emitTerm(t)})"
      else if (isOptionType(t)) s"_optToList(${emitTerm(t)})"
      else emitTerm(t)
    /** A List, a Vector, a Seq or an Array seen through its ArrayOps (and a Set): an array here, which the sequence
      * helpers take. */
    def isSeqLike(recv: Term): Boolean = isCollType(recv) && !isMapTpe(recv.tpe) && !isStringLike(recv.tpe)
    def isSeqTpe(tp: TypeRepr): Boolean =
      isListTpe(tp) || Set("scala.collection.immutable.IndexedSeq", "scala.collection.IndexedSeq", "scala.Array").contains(fqnOf(tp))
    def isModuleRef(q: Term): Boolean =
      q.symbol.exists && q.symbol.flags.is(Flags.Module) || q.tpe.widen.typeSymbol.flags.is(Flags.Module)
    /** An argument, or 0 where it is the parameter's default (`indexWhere(p, from = 0)`). */
    def orZero(a: Term): String = {
      val sy = unwrap(a).symbol
      if (sy.exists && sy.name.contains("$default$")) "0" else emitArgVal(a)
    }
    /** `a to b` or `a until b`, in either shape (a RichInt's, or 3.10's extension in Int): its bounds, and whether it
      * takes in the last. */
    def rangeBounds(t: Term): Option[(Term, Term, Boolean)] = unwrap(t) match {
      case Apply(Select(Apply(wrap, List(a)), op @ ("to" | "until")), List(b)) if isRichNumWrapper(wrap) => Some((a, b, op == "to"))
      case Apply(Apply(f, List(a)), List(b)) if isPrimitiveExtension(stripTypeApply(f)) && Set("to", "until").contains(stripTypeApply(f).symbol.name) =>
        Some((a, b, stripTypeApply(f).symbol.name == "to"))
      case _ => None
    }
    def isListTpe(tp: TypeRepr): Boolean = {
      val f = fqnOf(tp)
      f == "scala.collection.immutable.List" || f == "scala.collection.immutable.Seq"
        || f == "scala.collection.immutable.Vector" || f == "scala.collection.Seq"
    }
    def elemIsUserType(recv: Term): Boolean =
      recv.tpe.widen.dealias.typeArgs.headOption.exists { ta =>
        val sy = ta.dealias.typeSymbol
        userTypeDefs.contains(sy) || isUserClassSym(sy)
      }
    // Scala 3.10 replaced the `Rich*` wrappers by extension methods in the primitive companions
    // (`Int.until(0)(n)`, `Double.compare(a)(b)`, `Double.abs(a)`), and `k -> v` by a call of a top-level `->`.
    // Both shapes are recognised, so the emitter reads 3.9's and 3.10's TASTy alike.
    def primitiveCompanions: Set[String] = Set("scala.Int", "scala.Long", "scala.Double", "scala.Float", "scala.Short", "scala.Byte", "scala.Char")
    def primitiveExtensionNames: Set[String] = Set("compare", "until", "to", "max", "min", "abs", "floor", "ceil", "round",
      "isDigit", "isLetter", "isLetterOrDigit", "isWhitespace", "isSpaceChar", "isUpper", "isLower")
    def isPrimitiveExtension(fn: Term): Boolean =
      fn.symbol.exists && fn.symbol.isDefDef && primitiveCompanions.contains(ownerFqn(fn.symbol))
        && primitiveExtensionNames.contains(fn.symbol.name)
    /** A primitive companion's extension method, uncurried to `receiver :: arguments`, onto the same runtime the
      * wrapper shapes reach. */
    def emitPrimitiveExtension(fn: Term, name: String, args: List[Term]): String = name match {
      case "compare" if args.size == 2 => s"_cmpNum(${emitTerm(args(0))}, ${emitArgVal(args(1))})"
      case "until" if args.size == 2   => s"_until(${emitTerm(args(0))}, ${emitTerm(args(1))})"
      case "to" if args.size == 2      => s"_toIncl(${emitTerm(args(0))}, ${emitTerm(args(1))})"
      case "max" | "min" if args.size == 2 => emitMath(name, args)
      case "abs" | "floor" | "ceil" | "round" if args.size == 1 => emitMath(name, args)
      // a Char's tests (a Char is a one-character string here)
      case "isDigit" | "isLetter" | "isLetterOrDigit" | "isWhitespace" | "isSpaceChar" | "isUpper" | "isLower" if args.size == 1 =>
        s"_ch_$name(${emitTerm(args.head)})"
      case _ => todo(fn)
    }
    def isRichNumWrapper(wrap: Term): Boolean = {
      val sy = stripTypeApply(wrap).symbol
      sy.exists && Set("doubleWrapper", "intWrapper", "longWrapper", "floatWrapper", "shortWrapper",
        "byteWrapper", "charWrapper").contains(sy.name)
    }
    /** `Iterator.iterate(z)(f)`: its start and its step. */
    def iterateCall(t: Term): Option[(Term, Term)] = unwrap(t) match {
      case Apply(Apply(fn, List(z)), List(f)) if fnIs(stripTypeApply(fn), "scala.collection.Iterator", "iterate") => Some((z, f))
      case _ => None
    }
    def isArrowAssoc(arrow: Term): Boolean = {
      val sy = stripTypeApply(arrow).symbol
      sy.exists && sy.name == "ArrowAssoc"
    }

    // ── library shims (the network module): rapid's Task, fabric's Json and RW, scribe, java.util ──
    // Spice's durable client compiles through with these libraries left OUT of the TASTy; a call into them
    // emits a runtime-neutral helper name (`RapidTask_map`, `_jsonGet`, `ConcurrentHashMap_put`, …) that both
    // runtimes define. Implicit clauses (sourcecode positions, MDC) are dropped at the call.
    def baseNames(s: Symbol): List[String] =
      try s.typeRef.baseClasses.map(_.fullName) catch case _: Throwable => Nil
    def instanceOf(fn: Term, fqn: String): Boolean = {
      val o = fn.symbol.owner
      fn.symbol.exists && o.exists && !o.flags.is(Flags.Module) && (o.fullName == fqn || baseNames(o).contains(fqn))
    }
    def isRapidTask(fn: Term): Boolean = instanceOf(fn, "rapid.Task")
    def isRapidTaskCompanion(fn: Term): Boolean =
      fn.symbol.exists && fn.symbol.owner.exists && fn.symbol.owner.flags.is(Flags.Module) && ownerFqn(fn.symbol) == "rapid.Task"
    def isFabricJson(fn: Term): Boolean = instanceOf(fn, "fabric.Json")
    def isFabricBuilder(fn: Term): Boolean =
      fn.symbol.exists && ownerFqn(fn.symbol).startsWith("fabric.") && !ownerFqn(fn.symbol).startsWith("fabric.rw")
        && Set("obj", "arr", "str", "num", "bool").contains(fn.symbol.name)
    def isFabricNull(s: Symbol): Boolean =
      s.exists && s.name == "Null" && (try s.owner.fullName == "fabric" catch case _: Throwable => false)
    def isFabricRW(s: Symbol): Boolean =
      s.exists && s.isTerm && s.name.endsWith("RW")
        && (try s.owner.fullName.startsWith("fabric.rw") catch case _: Throwable => false)
    def isScribeLog(fn: Term): Boolean =
      fn.symbol.exists && Set("trace", "debug", "info", "warn", "error").contains(fn.symbol.name)
        && { val o = ownerFqn(fn.symbol); o == "scribe" || o.startsWith("scribe.") }
    def isDurationTerm(t: Term): Boolean = fqnOf(t.tpe).startsWith("scala.concurrent.duration")
    /** The arguments of the explicit clauses only — a trailing implicit clause is dropped. */
    def explicitArgs(sy: Symbol, args: List[Term]): List[Term] = {
      val clauses = try sy.paramSymss.filter(c => c.nonEmpty && c.head.isTerm) catch case _: Throwable => Nil
      val n = clauses.takeWhile(c => !(c.head.flags.is(Flags.Given) || c.head.flags.is(Flags.Implicit))).map(_.size).sum
      if (clauses.isEmpty) args else args.take(n)
    }
    def shimCall(name: String, recv: Option[Term], args: List[Term]): String =
      s"$name(${(recv.map(emitTerm).toList ++ args.map(emitArgVal)).mkString(", ")})"
    /** `new Wrapper(v)` (an implicit class application) → `v`. */
    def unwrapCtor(t: Term): Term = unwrap(t) match {
      case Apply(TypeApply(Select(New(_), _), _), List(v)) => v
      case Apply(Select(New(_), _), List(v))               => v
      case Apply(TypeApply(_, _), List(v))                 => v
      case Apply(_, List(v))                               => v
      case other                                           => other
    }
    /** `q.getClass` → `q`. */
    def getClassRecv(t: Term): Option[Term] = unwrap(t) match {
      case Apply(f, Nil) => stripTypeApply(f) match { case Select(q, "getClass") => Some(q); case _ => None }
      case Select(q, "getClass") => Some(q)
      case _                     => None
    }
    /** A Scala 3 enum's simple case: the companion's stable value with the Enum and Case flags. */
    def isEnumCase(s: Symbol): Boolean =
      s.exists && s.isTerm && s.flags.is(Flags.Enum) && s.flags.is(Flags.Case)
        && (try s.owner.exists && s.owner.companionClass.exists && s.owner.companionClass.flags.is(Flags.Enum) catch case _: Throwable => false)
    def emitEnumCase(s: Symbol, name: String): String = {
      val enumClass = s.owner.companionClass
      val ordinal = try enumClass.children.indexOf(s) catch case _: Throwable => -1
      s"RtEnum(${brStr(enumClass.name)}, ${brStr(name)}, $ordinal)"
    }
    def isTaskModule(q: Term): Boolean =
      q.symbol.exists && q.symbol.flags.is(Flags.Module) && q.symbol.fullName.stripSuffix("$") == "rapid.Task"
    def isTaskTerm(t: Term): Boolean =
      try t.tpe.baseClasses.exists(_.fullName == "rapid.Task") catch case _: Throwable => false
    def isFabricWrapper(fn: Term, name: String): Boolean =
      fn.symbol.exists && fn.symbol.owner.exists && fn.symbol.owner.name == name && ownerFqn(fn.symbol).startsWith("fabric.rw")
    /** A constructor call with every argument clause (`new C[A, B]()(using rwA, rwB)`): the class, the
      * constructor, and the arguments of all clauses in order. */
    def newChain(t: Term): Option[(Symbol, Symbol, List[Term])] = t match {
      case Apply(ctor @ Select(New(tpt), _), args) if isUserClassSym(tpt.tpe.typeSymbol) =>
        Some((tpt.tpe.typeSymbol, ctor.symbol, args))
      case Apply(TypeApply(ctor @ Select(New(tpt), _), _), args) if isUserClassSym(tpt.tpe.typeSymbol) =>
        Some((tpt.tpe.typeSymbol, ctor.symbol, args))
      case Apply(inner @ Apply(_, _), more) => newChain(inner).map((c, k, a) => (c, k, a ++ more))
      case _ => None
    }
    // ── fabric RW derivation (`given rw: RW[T] = RW.gen`) ──
    // The emitted object model carries a case class as `{ _cls, _fields, <field>: … }`, so the runtime can read and
    // write one by its field names given a codec per field; a model case class is constructed through its `_new`.
    // Nothing of fabric's macro output is emitted: its ClassRW / Definition machinery has no place here.
    def rwGenType(call: Tree): Option[TypeRepr] = {
      def find(t: Tree): Option[TypeRepr] = t match {
        case Apply(f, _)                 => find(f)
        case TypeApply(f, List(targ)) if f.symbol.exists && f.symbol.name == "gen" && ownerFqn(f.symbol).startsWith("fabric.rw.") =>
          Some(targ.tpe)
        case TypeApply(f, _)             => find(f)
        case Inlined(_, _, e)            => find(e)
        case Typed(e, _)                 => find(e)
        case _                           => None
      }
      try find(call) catch case _: Throwable => None
    }
    /** The runtime codec for a field of type `tp`: a primitive, a String or a Json value is itself; Option, List
      * and Map wrap their element's; another case class derives inline. */
    def rwFor(tp: TypeRepr, depth: Int): String = {
      val sym  = tp.typeSymbol
      val fqn  = fqnOf(tp)
      val args = try tp.typeArgs catch case _: Throwable => Nil
      if (fqn == "scala.Option" && args.nonEmpty) s"_rwOpt(${rwFor(args.head, depth)})"
      else if (args.size == 1 && Set("scala.collection.immutable.List", "scala.collection.immutable.Vector",
          "scala.collection.immutable.Seq", "scala.collection.Seq", "scala.collection.immutable.Set", "scala.Array").contains(fqn))
        s"_rwList(${rwFor(args.head, depth)})"
      else if (args.size == 2 && fqn.endsWith(".Map")) s"_rwMap(${rwFor(args(1), depth)})"
      else if (sym.exists && sym.flags.is(Flags.Case) && !isBuiltin(sym) && depth < 6) emitRwGen(tp, depth + 1)
      else s"_rw(${brStr("valueRW")})"
    }
    def emitRwGen(tp: TypeRepr, depth: Int): String = {
      val cls    = tp.typeSymbol
      val fields = try cls.caseFields catch case _: Throwable => Nil
      val types  = fields.map(f => try f.tree match { case vd: ValDef => vd.tpt.tpe; case _ => TypeRepr.of[Any] } catch case _: Throwable => TypeRepr.of[Any])
      val keysL  = fields.map(f => brStr(f.name))
      // the slot is the field name as the code READS it, which brSafe escapes for a reserved word (`type` → `type_`);
      // a plain `f.name` here left a non-model case class's `type` decoded under a key its own field access never read
      val slotsL = fields.map(f => brStr(fieldKey(f)))
      val rwsL   = types.map(t => rwFor(t, depth))
      val ctor = if (isModel(cls)) s"${typeName(cls)}_new" else "invalid"
      // a field the JSON leaves out takes its default, as fabric reads it on the JVM
      val defaultsL = fields.indices.map(i => caseDefault(cls, i))
      val defs = defaultsL.map(_.getOrElse("invalid")).mkString(", ")
      val has = defaultsL.map(d => if (d.isDefined) "true" else "false").mkString(", ")
      s"_rwGen(${brStr(cls.name)}, [${keysL.mkString(", ")}], [${slotsL.mkString(", ")}], [${rwsL.mkString(", ")}], $ctor, [$defs], [$has])"
    }
    /** The default of a case class's `i`th field (from 0), emitted, if it has one. */
    def caseDefault(cls: Symbol, i: Int): Option[String] = {
      val comp = cls.companionModule
      val getter = if (comp.exists) comp.methodMember("$lessinit$greater$default$" + (i + 1)).headOption else None
      getter.flatMap(defaultRhsOf).map(emitArgVal)
    }

    /** A default argument's value: the getter's right-hand side, from its tree or from the companion's ClassDef. */
    def defaultRhsOf(sy: Symbol): Option[Term] =
      defaultGetterRhs(sy).orElse {
        if (!sy.exists || !sy.owner.exists) None
        else allClassDefs.get(sy.owner).flatMap(_.body.collectFirst { case dd: DefDef if dd.symbol == sy => dd.rhs }.flatten)
      }
    /** A case-class literal's arguments in field order: named arguments by name, a default argument as
      * the default's own value (inlined), a field with neither as `null`. */
    def caseLiteralArgs(fields: List[String], args: List[Term]): List[Term] = {
      val byName = args.collect { case NamedArg(n, v) => n -> v }.toMap
      val positional = args.filterNot { case NamedArg(_, _) => true; case _ => false }
      var i = 0
      fields.map { f =>
        val a = byName.get(f).orElse {
          if (i < positional.size) { val p = positional(i); i += 1; Some(p) } else None
        }
        a.flatMap { t =>
          if (isDefaultArg(t)) defaultRhsOf(unwrap(t match { case NamedArg(_, v) => v; case o => o }).symbol)
          else Some(t)
        }.getOrElse(Literal(NullConstant()))
      }
    }
    /** The factory an `implicit class` generates (`ListVar(xs)`), if `sy` is one. */
    def implicitClassTarget(sy: Symbol): Symbol =
      if (!(sy.exists && sy.isDefDef && sy.flags.is(Flags.Implicit))) Symbol.noSymbol
      else {
        val rt =
          try sy.tree match {
            case dd: DefDef => dd.returnTpt.tpe.typeSymbol
            case _          => Symbol.noSymbol
          }
          catch case _: Throwable => Symbol.noSymbol
        if (rt.exists && rt.name == sy.name && (userTypeDefs.contains(rt) || isUserClassSym(rt))) rt
        else Symbol.noSymbol
      }
    /** `c.copy(apply = …)`: shallow-copy the receiver, override the fields
     *  actually given (omitted ones arrive as `copy$default$N` getters). */
    def emitCaseCopy(recv: Term, args: List[Term]): String = {
      val cls = recv.tpe.widen.dealias.typeSymbol
      val overrides = cls.caseFields.zip(flattenVarargs(args)).collect {
        case (f, arg) if !isCopyDefaultArg(arg) =>
          val v = arg match { case NamedArg(_, x) => x; case o => o }
          s"${fieldKey(f)}: ${emitArgVal(v)}"
      }
      s"_caseCopy(${emitTerm(recv)}, { ${overrides.mkString(", ")} })"
    }

    // `f(a)(b)` on a method with several term clauses is ONE call in the
    // emitted function (termParams flattens the clauses), so the Apply chain
    // flattens too. A call whose result is itself a function is left alone.
    def innermostFnSym(t: Term): Symbol = stripTypeApply(t) match {
      case Apply(f, _) => innermostFnSym(f)
      case other       => other.symbol
    }
    def isMultiClause(sy: Symbol): Boolean =
      sy.exists && sy.isDefDef && sy.paramSymss.count(c => c.headOption.exists(_.isTerm)) > 1
    def uncurry(fn: Term, args: List[Term]): (Term, List[Term]) =
      stripTypeApply(fn) match {
        case Apply(inner, innerArgs) if isMultiClause(innermostFnSym(inner)) => uncurry(inner, innerArgs ++ args)
        case _ => (fn, args)
      }

    // `super.m(…)` resolves statically to the implementation TASTy names.
    def superSel(f: Term): Option[Select] = f match {
      case sel @ Select(spr, _) if isSuper(spr) => Some(sel)
      case TypeApply(inner, _)                  => superSel(inner)
      case _                                    => None
    }

    // Default arguments. A getter on an INSTANCE (`fire$default$3` reads
    // `this.reactions()`) is emitted as a method and called; one on a companion
    // object (constructor defaults) has no `this`, so its body is inlined.
    def defaultGetterRhs(sy: Symbol): Option[Term] =
      try sy.tree match {
        case dd: DefDef if dd.termParamss.forall(_.params.isEmpty) => dd.rhs
        case _                                                     => None
      }
      catch case _: Throwable => None
    def emitDefaultArg(t: Term): String = {
      val inner = unwrap(t) match { case NamedArg(_, v) => v; case x => x }
      val sy = inner.symbol
      if (sy.exists && sy.owner.exists && sy.owner.flags.is(Flags.Module))
        defaultGetterRhs(sy).map(emitArgVal).getOrElse(emitTerm(inner))
      else emitTerm(inner)
    }

    // Scala `==` is structural for case classes and collections, not identity;
    // BrightScript `=` on two objects is a runtime type error. Primitives,
    // strings and facade values keep `=`; anything else goes through `_eq`.
    /** A String, or a String seen through Scala's StringOps (`"12".toIntOption`). */
    def isStringLike(tp: TypeRepr): Boolean = Set("java.lang.String", "scala.collection.StringOps").contains(fqnOf(tp))

    /** A Scala library method on a collection, a String or an Option that nothing above translated. Emitted as it is, it
      * is a member the BrightScript value does not have, found only when the device reaches it; as a tracked RtTodo it
      * is found by the build. */
    def isLibraryGap(recv: Term, sym: Symbol): Boolean =
      sym.exists && sym.isDefDef && ownerFqn(sym).startsWith("scala.") &&
        (isCollType(recv) || isStringLike(recv.tpe) || isOptionType(recv))
    def libraryGap(recv: Term, name: String): String = {
      val label = name + " on " + fqnOf(recv.tpe)
      todos += s"unsupported: $label${where(recv)}"
      // A gap stands in a VALUE position, so it must degrade into something of roughly the shape the caller expects.
      // `RtTodo` answers with a Label node, which is right where a gap is a thing to draw and catastrophic anywhere
      // else: the next thing to iterate it dies, and the fault surfaces in a handler far from the method that is
      // actually missing. That cost a black screensaver and a Home that stopped asking for rails, both of them
      // compiling perfectly quietly. An empty list, an empty string and None are wrong answers too, but they are
      // wrong the way missing data is wrong, and they say so on the console as they go by.
      // a String asked before a collection: `scala.collection.StringOps` begins with `scala.collection.`, so a String
      // seen through it answers to isCollType as well, and would otherwise stand in as an empty list
      if (isStringLike(recv.tpe)) s"RtTodoStr(${brStr(label)})"
      else if (isCollType(recv)) s"RtTodoList(${brStr(label)})"
      else s"RtTodoOpt(${brStr(label)})"
    }

    /** Whether a value of this type is a whole number, which BrightScript divides and truncates differently. */
    def isIntegral(tp: TypeRepr): Boolean =
      Set("scala.Int", "scala.Long", "scala.Short", "scala.Byte", "scala.Char").contains(fqnOf(tp))

    def isPrimLike(t: Term): Boolean = {
      val sy = t.tpe.widen.dealias.typeSymbol
      sy.exists && (primitiveFqns.contains(sy.fullName) || hasNative(sy))
    }

    // ── expression emission ─────────────────────────────────────────────────
    def emitRef(sym: Symbol, name: String): String =
      if (name == "Nil") "[]"
      else if (name == "None" && sym.exists && sym.fullName.endsWith(".None")) "invalid"
      else if (isFacade(sym)) name
      else if (modelModuleOf(sym).isDefined) moduleAccessor(modelModuleOf(sym).get)
      // A symbol with no denotation (some local/synthetic Idents) has no owner —
      // `sym.owner` would throw NoDenotation.owner — so emit the bare safe name.
      else if (!sym.exists || !sym.owner.exists) brSafe(name)
      // a local def without parameters (`def now = …`), named: a call of the closure it is held in
      else if (isLocalDef(sym)) localDefCall(sym, Nil)
      else {
        val boxed = boxedVars.contains(sym)
        val byName = isByNameParam(sym)
        // a by-name constructor parameter read from a method or closure of its class: the field it was kept in
        val byNameField = byName && anonCaptureRef(sym).isEmpty && !inParentArgs && sym.flags.is(Flags.ParamAccessor) &&
          currentClass.exists && inCurrentClass(sym.owner)
        val local = anonCaptureRef(sym).orElse(if (boxed || byName) Some(brLocal(name)) else None)
        local match {
          // a captured box reads its cell; a by-name parameter is forced on
          // EVERY use (Scala re-evaluates it — reactify's dependency tracking
          // depends on exactly that)
          case Some(ref) =>
            if (boxed) ref + ".v" else if (byNameField) s"RtForce(self.${fieldKey(sym)})" else if (byName) s"RtForce($ref)" else ref
          case None =>
            if (inParentArgs && sym.flags.is(Flags.ParamAccessor) && currentClass.exists && sym.owner == currentClass) brLocal(name)
            else if (isModelMember(sym) && inCurrentClass(sym.owner)) emitModelCall(None, sym, Nil)
            else if (inCurrentClass(sym.owner)) s"self.${fieldKey(sym)}"
            else moduleSyms.get(sym.owner) match {
              case Some(prefix) => s"${prefix}_${memberName(sym, name)}()"
              case None         => if (isLocalSym(sym)) brLocal(name) else brSafe(name)
            }
        }
      }

    /** A call of a local def: the closure held in its local (or, from inside a local class, the one it captured). */
    def localDefCall(sym: Symbol, args: List[Term]): String = {
      val r = anonCaptureRef(sym).getOrElse(brLocal(sym.name))
      val a = emitOrdered(argParts(args, sym)).filter(_ != dropArg)
      s"$r.fn($r.env, [${a.mkString(", ")}])"
    }

    def rep(t: Term): List[Term] = unwrap(t) match {
      case Repeated(es, _) => es
      case _               => Nil
    }

    def emitStringInterp(outer: Term): Option[String] = unwrap(outer) match {
      case Apply(Select(inner, "s"), List(argsArg)) =>
        unwrap(inner) match {
          case Apply(Select(sc, "apply"), List(partsArg)) if sc.symbol.fullName.contains("StringContext") =>
            val parts = rep(partsArg).collect { case Literal(StringConstant(s)) => s }
            val args  = rep(argsArg)
            val pieces = mutable.ListBuffer[String]()
            for (i <- args.indices) {
              if (i < parts.length && parts(i).nonEmpty) pieces += brStr(parts(i))
              pieces += s"_str(${emitTerm(args(i))})"
            }
            if (parts.length > args.length && parts.last.nonEmpty) pieces += brStr(parts.last)
            Some(if (pieces.isEmpty) "\"\"" else pieces.mkString(" + "))
          case _ => None
        }
      case _ => None
    }

    def someArg(t: Term): Option[Term] = t match {
      case Apply(fn, List(x)) =>
        stripTypeApply(fn) match {
          case Select(q, "apply") if q.symbol.exists && q.symbol.fullName.endsWith(".Some") => Some(x)
          case _ => None
        }
      case _ => None
    }

    def mathNames: Set[String] =
      Set("sqrt", "pow", "sin", "cos", "tan", "atan", "exp", "log", "abs", "min", "max", "floor", "ceil", "round", "random")
    /** `scala.math` onto BrightScript's numeric builtins. */
    def emitMath(name: String, args: List[Term]): String = {
      val a = flattenVarargs(args).map(emitArgVal)
      name match {
        case "sqrt"  => s"Sqr(${a(0)})"
        case "pow"   => s"(${a(0)} ^ ${a(1)})"
        case "sin"   => s"Sin(${a(0)})"
        case "cos"   => s"Cos(${a(0)})"
        case "tan"   => s"Tan(${a(0)})"
        case "atan"  => s"Atn(${a(0)})"
        case "exp"   => s"Exp(${a(0)})"
        case "log"   => s"Log(${a(0)})"
        case "abs"   => s"Abs(${a(0)})"
        case "min"   => s"RtMin(${a(0)}, ${a(1)})"
        case "max"   => s"RtMax(${a(0)}, ${a(1)})"
        case "floor" => s"Int(${a(0)})"
        case "ceil"  => s"(-Int(-(${a(0)})))"
        case "random" => "Rnd(0)"
        case _       => s"Int(${a(0)} + 0.5)"
      }
    }

    /** Milliseconds per unit of a `scala.concurrent.duration` conversion (`5.seconds`). */
    def durationFactor(conv: Term, unit: String): Option[String] = {
      val sy = stripTypeApply(conv).symbol
      if (!(sy.exists && Set("DurationInt", "DurationLong", "DurationDouble").contains(sy.name))) None
      else unit match {
        case "nanoseconds" | "nanos" | "nanosecond" | "nano"      => Some("0.000001")
        case "microseconds" | "micros" | "microsecond" | "micro"  => Some("0.001")
        case "milliseconds" | "millis" | "millisecond" | "milli"  => Some("1")
        case "seconds" | "second"                                 => Some("1000")
        case "minutes" | "minute"                                 => Some("60000")
        case "hours" | "hour"                                     => Some("3600000")
        case "days" | "day"                                       => Some("86400000")
        case _                                                    => None
      }
    }

    /** Scala compiles `super.m(...)` inside a trait into a call to a synthetic accessor `pkg$T$$super$m`, which
      * only exists after mixin. Resolve it statically to the nearest user implementation in T's base classes. */
    def isSuperAccessor(sy: Symbol): Boolean = sy.exists && sy.name.contains("$$super$")
    def superAccessorImpl(sy: Symbol): Option[Symbol] =
      if (!isSuperAccessor(sy) || !sy.owner.exists) None
      else {
        val m = sy.name.substring(sy.name.lastIndexOf("$$super$") + "$$super$".length)
        val arity = arityOf(sy)
        sy.owner.typeRef.baseClasses.drop(1).iterator.filter(userTypeDefs.contains).flatMap { b =>
          userTypeDefs(b).body.collectFirst {
            case dd: DefDef if dd.name == m && emittableMethod(dd) && arityOf(dd.symbol) == arity => dd.symbol
          }
        }.nextOption()
      }

    /** The constructor a `new C(...)` / parent `C(...)` call invokes. */
    def parentCtor(p: Tree): Symbol = {
      def peel(t: Term): Symbol = t match {
        case Apply(f, _)             => peel(f)
        case TypeApply(f, _)         => peel(f)
        case sel @ Select(New(_), _) => sel.symbol
        case Block(_, e)             => peel(e)
        case _                       => Symbol.noSymbol
      }
      p match {
        case t: Term => peel(t)
        case _       => Symbol.noSymbol
      }
    }
    def secondaryCtors(cls: Symbol): List[DefDef] =
      userTypeDefs.get(cls).map(_.body.collect {
        case dd: DefDef if dd.name == "<init>" && dd.symbol != cls.primaryConstructor && dd.rhs.isDefined => dd
      }).getOrElse(Nil)
    /** `C__init` for the primary constructor, `C__init__k` for the k-th secondary one. */
    def initName(cls: Symbol, ctor: Symbol): String = {
      val k = if (!ctor.exists || ctor == cls.primaryConstructor) 0 else secondaryCtors(cls).map(_.symbol).indexOf(ctor) + 1
      if (k <= 0) s"${typeName(cls)}__init" else s"${typeName(cls)}__init__$k"
    }

    /** The singleton instance a model module's term or class refers to, if any. */
    def modelModuleOf(sy: Symbol): Option[Symbol] =
      if (!sy.exists) None
      else if (sy.isClassDef) Some(sy).filter(modelModules.contains)
      else if (sy.flags.is(Flags.Module)) {
        val mc = try sy.moduleClass catch case _: Throwable => Symbol.noSymbol
        Some(mc).filter(m => m.exists && modelModules.contains(m))
      }
      else None
    /** A case object is initialised once at startup (rtMain) and then read straight from the global AA; any
      * other object (or code running inside a module's own initialisation) goes through its lazy accessor. */
    def moduleAccessor(mc: Symbol): String =
      if (mc.flags.is(Flags.Case) && !(currentClass.exists && modelModules.contains(currentClass)))
        s"ga.__mod_${typeName(mc)}"
      else s"${typeName(mc)}__module()"

    def emitApply(t: Term): String = {
      someArg(t) match {
        case Some(x) => return emitArgVal(x)
        case None    =>
      }
      emitStringInterp(t) match {
        case Some(s) => return s
        case None    =>
      }
      // `opt.fold(ifEmpty)(f)` → runtime invalid-check helper.
      optionFold(t) match {
        case Some((recv, ifEmpty, f)) =>
          return s"_optFold(${emitTerm(recv)}, ${emitArgVal(ifEmpty)}, ${emitArgVal(f)})"
        case None =>
      }
      t match {
        case Apply(fn00, args00) =>
          val (fn0, args) = uncurry(fn00, args00)
          val fn = stripTypeApply(fn0)
          if (isListApply(fn))
            return "[" + flattenVarargs(args).filterNot(a => fqnOf(a.tpe) == "scala.reflect.ClassTag").map(emitArgVal).mkString(", ") + "]"
          caseApply(t.tpe) match {
            case Some(_) if isCaseCtor(fn, t.tpe) && isModel(t.tpe.typeSymbol) =>
              return s"${typeName(t.tpe.typeSymbol)}_new(${emitArgs(args, fn.symbol)})"
            case Some(fields) if isCaseCtor(fn, t.tpe) =>
              val flat = caseLiteralArgs(fields, flattenVarargs(args))
              val fs = fields.zip(flat).map((f, a) => s"$f: ${emitArgVal(a)}")
              val fieldList = fields.take(flat.size).map(f => brStr(f)).mkString(", ")
              return "{ " + (s"_cls: ${brStr(t.tpe.typeSymbol.name)}" :: s"_fields: [$fieldList]" :: fs).mkString(", ") + " }"
            case _ =>
          }
          fn match {
            // ── a local def, called through the closure it is held in (see `emitStats`) ──
            case Ident(_) if isLocalDef(fn.symbol) => localDefCall(fn.symbol, args)
            // ── a call into a type nothing ever constructs: dead at runtime (see "reachability") ──
            case _ if pruning && fn.symbol.exists && fn.symbol.isDefDef && !fn.symbol.isClassConstructor
                && allClassDefs.contains(fn.symbol.owner) && !keepClass(fn.symbol.owner) =>
              s"RtDead(${brStr(fn.symbol.owner.name.stripSuffix("$") + "." + fn.symbol.name)})"
            // ── a fabric codec made of others (`optionRW(intRW)`, given to `.json`): the runtime's wrapper of them ──
            case _ if isFabricRW(fn.symbol) && args.nonEmpty =>
              val inner = args.map(emitArgVal)
              fn.symbol.name match {
                case "optionRW"                                   => s"_rwOpt(${inner.head})"
                case "listRW" | "vectorRW" | "setRW" | "arrayRW" => s"_rwList(${inner.head})"
                case "mapRW"                                      => s"_rwMap(${inner.last})"
                case n                                            => s"_rw(${brStr(n)})"
              }
            // ── rapid.Task (see "library shims") ──
            case Select(q, m) if isTaskModule(q) =>   // `Task.unit`, `Task.sleep` … inherited by the companion too
              val ea = explicitArgs(fn.symbol, args)
              m match {
                case "apply" | "defer" if ea.size == 1 => s"RapidTaskObj_$m(${emitThunk(ea.head)})"
                case _                                 => shimCall(s"RapidTaskObj_$m", None, ea)
              }
            case _ if isRapidTaskCompanion(fn) =>
              val ea = explicitArgs(fn.symbol, args)
              fn.symbol.name match {
                case m @ ("apply" | "defer") if ea.size == 1 => s"RapidTaskObj_$m(${emitThunk(ea.head)})"
                case m                                       => shimCall(s"RapidTaskObj_$m", None, ea)
              }
            case Select(recv, m) if isRapidTask(fn) =>
              val ea = explicitArgs(fn.symbol, args)
              m match {
                case "apply" if ea.isEmpty       => shimCall("RapidTask_sync", Some(recv), Nil)
                case "guarantee" if ea.size == 1 => s"RapidTask_guarantee(${emitTerm(recv)}, ${emitThunk(ea.head)})"
                case _                           => shimCall(s"RapidTask_$m", Some(recv), ea)
              }
            // ── fabric.Json, its builders, the parser and formatter, RW ──
            // `RW.gen[T]` reaches here as a plain call (the macro's expansion is not in the TASTy the emitter reads)
            case _ if fn.symbol.exists && fn.symbol.name == "gen" && ownerFqn(fn.symbol).startsWith("fabric.rw.")   // RW.gen lives in CompileRW
                && (try t.tpe.widen.typeArgs.nonEmpty catch case _: Throwable => false) =>
              emitRwGen(t.tpe.widen.typeArgs.head, 0)   // the call's type is RW[T]
            case Select(recv, "apply") if isFabricJson(fn) && args.size == 1 => shimCall("_jsonGet", Some(recv), args)
            case Select(recv, "get") if isFabricJson(fn) && args.size == 1   => shimCall("_jsonOpt", Some(recv), args)
            case Select(recv, m) if isFabricJson(fn) => shimCall(s"_json_$m", Some(recv), explicitArgs(fn.symbol, args))
            case _ if isFabricBuilder(fn) =>
              fn.symbol.name match {
                // a spliced list (`obj(pairs*)`) is the array itself
                case "obj" => s"_jsonObj(${varargsArray(args)})"
                case "arr" => varargsArray(args)
                case _     => emitArgVal(args.head)
              }
            case _ if fnIs(fn, "fabric.io.JsonParser", "apply") => s"_jsonParse(${emitArgVal(args.head)})"
            case Select(_, "apply") if ownerIs(fn, "fabric.io.JsonFormatter") && args.size == 1 =>
              s"_jsonFormat(${emitArgVal(args.head)})"
            case Select(wrapped, "json") if isFabricWrapper(fn, "Convertible") && args.size == 1 =>
              s"_rwWrite(${emitArgVal(args.head)}, ${emitTerm(unwrapCtor(wrapped))})"
            case Select(wrapped, "as") if isFabricWrapper(fn, "Asable") && args.size == 1 =>
              s"_rwRead(${emitArgVal(args.head)}, ${emitTerm(unwrapCtor(wrapped))})"
            // ── scribe ──
            case _ if isScribeLog(fn) =>
              s"Scribe_log(${brStr(fn.symbol.name)}, [${flattenVarargs(explicitArgs(fn.symbol, args)).map(emitArgVal).mkString(", ")}])"
            case _ if fn.symbol.exists && fn.symbol.name.endsWith("2LoggableMessage") && args.size == 1 => emitArgVal(args.head)
            // ── java.util.concurrent, UUID, System, Math; Predef's wrappers ──
            case Select(recv, m) if ownerIs(fn, "java.util.concurrent.ConcurrentHashMap") && !fn.symbol.isClassConstructor =>
              shimCall(s"ConcurrentHashMap_$m", Some(recv), args)
            case Select(recv, m) if ownerIs(fn, "java.util.concurrent.ConcurrentLinkedQueue") && !fn.symbol.isClassConstructor =>
              shimCall(s"ConcurrentLinkedQueue_$m", Some(recv), args)
            case Select(recv, m)
                if (ownerIs(fn, "java.util.concurrent.atomic.AtomicLong") || ownerIs(fn, "java.util.concurrent.atomic.AtomicInteger"))
                   && !fn.symbol.isClassConstructor =>
              shimCall(s"AtomicLong_$m", Some(recv), args)
            case _ if fnIs(fn, "java.util.UUID", "randomUUID")           => "_uuid()"
            case _ if fnIs(fn, "java.lang.System", "currentTimeMillis")  => "_nowMillis()"
            case _ if fnIs(fn, "java.lang.System", "identityHashCode") && args.size == 1 => s"_identityHash(${emitArgVal(args.head)})"
            // a string here is read by character, never by UTF-16 unit, so no character is half of a surrogate pair
            case _ if (fnIs(fn, "java.lang.Character", "isHighSurrogate") || fnIs(fn, "java.lang.Character", "isLowSurrogate"))
                && args.size == 1 => "false"
            case _ if fn.symbol.exists && ownerFqn(fn.symbol) == "java.lang.Math" => emitMath(fn.symbol.name, args)
            case Select(Apply(wrap, List(x)), m @ ("min" | "max" | "abs" | "round" | "floor" | "ceil")) if isRichNumWrapper(wrap) => emitMath(m, x :: args)
            // RichChar predicates (charWrapper is in the rich-number set): a Char is a one-character string here
            case Select(Apply(wrap, List(x)), m @ ("isDigit" | "isLetter" | "isLetterOrDigit" | "isWhitespace" | "isSpaceChar" | "isUpper" | "isLower")) if isRichNumWrapper(wrap) =>
              s"_ch_$m(${emitTerm(x)})"
            case _ if fn.symbol.exists && primitiveCompanions.contains(ownerFqn(fn.symbol))
                && fn.symbol.name.matches("[a-z]+2[a-z]+") && args.size == 1 => emitTerm(args.head)
            case _ if fn.symbol.exists && fn.symbol.name == "augmentString" && args.size == 1 => emitTerm(args.head)
            case Select(recv, "getMessage") if args.isEmpty => s"_getMessage(${emitTerm(recv)})"
            case Select(recv, "getSimpleName" | "getName") if args.isEmpty && getClassRecv(recv).isDefined =>
              s"_clsName(${emitTerm(getClassRecv(recv).get)})"
            // ── stdlib the emitter models directly (runtime shims) ──
            case _ if fnIs(fn, "scala.Option", "apply") && args.size == 1 => emitArgVal(args.head)
            // `Try(body)`: the body run under a try, answering a Success or a Failure as the runtime's Try ops read them
            case _ if fnIs(fn, "scala.util.Try", "apply") && args.size == 1 => s"_tryApply(${emitThunk(args.head)})"
            case _ if fnIs(fn, "scala.concurrent.Promise", "apply") => "Promise_new()"
            case Select(recv, "success") if ownerIs(fn, "scala.concurrent.Promise") =>
              s"_promiseSuccess(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "map") if ownerIs(fn, "scala.concurrent.Future") =>
              s"_futureMap(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, m) if ownerIs(fn, "java.lang.ThreadLocal") && !fn.symbol.isClassConstructor =>
              s"ThreadLocal_$m(${(emitTerm(recv) :: emitArgList(args)).mkString(", ")})"
            case Select(recv, m) if ownerIs(fn, "java.util.concurrent.atomic.AtomicBoolean") && !fn.symbol.isClassConstructor =>
              s"AtomicBoolean_$m(${(emitTerm(recv) :: emitArgList(args)).mkString(", ")})"
            case Select(Apply(wrap, List(x)), "compare") if isRichNumWrapper(wrap) && args.size == 1 =>
              s"_cmpNum(${emitTerm(x)}, ${emitArgVal(args.head)})"
            case Ident("->" | "$minus$greater") if args.size == 2 =>
              s"Tuple2(${emitTerm(args(0))}, ${emitArgVal(args(1))})"
            case Select(_, name) if isPrimitiveExtension(fn) => emitPrimitiveExtension(fn, name, args)
            case Select(Apply(arrow, List(k)), "->" | "$minus$greater") if isArrowAssoc(arrow) && args.size == 1 =>
              s"Tuple2(${emitTerm(k)}, ${emitArgVal(args.head)})"
            case _ if fn.symbol.exists && fn.symbol.name == "assertFailed" =>
              s"RtThrow(AssertionError_new(${if (args.isEmpty) "invalid" else emitArgVal(args.head)}))"
            case _ if fnIs(fn, "scala.Predef", "assert") =>
              s"RtAssert(${emitArgVal(args.head)}${if (args.size > 1) ", " + emitThunk(args(1)) else ""})"
            case _ if fn.symbol.exists && fn.symbol.name == "synchronized" && args.size == 1 =>
              s"RtSynchronized(${emitThunk(args.head)})"
            case Select(recv, "getOrElse") if isOptionType(recv) =>
              val d = args.head match { case NamedArg(_, x) => x; case o => o }
              if (pureValue(d)) s"_getOrElse(${emitTerm(recv)}, ${emitArgVal(d)})"
              else s"_getOrElseThunk(${emitTerm(recv)}, ${emitThunk(d)})"
            case Select(recv, "orElse") if isOptionType(recv) =>
              s"_optOrElse(${emitTerm(recv)}, ${emitThunk(args.head)})"
            case Select(recv, "contains") if isOptionType(recv) =>
              if (isModuleOperand(args.head)) s"RtIdentity(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              else s"_optContains(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // (the module by its type: `Set` is Predef's alias for it, a val, whose own symbol is no module)
            case Select(q, "apply") if isSetTpe(t.tpe) && (q.symbol.exists && q.symbol.flags.is(Flags.Module) ||
                q.tpe.widen.typeSymbol.flags.is(Flags.Module)) && !isFacadeCall(fn) =>
              s"_distinct([${flattenVarargs(args).map(emitArgVal).mkString(", ")}])"
            case Select(q, "apply") if isMapTpe(t.tpe) && q.tpe.widen.typeSymbol.flags.is(Flags.Module) && !isFacadeCall(fn) =>
              s"_mapFromPairs(${varargsArray(args)})"
            case _ if ownerIs(fn, "scala.math.package") && mathNames.contains(fn.symbol.name) =>
              emitMath(fn.symbol.name, args)
            case Select(q, "apply") if q.symbol.exists && q.symbol.fullName.startsWith("scala.StringContext") =>
              varargsArray(args)
            case Select(recv, "s") if fqnOf(recv.tpe) == "scala.StringContext" =>
              s"_strInterp(${emitTerm(recv)}, ${varargsArray(args)})"
            case Select(recv, "length") if fqnOf(recv.tpe) == "java.lang.String" && args.isEmpty =>
              s"Len(${emitTerm(recv)})"
            case Select(recv, "isEmpty") if fqnOf(recv.tpe) == "java.lang.String" && args.isEmpty =>
              s"(Len(${emitTerm(recv)}) = 0)"
            case Select(recv, "indexOf") if fqnOf(recv.tpe) == "java.lang.String" && args.size == 1 =>
              s"_strIndexOf(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "lastIndexOf") if fqnOf(recv.tpe) == "java.lang.String" && args.size == 1 =>
              s"_strLastIndexOf(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "+" | "incl") if isSetTpe(recv.tpe) && args.size == 1 =>
              s"_setAdd(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "-" | "excl") if isSetTpe(recv.tpe) && args.size == 1 =>
              s"_setRemove(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // s + (a, b, cs*) and s - (a, b, cs*)
            case Select(recv, "+") if isSetTpe(recv.tpe) => s"_setUnion(${emitTerm(recv)}, ${varargsArray(args)})"
            case Select(recv, "-") if isSetTpe(recv.tpe) => s"_setDiff(${emitTerm(recv)}, ${varargsArray(args)})"
            case Select(recv, "++" | "union" | "concat" | "|") if isSetTpe(recv.tpe) =>
              s"_setUnion(${emitTerm(recv)}, ${emitElems(args.head)})"
            case Select(recv, "diff" | "--" | "&~" | "removedAll") if isSetTpe(recv.tpe) =>
              s"_setDiff(${emitTerm(recv)}, ${emitElems(args.head)})"
            case Select(recv, "intersect" | "&") if isSetTpe(recv.tpe) =>
              s"_setIntersect(${emitTerm(recv)}, ${emitElems(args.head)})"
            case Select(q, "from") if isSetTpe(t.tpe) && q.tpe.widen.typeSymbol.flags.is(Flags.Module) && args.size == 1 =>
              s"_distinct(${emitElems(args.head)})"
            // A Map joined with a Map, or with pairs from anything, is a Map: joined as arrays it was an array, and the
            // next lookup in it failed (a Map ++ a Map came out a list of the one's entries and the other's)
            case Select(recv, "++" | "concat") if isMapTpe(recv.tpe) && isMapTpe(t.tpe) && args.size == 1 =>
              s"_mapConcat(${emitTerm(recv)}, ${emitElems(args.head)})"
            case Select(recv, "+") if isMapTpe(recv.tpe) && args.size > 1 =>   // m + (p1, p2, ps*)
              s"_mapConcat(${emitTerm(recv)}, ${varargsArray(args)})"
            case Select(recv, "--" | "removedAll") if isMapTpe(recv.tpe) && args.size == 1 =>
              s"_mapRemoveAll(${emitTerm(recv)}, ${emitElems(args.head)})"
            case Select(recv, "-") if isMapTpe(recv.tpe) && args.size > 1 =>   // m - (k1, k2, ks*)
              s"_mapRemoveAll(${emitTerm(recv)}, ${varargsArray(args)})"
            case Select(q, "from") if isMapTpe(t.tpe) && q.tpe.widen.typeSymbol.flags.is(Flags.Module) && args.size == 1 =>
              s"_mapConcat(_mapEmpty(), ${emitElems(args.head)})"
            // `xs ++: ys` is `ys.++:(xs)`: the argument's elements first, then the receiver's, as the receiver's kind
            case Select(recv, "++:" | "$plus$plus$colon" | "prependedAll") if isCollType(recv) && args.size == 1 =>
              val joined = s"_concat(${emitElems(args.head)}, ${emitElems(recv)})"
              if (isMapTpe(t.tpe)) s"_mapConcat(_mapEmpty(), $joined)"
              else if (isSetTpe(t.tpe)) s"_distinct($joined)"
              else joined
            // ── a sequence's library methods (List, Vector, Seq, an Array's ArrayOps; a String's characters where it
            // answers with a number) ──
            case Select(Select(recv, "lift"), "apply") if isSeqLike(recv) && args.size == 1 =>
              s"_lift(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // (the start is a default argument where it is left out)
            case Select(recv, "indexWhere") if (isSeqLike(recv) || isStringLike(recv.tpe)) && args.size == 2 =>
              s"_arrIndexWhereFrom(${emitTerm(recv)}, ${emitArgVal(args(0))}, ${orZero(args(1))})"
            case Select(q, name @ ("range" | "fill" | "tabulate")) if isSeqTpe(t.tpe) && isModuleRef(q) =>
              val passed = args.filterNot(a => fqnOf(a.tpe).startsWith("scala.math."))   // (a range's Integral)
              name match {
                case "range" if passed.size == 2 => s"_rangeBy(${emitTerm(passed(0))}, ${emitTerm(passed(1))}, 1, false)"
                case "range" if passed.size == 3 =>
                  s"_rangeBy(${emitTerm(passed(0))}, ${emitTerm(passed(1))}, ${emitTerm(passed(2))}, false)"
                case "fill" if passed.size == 2 => s"_fill(${emitTerm(passed(0))}, ${emitThunk(passed(1))})"
                case "tabulate" if passed.size == 2 => s"_tabulate(${emitTerm(passed(0))}, ${emitArgVal(passed(1))})"
                case _ => libraryGap(q, name)
              }
            case Select(recv, "partition") if isSeqLike(recv) && args.size == 1 =>
              s"_partition(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "sortWith") if isSeqLike(recv) && args.size == 1 =>
              s"_sortWith(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "reduce" | "reduceLeft") if isSeqLike(recv) && args.size == 1 =>
              s"_reduce(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "updated") if isSeqLike(recv) && args.size == 2 =>
              s"_seqUpdated(${emitTerm(recv)}, ${emitArgVal(args(0))}, ${emitArgVal(args(1))})"
            // `xs.collect { case … }`: the cases as one function, an element none of them takes (its MatchError) left out
            case Select(recv, "collect") if isSeqLike(recv) && args.size == 1 =>
              s"_collect(${emitTerm(recv)}, ${emitArgVal(args.head)}, false)"
            case Select(recv, "collectFirst") if isSeqLike(recv) && args.size == 1 =>
              s"_collect(${emitTerm(recv)}, ${emitArgVal(args.head)}, true)"
            // `a to b by s` and `a until b by s`: the bounds, since the range made first is already its values (and
            // counting down it has none)
            case Select(recv, "by") if fqnOf(recv.tpe).startsWith("scala.collection.immutable.Range") && args.size == 1 =>
              rangeBounds(recv) match {
                case Some((a, b, incl)) => s"_rangeBy(${emitTerm(a)}, ${emitTerm(b)}, ${emitTerm(args.head)}, $incl)"
                case None => libraryGap(recv, "by")
              }
            case Select(recv, "contains") if isSetTpe(recv.tpe) =>
              s"_setHas(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "get" | "apply") if isMapTpe(recv.tpe) && args.size == 1 =>
              s"_mapGet(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // a Map's getOrElse takes the key and the default (an Option's takes only the default)
            case Select(recv, "getOrElse") if isMapTpe(recv.tpe) && args.size == 2 =>
              s"_mapGetOrElse(${emitTerm(recv)}, ${emitArgVal(args(0))}, ${emitArgVal(args(1))})"
            case Select(recv, "contains") if isMapTpe(recv.tpe) =>
              s"_mapHas(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "+" | "updated") if isMapTpe(recv.tpe) =>
              if (args.size == 1) s"_mapPutT(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              else s"_mapPut(${emitTerm(recv)}, ${emitArgVal(args(0))}, ${emitArgVal(args(1))})"
            case Select(recv, "-" | "removed") if isMapTpe(recv.tpe) && args.size == 1 =>
              s"_mapRemove(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // a Map walked as its entries, as pairs: going over the map itself went over an assoc array's keys alone. A
            // map, flatMap or filter that answers with a Map is made one again from the pairs
            case Select(recv, op @ ("map" | "flatMap" | "filter" | "filterNot" | "foreach" | "exists" | "forall" | "find" | "count"))
                if isMapTpe(recv.tpe) && args.size == 1 =>
              val helper = op match {
                case "flatMap" => "_arrFlatMap"
                case "filterNot" => "_arrFilterNot"
                case "count" => "_arrCount"
                case other => "_arr" + other.capitalize
              }
              val walked = s"$helper(_mapToList(${emitTerm(recv)}), ${emitArgVal(args.head)})"
              if (isMapTpe(t.tpe)) s"_mapOfPairs($walked)" else walked
            // a String's character at an index (a Char is a one-character string here), and the next place of a
            // character or a string at or after an index
            case Select(recv, "charAt") if fqnOf(recv.tpe) == "java.lang.String" && args.size == 1 =>
              s"Mid(${emitTerm(recv)}, ${emitArgVal(args.head)} + 1, 1)"
            case Select(recv, "indexOf") if fqnOf(recv.tpe) == "java.lang.String" && args.size == 2 =>
              s"_strIndexFrom(${emitTerm(recv)}, ${emitArgVal(args(0))}, ${emitArgVal(args(1))})"
            case Select(recv, "sorted") if isCollType(recv) =>
              if (elemIsUserType(recv)) s"_sortedOrdered(${emitTerm(recv)})" else s"_sortedPrim(${emitTerm(recv)})"
            case Select(recv, "copy") if { val c = recv.tpe.widen.dealias.typeSymbol; c.exists && c.flags.is(Flags.Case) && !isBuiltin(c) } =>
              emitCaseCopy(recv, args)
            case _ if implicitClassTarget(fn.symbol).exists =>
              s"${typeName(implicitClassTarget(fn.symbol))}_new(${emitArgs(args)})"
            case Select(New(tpt), _) if hasNative(tpt.tpe.typeSymbol) =>
              s"${tpt.tpe.typeSymbol.name}(${emitFacadeCtorArgs(tpt.tpe.typeSymbol.primaryConstructor, args)})"
            case Select(New(tpt), _) if isUserClassSym(tpt.tpe.typeSymbol) =>
              s"${typeName(tpt.tpe.typeSymbol)}_new(${emitArgs(args, fn.symbol)})"
            case Select(New(tpt), _) =>
              s"${typeName(tpt.tpe.typeSymbol)}_new(${emitArgs(args, fn.symbol)})"
            // var setter x.p_=(v) → assignment
            case Select(recv, setter) if setter.endsWith("_=") && args.length == 1 && !userMethodSyms.contains(fn.symbol) =>
              s"${emitTerm(recv)}.${setter.dropRight(2)} = ${emitTerm(args.head)}"
            case _ if isSuperAccessor(fn.symbol) =>
              superAccessorImpl(fn.symbol) match {
                case Some(impl) => s"${modelImplName(impl)}(self${emitArgList(args, impl).map(", " + _).mkString})"
                case None       => "invalid"
              }
            // Object-model dispatch (see "object model" above).
            case Select(recv, _) if isModelMember(fn.symbol) && !isFacade(fn.symbol) && !isSuper(recv) =>
              emitModelCall(Some(recv), fn.symbol, args)
            case Ident(_) if isModelMember(fn.symbol) && !isFacade(fn.symbol) =>
              emitModelCall(None, fn.symbol, args)
            // `x == null` / `x ne null` on an object: a direct test, not a call.
            case Select(recv, op @ ("==" | "!=" | "eq" | "ne")) if args.length == 1 && nullCompared(recv, args.head).isDefined =>
              val cmp = if (op == "==" || op == "eq") "=" else "<>"
              s"(${emitTerm(nullCompared(recv, args.head).get)} $cmp invalid)"
            case Select(recv, op @ ("==" | "!=")) if args.length == 1 && (isModuleOperand(recv) || isModuleOperand(args.head)) =>
              val e = s"RtIdentity(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              if (op == "==") e else s"(not $e)"
            // Scala equality / identity on non-primitive operands.
            case Select(recv, op @ ("==" | "!=")) if args.length == 1 && !(isPrimLike(recv) && isPrimLike(args.head)) =>
              val e = s"_eq(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              if (op == "==") e else s"(not $e)"
            case Select(recv, op @ ("eq" | "ne")) if args.length == 1 =>
              val e = s"RtIdentity(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              if (op == "eq") e else s"(not $e)"
            case Select(recv, "apply") if args.length == 1 && isCollType(recv) =>
              s"${emitTerm(recv)}[${emitTerm(args.head)}]"
            // Invoking a function VALUE (`f(x)` where f: T => R desugars to
            // f.apply(x)). A function value is a defunctionalised {fn, env}
            // closure, so it must be called via RtInvoke, not `f(x)`.
            case Select(recv, "apply") if isFunctionType(recv.tpe) =>
              val r = emitTerm(recv)
              val a = emitArgs(args, fn.symbol)
              // A closure held in a local or a field chain is called in place — one call fewer than RtInvoke.
              if (r.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*") && r != "invalid") s"$r.fn($r.env, [$a])"
              else s"RtInvoke($r, [$a])"
            // A method DECLARED on a user class dispatches to its own emitted
            // function — it must win over the collection/string/Option builtins
            // below, which match on NAME alone. `Var.map` is not `List.map`,
            // and a UI layer is full of `map`/`filter`/`find`/`contains`.
            case Select(recv, name)
                if fn.symbol.exists && fn.symbol.isDefDef && fn.symbol.flags.is(Flags.Method)
                   && isUserClassSym(fn.symbol.owner) && !isFacade(fn.symbol) =>
              val a = emitArgs(args, fn.symbol)
              val tail = if (a.isEmpty) "" else ", " + a
              if (isThis(recv) && inCurrentClass(fn.symbol.owner))
                s"${typeName(currentClass)}_${memberName(fn.symbol, name)}(self$tail)"
              else s"${typeName(fn.symbol.owner)}_${memberName(fn.symbol, name)}(${emitTerm(recv)}$tail)"
            // A method on a user `object` → the flat `Obj_m(args)` function
            // emitModule wrote. Matches the BARE-Ident form too (`build(x)`
            // inside the same object, where Scala drops the qualifier):
            // emitRef renders such a reference as `Obj_m()`, assuming a
            // no-arg getter, and the generic tail would then apply it again.
            case _ if fn.symbol.exists && fn.symbol.isDefDef && fn.symbol.flags.is(Flags.Method)
                      && moduleSyms.contains(fn.symbol.owner) =>
              s"${moduleSyms(fn.symbol.owner)}_${memberName(fn.symbol, fn.symbol.name)}(${emitArgs(args, fn.symbol)})"
            case Select(recv, ":+" | "$colon$plus") =>
              s"_append(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "+:" | "$plus$colon") =>  // x +: xs (prepend)
              s"_prepend(${emitTerm(args.head)}, ${emitTerm(recv)})"
            case Select(recv, "::") =>  // x :: xs (cons/prepend); recv is xs
              s"_prepend(${emitTerm(args.head)}, ${emitTerm(recv)})"
            // `xs ::: ys` is RIGHT-associative: it desugars to `ys.:::(xs)`, so
            // the RECEIVER is the suffix. Concatenating receiver-first turned
            // every `list ::: List(x)` append into a prepend.
            case Select(recv, ":::") =>
              s"_concat(${emitTerm(args.head)}, ${emitTerm(recv)})"
            case Select(recv, "++") =>  // xs ++ ys (left-associative)
              s"_concat(${emitElems(recv)}, ${emitElems(args.head)})"
            case Select(recv, "concat" | ":++" | "$colon$plus$plus" | "appendedAll") if isCollType(recv) && args.size == 1 =>
              s"_concat(${emitElems(recv)}, ${emitElems(args.head)})"
            // ── String methods → BrightScript string builtins/helpers ──
            case Select(recv, "substring") if args.length == 2 =>
              s"_substr(${emitTerm(recv)}, ${emitTerm(args(0))}, ${emitTerm(args(1))})"
            case Select(recv, "substring") if args.length == 1 =>
              s"_substr1(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "toLowerCase") => s"LCase(${emitTerm(recv)})"
            case Select(recv, "toUpperCase") => s"UCase(${emitTerm(recv)})"
            case Select(recv, "trim")        => s"_trim(${emitTerm(recv)})"
            case Select(recv, "split")       => s"_split(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "startsWith")  => s"_startsWith(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "endsWith")    => s"_endsWith(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "stripPrefix") if args.size == 1 => s"_stripPrefix(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "stripSuffix") if args.size == 1 => s"_stripSuffix(${emitTerm(recv)}, ${emitTerm(args.head)})"
            // String ordering: BrightScript strings have no `.compareTo` member, so lower it to a three-way
            // helper. The app compares `yyyy-MM-dd` dates this way (Dates.past, TheaterGuide.events).
            case Select(recv, "compareTo") if isStringLike(recv.tpe) && args.length == 1 =>
              s"_strCompare(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "replace") if args.length == 2 =>
              s"_replace(${emitTerm(recv)}, ${emitTerm(args(0))}, ${emitTerm(args(1))})"
            // FiniteDuration arithmetic and ordering, on the runtime's millisecond representation
            case Select(recv, op @ ("*" | "$times" | "/" | "$div")) if isDurationTerm(recv) && args.length == 1 =>
              val o = if (op == "*" || op == "$times") "*" else "/"
              s"RtDuration(RtDurationMs(${emitTerm(recv)}) $o ${emitArgVal(args.head)})"
            case Select(recv, op @ (">" | "<" | ">=" | "<=" | "$greater" | "$less" | "$greater$eq" | "$less$eq"))
                if isDurationTerm(recv) && args.length == 1 =>
              val o = op match {
                case ">" | "$greater"      => ">"
                case "<" | "$less"         => "<"
                case ">=" | "$greater$eq"  => ">="
                case _                     => "<="
              }
              s"(RtDurationMs(${emitTerm(recv)}) $o RtDurationMs(${emitArgVal(args.head)}))"
            // arithmetic on a Char is on its code, as the JVM widens it to an Int: a Char is a one-character string
            // here, which BrightScript will not subtract (`iso.charAt(0) - 'A'`, a flag from a country code)
            case Select(recv, op @ ("+" | "-" | "*" | "$plus" | "$minus" | "$times")) if args.length == 1 &&
                (fqnOf(recv.tpe) == "scala.Char" || fqnOf(args.head.tpe) == "scala.Char") &&
                isPrimLike(recv) && isPrimLike(args.head) && !isStringLike(recv.tpe) && !isStringLike(args.head.tpe) =>
              val o = op match {
                case "+" | "$plus" => "+"
                case "-" | "$minus" => "-"
                case _ => "*"
              }
              s"(_chCode(${emitTerm(recv)}) $o _chCode(${emitTerm(args.head)}))"
            // Dividing by nothing: the JVM throws on whole numbers and answers Infinity or NaN on fractions, while a
            // Roku stops the thread in its debugger and takes the channel with it — the guard written to prevent it
            // is no help where the arm is worked out anyway. A divisor that is not plainly not zero goes through the
            // runtime, which answers as the JVM does, so the fault is a Scala one and can be caught and reported.
            case Select(recv, op @ ("/" | "$div" | "%" | "$percent"))
                if args.length == 1 && !nonZeroLiteral(args.head) && isPrimLike(recv) && isPrimLike(args.head) =>
              val whole = isIntegral(recv.tpe) && isIntegral(args.head.tpe)
              val fn = if (op == "%" || op == "$percent") "_rem" else if (whole) "_quot" else "_div"
              s"$fn(${emitTerm(recv)}, ${emitTerm(args.head)})"
            // an integral quotient is whole, as the JVM keeps it: BrightScript's `/` gives a fraction, `\\` does not
            case Select(recv, "/" | "$div") if args.length == 1 && isIntegral(recv.tpe) && isIntegral(args.head.tpe) =>
              s"(${emitTerm(recv)} \\ ${emitTerm(args.head)})"
            // string concatenation coerces its right side, as the JVM does: BrightScript's `+` refuses a string
            // and a number, so `"n=" + count` (or a `.toString` joined to a bare Int) faults without this
            case Select(recv, "+") if isStringLike(recv.tpe) && args.length == 1 =>
              s"(${emitTerm(recv)} + _str(${emitTerm(args.head)}))"
            case Select(recv, op) if binOps.contains(op) && args.length == 1 =>
              s"(${emitTerm(recv)} ${binOps(op)} ${emitTerm(args.head)})"
            // ── Option ops → invalid-checks (Option lowers to a nullable). Must
            // precede the collection ops, since some names (map/filter/…) overlap. ──
            case Select(recv, "map" | "flatMap") if isOptionType(recv) =>
              s"_optMap(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // an Option of an Option is one Option here already (Some(None) is None), so flattening it is nothing to do
            case Select(recv, "flatten") if isOptionType(recv) => emitTerm(recv)
            // a collection of collections, or of Options (an empty one is nothing here), made one
            case Select(recv, "flatten") if isCollType(recv) => s"_flatten(${emitTerm(recv)})"
            case Select(recv, "foreach") if isOptionType(recv) =>
              s"_optForeach(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "getOrElse") if isOptionType(recv) =>
              s"_getOrElse(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "filter" | "withFilter") if isOptionType(recv) =>
              s"_optFilter(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "exists") if isOptionType(recv) =>
              s"_optExists(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "filterNot") if isOptionType(recv) =>
              s"_optFilterNot(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "forall") if isOptionType(recv) =>
              s"_optForall(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "contains") if isOptionType(recv) =>
              s"(${emitTerm(recv)} = ${emitArgVal(args.head)})"
            // ── Try / Either ops ──
            case Select(recv, "fold") if (isTryType(recv) || isEitherType(recv)) && args.length == 2 =>
              s"_tryFold(${emitTerm(recv)}, ${emitArgVal(args(0))}, ${emitArgVal(args(1))})"
            case Select(recv, "map") if isTryType(recv) || isEitherType(recv) =>
              s"_tryMap(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "flatMap") if isTryType(recv) || isEitherType(recv) =>
              s"_tryFlatMap(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "getOrElse") if isTryType(recv) || isEitherType(recv) =>
              s"_tryGetOrElse(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // ── collection ops → runtime helpers (roArray has none of these) ──
            case Select(recv, "map") =>
              s"_arrMap(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "flatMap") =>
              s"_arrFlatMap(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // a String's characters kept or dropped: a String again, not an array of its characters
            case Select(recv, "filter") if isStringLike(recv.tpe) =>
              s"_strFilter(${emitTerm(recv)}, ${emitArgVal(args.head)}, true)"
            case Select(recv, "filterNot") if isStringLike(recv.tpe) =>
              s"_strFilter(${emitTerm(recv)}, ${emitArgVal(args.head)}, false)"
            case Select(recv, "filter" | "withFilter") =>
              s"_arrFilter(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "filterNot") =>
              s"_arrFilterNot(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "foreach") =>
              s"_arrForeach(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "exists") =>
              s"_arrExists(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "forall") =>
              s"_arrForall(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "find") =>
              s"_arrFind(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // an Array written in place (`xs(i) = v`): a BrightScript array is a reference, as a JVM one is
            case Select(recv, "update") if fqnOf(recv.tpe) == "scala.Array" && args.size == 2 =>
              s"_arrSet(${emitTerm(recv)}, ${emitArgVal(args(0))}, ${emitArgVal(args(1))})"
            // an Array from a collection (the argument is the ClassTag): a BrightScript array either way
            case Select(recv, "toArray") if isCollType(recv) && args.size == 1 => emitTerm(recv)
            case Select(recv, "count") if isCollType(recv) && args.size == 1 =>
              s"_arrCount(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "indexWhere") if isCollType(recv) && args.size == 1 =>
              s"_arrIndexWhere(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "indexOf") if isCollType(recv) && args.size == 1 =>
              s"_arrIndexOf(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // pairs to a Map (the argument is the `<:<` evidence); a Map is one already, and walked as an array it
            // gave its keys alone
            case Select(recv, "toMap") if isMapTpe(recv.tpe) && args.size == 1 => emitTerm(recv)
            case Select(recv, "toMap") if isCollType(recv) && args.size == 1 =>
              s"_mapFromPairs(${emitTerm(recv)})"
            case Select(recv, "contains") if fqnOf(recv.tpe) == "java.lang.String" && args.size == 1 =>
              s"_strContains(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "contains") =>
              s"_contains(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "mkString") if args.length == 1 =>
              s"_mkString(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "take") =>
              s"_take(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "drop") =>
              s"_drop(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "slice") if isCollType(recv) && args.size == 2 =>
              s"_slice(${emitTerm(recv)}, ${emitTerm(args(0))}, ${emitTerm(args(1))})"
            // xs.patch(from, other, replaced): `replaced` of xs from `from` given up for other's elements
            case Select(recv, "patch") if isCollType(recv) && args.size == 3 =>
              s"_patch(${emitTerm(recv)}, ${emitTerm(args(0))}, ${emitTerm(args(1))}, ${emitTerm(args(2))})"
            case Select(recv, "dropRight") if isCollType(recv) && args.size == 1 =>
              s"_dropRight(${emitTerm(recv)}, ${emitTerm(args.head)})"
            // a String's takeWhile/dropWhile answer with a String; a collection's with a collection
            case Select(recv, "takeWhile") if isStringLike(recv.tpe) && args.size == 1 =>
              s"_strTakeWhile(${emitTerm(recv)}, ${emitArgVal(args.head)}, true)"
            case Select(recv, "dropWhile") if isStringLike(recv.tpe) && args.size == 1 =>
              s"_strTakeWhile(${emitTerm(recv)}, ${emitArgVal(args.head)}, false)"
            // `Iterator.iterate(z)(f).takeWhile(p)`: the values from z on while p holds, gathered at once, since an
            // iterator here is the array it would have given
            case Select(recv, "takeWhile") if iterateCall(recv).isDefined && args.size == 1 =>
              val (z, f) = iterateCall(recv).get
              s"_iterateWhile(${emitArgVal(z)}, ${emitArgVal(f)}, ${emitArgVal(args.head)})"
            case Select(recv, "takeWhile") if isCollType(recv) && args.size == 1 =>
              s"_takeWhile(${emitTerm(recv)}, ${emitArgVal(args.head)}, true)"
            case Select(recv, "dropWhile") if isCollType(recv) && args.size == 1 =>
              s"_takeWhile(${emitTerm(recv)}, ${emitArgVal(args.head)}, false)"
            case Select(recv, "lastIndexWhere") if isCollType(recv) && args.size == 1 =>
              s"_arrLastIndexWhere(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // an empty collection has no least or greatest, which is None — `invalid` here
            case Select(recv, "minOption") if isCollType(recv) => s"_minOption(${emitTerm(recv)}, true)"
            case Select(recv, "maxOption") if isCollType(recv) => s"_minOption(${emitTerm(recv)}, false)"
            case Select(recv, "capitalize") if isStringLike(recv.tpe) => s"_strCapitalize(${emitTerm(recv)})"
            case Select(recv, "sortBy") =>
              s"_sortBy(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // `xs.foldLeft(z)(op)`, uncurried to its two arguments
            case Select(recv, "foldLeft") if isCollType(recv) && args.size == 2 =>
              s"_arrFoldLeft(${emitTerm(recv)}, ${emitArgVal(args(0))}, ${emitArgVal(args(1))})"
            case Select(recv, "zip") if isCollType(recv) && args.size == 1 =>
              s"_zip(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            // `.sum` / `.min` / `.max` carry their Numeric / Ordering as the one argument
            case Select(recv, "sum") if isCollType(recv) => s"_sum(${emitTerm(recv)})"
            case Select(recv, "min") if isCollType(recv) => s"_minList(${emitTerm(recv)})"
            case Select(recv, "max") if isCollType(recv) => s"_maxList(${emitTerm(recv)})"
            case Select(recv, "getOrElse") =>
              s"_getOrElse(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "until") =>
              s"_until(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "to") if args.length == 1 =>
              s"_toIncl(${emitTerm(recv)}, ${emitTerm(args.head)})"
            // Either's constructors: BrightScript's own Left and Right are string functions, so the runtime's are named apart
            case Select(_, "apply") if ownerIs(fn, "scala.util.Left") && args.size == 1 => s"RtLeft(${emitArgVal(args.head)})"
            case Select(_, "apply") if ownerIs(fn, "scala.util.Right") && args.size == 1 => s"RtRight(${emitArgVal(args.head)})"
            case Select(recv, "apply") if isFacadeCall(fn) =>
              s"${emitTerm(recv)}(${emitFacadeArgs(args)})"
            case _ if isFacadeCall(fn) => s"${emitTerm(fn)}(${emitFacadeArgs(args)})"
            // A companion `X.apply(args)` that ISN'T a case ctor, collection index,
            // or recognised facade (e.g. external library lib types not emitted
            // through) → in Scala `X.apply(a) == X(a)`, so drop `.apply` and call
            // the name; the runtime provides a matching stub function. But an
            // INSTANCE `.apply()` on a user object (e.g. `v()` on a reactive Var)
            // is a real method — let it fall through to cross-object dispatch
            // (Owner_apply(recv, …)) rather than emitting a bad `recv()` call.
            case Select(recv, "apply")
                if !(fn.symbol.exists && fn.symbol.isDefDef && fn.symbol.flags.is(Flags.Method)
                     && isUserClassSym(fn.symbol.owner) && !isFacade(fn.symbol)) =>
              s"${emitTerm(recv)}(${emitArgs(args, fn.symbol)})"
            // self method call (private def) → Class_method(self, args)
            case Select(recv, name) if inCurrentClass(fn.symbol.owner) && isThis(recv) =>
              val a = emitArgs(args, fn.symbol)
              s"${typeName(currentClass)}_${memberName(fn.symbol, name)}(self${if (a.isEmpty) "" else ", " + a})"
            // A self method referenced as a BARE Ident (`load()` inside another
            // method) — Scala drops the `this.`; emit Class_method(self, …), not
            // `self.load()` (a field-call passes no `self` → "wrong # of params").
            case _ if fn.symbol.exists && fn.symbol.isDefDef && fn.symbol.flags.is(Flags.Method)
                && inCurrentClass(fn.symbol.owner) && !isFacade(fn.symbol) =>
              val a = emitArgs(args, fn.symbol)
              s"${typeName(currentClass)}_${memberName(fn.symbol, fn.symbol.name)}(self${if (a.isEmpty) "" else ", " + a})"
            // Cross-object user-method call: `recv.m(args)` where recv is another
            // user object. RokuEmitter methods take `self` explicitly, so emit
            // Owner_method(recv, args), NOT recv.m(args) — a field-call passes no
            // self, yielding "wrong # of params" at runtime.
            case Select(recv, name)
                if fn.symbol.exists && fn.symbol.isDefDef && fn.symbol.flags.is(Flags.Method)
                   && isUserClassSym(fn.symbol.owner) && !isFacade(fn.symbol) =>
              val a = emitArgs(args, fn.symbol)
              s"${typeName(fn.symbol.owner)}_${memberName(fn.symbol, name)}(${emitTerm(recv)}${if (a.isEmpty) "" else ", " + a})"
            case Select(recv, name) if isLibraryGap(recv, fn.symbol) => libraryGap(recv, name)
            case _ =>
              val fe = emitTerm(fn)
              // `throw e` is a keyword in BrightScript, not a call → runtime helper.
              if (fe == "throw") s"RtThrow(${emitArgs(args, fn.symbol)})"
              else s"$fe(${emitArgs(args, fn.symbol)})"
          }
        case _ => emitTerm(t)
      }
    }

    def isThis(t: Term): Boolean = t match {
      case th: This => thisRef(th.symbol) == "self"
      case _        => false
    }

    def emitTerm(t0: Term): String = t0 match {
      // fabric's `RW.gen[T]`: the emitter derives the codec itself (see emitRwGen); the macro's output is not emitted
      case Inlined(Some(call), _, _) if rwGenType(call).isDefined => emitRwGen(rwGenType(call).get, 0)
      case Inlined(call, bs, e) if call.exists(c => c.show.contains("gen")) =>
        System.err.println("DEBUG inlined call=" + call.map(_.show.take(160)) + " bs=" + bs.size + " expansion=" + e.show.take(200))
        emitTermU(t0)
      // An inlined call's bindings (`assert`'s message proxy …) are real vals.
      case Inlined(_, bs, e) if bs.nonEmpty && pendingStack.nonEmpty =>
        bs.foreach { bd => val st = emitStat(bd); if (st.trim.nonEmpty) pendingStack.top += st }
        emitTerm(e)
      case _ => emitTermU(t0)
    }

    def emitTermU(t0: Term): String =
      unwrap(t0) match {
        case Apply(Select(New(tpt), _), args) if hasNative(tpt.tpe.typeSymbol) =>
          s"${tpt.tpe.typeSymbol.name}(${emitFacadeCtorArgs(tpt.tpe.typeSymbol.primaryConstructor, args)})"
        case Apply(TypeApply(Select(New(tpt), _), _), args) if hasNative(tpt.tpe.typeSymbol) =>
          s"${tpt.tpe.typeSymbol.name}(${emitFacadeCtorArgs(tpt.tpe.typeSymbol.primaryConstructor, args)})"
        // `new Array[T](n)`: n slots holding what the JVM starts them at, a zero for a number, false for a flag and null
        // (invalid) for anything else, so its length is n from the start
        case Apply(TypeApply(Select(New(tpt), _), _), List(n)) if fqnOf(tpt.tpe) == "scala.Array" =>
          val zero = tpt.tpe.dealias.typeArgs.headOption.map(fqnOf).getOrElse("") match {
            case "scala.Boolean" => "false"
            case "scala.Double" | "scala.Float" => "0.0"
            case "scala.Int" | "scala.Long" | "scala.Short" | "scala.Byte" => "0"
            case _ => "invalid"
          }
          s"_arrNew(${emitTerm(n)}, $zero)"
        case Apply(ctor @ Select(New(tpt), _), args) if isUserClassSym(tpt.tpe.typeSymbol) =>
          s"${typeName(tpt.tpe.typeSymbol)}_new(${emitArgs(args, ctor.symbol)})"
        case Apply(TypeApply(ctor @ Select(New(tpt), _), _), args) if isUserClassSym(tpt.tpe.typeSymbol) =>
          s"${typeName(tpt.tpe.typeSymbol)}_new(${emitArgs(args, ctor.symbol)})"
        // super.m(...) — a user base resolves statically to the implementation
        // TASTy names; into a @native base (State.initState etc.) it is a no-op.
        // Its arguments are emitted against the super method's parameters, so a by-name value forwarded to a
        // by-name parameter (`super.:=(value)`) stays a thunk rather than being evaluated once.
        case Apply(f0, sargs) if superSel(f0).isDefined =>
          val ss = superSel(f0).get.symbol
          if (ss.exists && userTypeDefs.contains(ss.owner))
            s"${modelImplName(ss)}(self${emitArgList(sargs, ss).map(", " + _).mkString})"
          else "invalid"
        case sel @ Select(spr, _) if isSuper(spr) =>
          val ss = sel.symbol
          if (ss.exists && userTypeDefs.contains(ss.owner) && (ss.isDefDef || ss.flags.is(Flags.Lazy)))
            s"${modelImplName(ss)}(self)"
          else "invalid"
        case Literal(c)  => emitConst(c)
        case th: This    => thisRef(th.symbol)
        // a constructor with several argument clauses: one `_new` with all of them
        case Apply(inner @ Apply(_, _), more) if newChain(inner).isDefined =>
          val (cls, ctor, first) = newChain(inner).get
          s"${typeName(cls)}_new(${emitArgs(first ++ more, ctor)})"
        // `task.start` (a Task's fiber; `.start()` syncs on it, so it is fire-and-forget)
        case Select(q, "start") if isTaskTerm(q) => s"RapidTask_start(${emitTerm(q)})"
        // `"…".r` (StringOps.r): the platform's regex
        case Select(Apply(aug, List(s)), "r") if aug.symbol.exists && aug.symbol.name == "augmentString" => s"_regex(${emitTerm(s)})"
        // a fabric Json accessor without parentheses (`json("type").asString`): the runtime's (see "library shims")
        case sel @ Select(q, m) if isFabricJson(sel) => s"_json_$m(${emitTerm(q)})"
        // fabric's JSON null: BrightScript's own (`invalid`, which FormatJson writes as null)
        case id: Ident if isFabricNull(id.symbol)   => "invalid"
        case sel: Select if isFabricNull(sel.symbol) => "invalid"
        // a fabric RW instance (`stringRW`, `valueRW`): the runtime's codec of that name
        case id @ Ident(n) if isFabricRW(id.symbol)       => s"_rw(${brStr(n)})"
        case sel @ Select(_, n) if isFabricRW(sel.symbol) => s"_rw(${brStr(n)})"
        // a Scala 3 enum's simple case (`ProtocolState.Active`): a runtime singleton, named and ordered
        case sel @ Select(_, n) if isEnumCase(sel.symbol) => emitEnumCase(sel.symbol, n)
        case id @ Ident(n) if isEnumCase(id.symbol)       => emitEnumCase(id.symbol, n)
        case id @ Ident(n) => emitRef(id.symbol, n)
        case Select(q, "toString")             => s"_str(${emitTerm(q)})"
        case Apply(Select(q, "toString"), Nil) => s"_str(${emitTerm(q)})"
        // `Duration.Zero`: the runtime's FiniteDuration of no milliseconds (the module is nothing a Roku has)
        case z @ Select(_, "Zero") if ownerFqn(z.symbol) == "scala.concurrent.duration.Duration" =>
          s"RtDuration(${emitConst(IntConstant(0))})"
        case sel @ Select(_, n @ ("MinValue" | "MaxValue")) if ownerFqn(sel.symbol) == "scala.Double" =>
          if (n == "MaxValue") "RtDoubleMax()" else "RtDoubleMin()"
        case sel @ Select(_, n @ ("MinValue" | "MaxValue")) if ownerFqn(sel.symbol) == "scala.Int" =>
          if (n == "MaxValue") "2147483647" else "(-2147483647 - 1)"
        case sel @ Select(_, n @ ("Pi" | "E")) if ownerFqn(sel.symbol) == "scala.math.package" =>
          if (n == "Pi") "3.141592653589793" else "2.718281828459045"
        case Select(Apply(conv, List(n)), unit) if durationFactor(conv, unit).isDefined =>
          s"RtDuration((${emitTerm(n)}) * ${durationFactor(conv, unit).get})"
        case Select(q, "toMillis") if fqnOf(q.tpe).startsWith("scala.concurrent.duration") =>
          s"RtDurationMs(${emitTerm(q)})"
        case Select(q, "toSeconds") if fqnOf(q.tpe).startsWith("scala.concurrent.duration") =>
          s"Int(RtDurationMs(${emitTerm(q)}) / 1000)"
        case Select(Apply(f, List(str)), m @ ("nonEmpty" | "isEmpty"))
            if stripTypeApply(f).symbol.exists && stripTypeApply(f).symbol.name == "augmentString" =>
          if (m == "nonEmpty") s"(Len(${emitTerm(str)}) > 0)" else s"(Len(${emitTerm(str)}) = 0)"
        case Select(q, "toList" | "toSeq" | "toVector") if isMapTpe(q.tpe) => s"_mapToList(${emitTerm(q)})"
        case Select(q, "length") if fqnOf(q.tpe) == "java.lang.String" => s"Len(${emitTerm(q)})"
        case ta @ TypeApply(Select(_, "empty"), _) if isMapTpe(ta.tpe) => "_mapEmpty()"
        case ta @ TypeApply(Select(_, "empty"), _) if isSetTpe(ta.tpe) || isListTpe(ta.tpe) => "[]"
        case sel @ Select(_, "empty") if isMapTpe(sel.tpe) => "_mapEmpty()"
        case sel @ Select(_, "empty") if isSetTpe(sel.tpe) || isListTpe(sel.tpe) => "[]"
        case Select(q, "keys" | "keySet") if isMapTpe(q.tpe) => s"_mapKeys(${emitTerm(q)})"
        case Select(q, "values") if isMapTpe(q.tpe) => s"_mapValues(${emitTerm(q)})"
        case Select(q, "size") if isMapTpe(q.tpe) => s"_mapSize(${emitTerm(q)})"
        case Select(q, "size" | "length") if isCollType(q) => s"${emitTerm(q)}.count()"
        // Paren-less Option getters (Option lowers to a plain nullable).
        case Select(q, "isDefined" | "nonEmpty") if isOptionType(q) => s"(${emitTerm(q)} <> invalid)"
        case Select(q, "isEmpty") if isOptionType(q)               => s"(${emitTerm(q)} = invalid)"
        case Select(q, "get" | "orNull") if isOptionType(q)        => emitTerm(q)
        case Select(q, "toList" | "toSeq" | "toVector") if isOptionType(q) => s"_optToList(${emitTerm(q)})"
        case Select(q, "toOption") if isTryType(q) || isEitherType(q) => s"_tryToOption(${emitTerm(q)})"
        case Select(q, "toList" | "toSeq" | "toArray" | "toVector" | "toIndexedSeq") if isCollType(q) => emitTerm(q)
        // Paren-less collection getters → runtime helpers.
        case Select(q, "isEmpty") if isCollType(q)   => s"(${emitTerm(q)}.count() = 0)"
        case Select(q, "nonEmpty") if isCollType(q)  => s"(${emitTerm(q)}.count() > 0)"
        case Select(q, "head") if isCollType(q)      => s"${emitTerm(q)}[0]"
        case Select(q, "last") if isCollType(q)      => s"_last(${emitTerm(q)})"
        case Select(q, "headOption") if isCollType(q) => s"_headOption(${emitTerm(q)})"
        case Select(q, "lastOption") if isCollType(q) => s"_lastOption(${emitTerm(q)})"
        // RichDouble/RichInt nullary rounding: doubleWrapper(x).round / .floor / .ceil (min/max, which take an
        // argument, are Applys handled where the calls are); and RichChar predicates, on a Char that is a one-char
        // string here. Placed after the collection getters so it is outside the Swift-derivation block above.
        case Select(Apply(wrap, List(x)), m @ ("round" | "floor" | "ceil" | "abs")) if isRichNumWrapper(wrap) => emitMath(m, List(x))
        case Select(Apply(wrap, List(x)), "toHexString") if isRichNumWrapper(wrap) => s"_toHex(${emitTerm(x)})"
        case Select(Apply(wrap, List(x)), m @ ("isDigit" | "isLetter" | "isLetterOrDigit" | "isWhitespace" | "isSpaceChar" | "isUpper" | "isLower")) if isRichNumWrapper(wrap) => s"_ch_$m(${emitTerm(x)})"
        case Select(q, "indices") if isCollType(q) => s"_until(0, ${emitTerm(q)}.count())"
        case Select(q, "reverse") if isCollType(q)   => s"_reverse(${emitTerm(q)})"
        case Select(q, "init") if isSeqLike(q) => s"_dropRight(${emitTerm(q)}, 1)"
        case Select(q, "capitalize") if isStringLike(q.tpe) => s"_strCapitalize(${emitTerm(q)})"
        // `xs.lift` as a function of its own, where it is not applied at once (see `_lift`)
        case Select(q, "lift") if isSeqLike(q) => s"{ fn: _liftCall, env: { xs: ${emitTerm(q)} } }"
        case Select(q, "distinct") if isCollType(q)  => s"_distinct(${emitTerm(q)})"
        // a Set is an array of distinct values here, so a collection made one is its distinct values (a Map's, its pairs)
        case Select(q, "toSet") if isMapTpe(q.tpe) => s"_mapToList(${emitTerm(q)})"
        case TypeApply(Select(q, "toSet"), _) if isMapTpe(q.tpe) => s"_mapToList(${emitTerm(q)})"
        case Select(q, "toSet") if isCollType(q) => s"_distinct(${emitTerm(q)})"
        case TypeApply(Select(q, "toSet"), _) if isCollType(q) => s"_distinct(${emitTerm(q)})"
        case Select(q, "sum") if isCollType(q)       => s"_sum(${emitTerm(q)})"
        // a Char made a number is its code: a Char is a one-character string here, and a hash over a name's letters
        // (31 * h + c.toInt) added the letter itself
        case Select(q, "toInt" | "toLong") if fqnOf(q.tpe) == "scala.Char" => s"_chCode(${emitTerm(q)})"
        case Select(q, "toDouble" | "toFloat") if fqnOf(q.tpe) == "scala.Char" => s"Cdbl(_chCode(${emitTerm(q)}))"
        case Select(q, "min") if isCollType(q)       => s"_minList(${emitTerm(q)})"
        case Select(q, "max") if isCollType(q)       => s"_maxList(${emitTerm(q)})"
        case Select(q, "zipWithIndex") if isCollType(q) => s"_zipWithIndex(${emitTerm(q)})"
        case Select(q, "mkString") if isCollType(q) => s"_mkString(${emitTerm(q)}, ${brStr("")})"
        // an integral value made floating: BrightScript keeps a LongInteger quotient whole. A Long is promoted by
        // arithmetic, since Cdbl goes through a 32-bit Integer and loses the top of one (a time in milliseconds)
        case Select(q, "toDouble" | "toFloat") if fqnOf(q.tpe) == "scala.Long" => s"(${emitTerm(q)} + 0#)"
        case Select(q, "toDouble" | "toFloat") if Set("scala.Int", "scala.Short", "scala.Byte", "scala.Char").contains(fqnOf(q.tpe)) =>
          s"Cdbl(${emitTerm(q)})"
        // a String read as a number: the whole of it, or (the Option forms) nothing when it is not one
        case Select(q, "toInt" | "toLong") if isStringLike(q.tpe) => s"_strToInt(${emitTerm(q)})"
        case Select(q, "toDouble" | "toFloat") if isStringLike(q.tpe) => s"_strToDouble(${emitTerm(q)})"
        case Select(q, "toIntOption" | "toLongOption") if isStringLike(q.tpe) => s"_strToIntOpt(${emitTerm(q)})"
        case Select(q, "toDoubleOption" | "toFloatOption") if isStringLike(q.tpe) => s"_strToDoubleOpt(${emitTerm(q)})"
        // a fraction made whole is truncated toward zero, as the JVM does (BrightScript's Int() floors)
        case Select(q, "toInt" | "toLong") if !isIntegral(q.tpe) && Set("scala.Double", "scala.Float").contains(fqnOf(q.tpe)) =>
          s"Fix(${emitTerm(q)})"
        case Select(q, "toDouble" | "toInt" | "toLong" | "toFloat") => emitTerm(q)
        case Select(q, "tail") if isCollType(q) => s"_drop(${emitTerm(q)}, 1)"
        case TypeApply(Select(q, "asInstanceOf"), _) => emitTerm(q)
        case sel @ Select(_, _) if modelModuleOf(sel.symbol).isDefined => moduleAccessor(modelModuleOf(sel.symbol).get)
        case sel @ Select(q, n) if inParentArgs && isThis(q) && sel.symbol.flags.is(Flags.ParamAccessor) && sel.symbol.owner == currentClass =>
          if (isByNameParam(sel.symbol)) s"RtForce(${brLocal(n)})" else brLocal(n)
        case sel @ Select(q, _) if isModelMember(sel.symbol) && !isFacade(sel.symbol) && !isSuper(q) =>
          emitModelCall(Some(q), sel.symbol, Nil)
        case Select(q, "unary_$bang" | "unary_!") => s"(not ${emitTerm(q)})"
        case Select(q, "unary_$minus" | "unary_-") => s"(-${emitTerm(q)})"
        // NOTE: do NOT special-case nullary Applys here as `emitTerm(fn)()` —
        // that emits `self.load()` for a self method (a field-call passing no
        // `self` → "wrong # of params"). Route ALL Applys through emitApply,
        // which turns a self call into `Class_method(self)`.
        case a: Apply                       => emitApply(a)
        case TypeApply(fn, _)               => emitTerm(fn)
        case sel @ Select(q, name) =>
          val s  = sel.symbol
          val qs = q.symbol
          // A static member of a @native object. BrightScript has no
          // namespaces, so it is read from the global assoc array as
          // `ga.<Object>.<member>`, which the platform runtime installs.
          if (qs.exists && qs.flags.is(Flags.Module)
             && (hasNative(qs) || hasNative(qs.companionClass)))
            s"ga.${qs.name.stripSuffix("$")}.${brSafe(name)}"
          else {
            // paren-less USER getter → Class_getter(self); a member of a @native
            // base class is a plain field → self.field.
            val parenlessSelf =
              s.exists && s.isDefDef && s.flags.is(Flags.Method) && !s.flags.is(Flags.FieldAccessor)
                && s.paramSymss.forall(c => c.nonEmpty && c.forall(_.isType)) && isUserClassSym(s.owner)
            // `inCurrentClass` is ownership-only, so it is ALSO true for another
            // instance of the same class (`child.node` inside a Component
            // method). Only a real `this.` receiver resolves against `self`.
            if (inCurrentClass(s.owner) && isThis(q))
              if (parenlessSelf) s"${typeName(currentClass)}_${memberName(s, name)}(self)" else s"self.${brSafe(name)}"
            else moduleSyms.get(s.owner) match {
              case Some(prefix) => s"${prefix}_${memberName(s, name)}()"
              // A paren-less getter on ANOTHER user instance is a method, not a
              // field — `self.getter` only holds the function ref, so call it.
              case None if parenlessSelf => s"${typeName(s.owner)}_${memberName(s, name)}(${emitTerm(q)})"
              // an outer instance's by-name constructor parameter (read from an anonymous class inside it) is forced
              case None if isByNameParam(s) && s.flags.is(Flags.ParamAccessor) => s"RtForce(${emitTerm(q)}.${brSafe(name)})"
              case None if isLibraryGap(q, s) => libraryGap(q, name)
              case None                  => s"${emitTerm(q)}.${brSafe(name)}"
            }
          }
        // `_iif(c, a, b)` only where both arms are pure; any other control flow
        // in value position is hoisted into statements (A-normalisation).
        case If(c, a, b) if iifSafe(a, b) => s"_iif(${emitTerm(c)}, ${emitTerm(a)}, ${emitTerm(b)})"
        case i: If    => hoist(i)
        case m: Match => hoist(m)
        case tr: Try  => hoist(tr)
        case w: While => hoist(w)
        case Block(List(cd: ClassDef), e) if isLocalClassInstantiation(cd, e) => emitAnonInstance(cd, e)
        case Block(List(dd: DefDef), _: Closure) => emitClosure(dd)
        case Block(Nil, e) => emitTerm(e)
        case Block(stats, e) if allValDefs(stats) && isTempVals(stats) && dropsDefaults(e) =>
          emitTempValBlock(stats.collect { case v: ValDef => v }, e)
        case bl: Block => hoistBlock(bl)
        case asg: Assign =>
          if (pendingStack.nonEmpty) {
            val st = emitStat(asg)
            if (st.trim.nonEmpty) pendingStack.top += st
            "invalid"
          }
          else todo(asg)
        case other => todo(other)
      }

    // ── statements / bodies ─────────────────────────────────────────────────
    // A statement-position term whose value is a no-op (a `super.x()` call, an
    // elided Unit) emits the bare literal `invalid`, which isn't a valid
    // BrightScript statement — drop those.
    def stmtTerm(e: String): String = {
      val t = e.trim
      // A bare literal / no-op isn't a valid BrightScript statement — only
      // calls and assignments are. Drop `invalid`, "", string/number literals.
      if (t.isEmpty || t == "invalid") ""
      else if (t.matches("\"(?:[^\"]|\"\")*\"")) ""
      else if (t.matches("-?[0-9][0-9.]*")) ""
      // a discarded VALUE (`(a - b)`, `x.field`) is not a BrightScript statement; keep its evaluation
      else if (t.startsWith("(") || !t.endsWith(")")) "  __discard = " + e
      else "  " + e
    }

    // ── pattern matching ─────────────────────────────────────────────────────
    // BrightScript has no expression-level match/IIFE, so `x match { … }` is
    // emitted as an if/elseif STATEMENT chain, with each branch's value routed
    // through a `sink` (return / assignment). Case classes carry a `_cls` tag.
    def patternClass(p: Tree): Symbol = p match {
      case Unapply(fun, _, _) =>
        val s = fun.symbol
        if (s.exists && s.owner.exists && s.owner.companionClass.exists) s.owner.companionClass
        else p match { case tp: Term => tp.tpe.typeSymbol; case _ => Symbol.noSymbol }
      case TypedOrTest(_, tpt) => tpt.tpe.typeSymbol
      case Typed(_, tpt)       => tpt.tpe.typeSymbol
      case _                   => Symbol.noSymbol
    }

    /** `case x: SomeClass` where SomeClass is a class of this program's own and not a case class: it was read as
      * "true" once, so `case i: ImageView` matched every component there was. Tested against `_isa` now. */
    def classTest(ts: Symbol, subj: String): String =
      if (!ts.exists || universal.contains(ts.name) || ts.flags.is(Flags.Module)) "true"
      else if (ts.fullName.startsWith("scala.") || ts.fullName.startsWith("java.")) "true"
      else if (!ts.isClassDef) "true"
      else s"RtIsA($subj, ${brStr("|" + ts.name + "|")})"

    // (testExpr, bindingStatements) for matching `subj` against pattern `p`.
    def patternTest(p: Tree, subj: String): (String, List[String]) = p match {
      case Wildcard()        => ("true", Nil)
      case Ident("_")        => ("true", Nil)
      case Bind(name, inner) =>
        val (t, b) = patternTest(inner, subj)
        (t, s"${brLocal(name)} = $subj" :: b)
      // `"a" | "b"` inside a pattern: any of them
      case Alternatives(ps) =>
        val ts = ps.map(x => patternTest(x, subj))
        ("(" + ts.map(_._1).mkString(" or ") + ")", ts.flatMap(_._2))
      case Literal(c)        => (s"($subj = ${emitConst(c)})", Nil)
      // `case p: PairStartResponse =>` can arrive as a plain Typed pattern: a case class is tested by its tag all the same
      case Typed(inner, tpt) =>
        val ts = tpt.tpe.typeSymbol
        val tag =
          if (ts.exists && ts.flags.is(Flags.Case) && !ts.fullName.matches("scala\\.Tuple\\d+"))
            s"($subj <> invalid and $subj._cls = ${brStr(ts.name)})"
          else classTest(ts, subj)
        val (it, ib) = patternTest(inner, subj)
        (if (it == "true") tag else if (tag == "true") it else s"$tag and $it", ib)
      case TypedOrTest(inner, tpt) =>
        val ts = tpt.tpe.typeSymbol
        val innerTests = inner match {
          case u: Unapply => { val c = patternClass(u); c.exists && c == ts }
          case _          => false
        }
        val tag =
          if (!ts.exists || innerTests) "true"
          else if (ts.fullName == "scala.Some" || ts.fullName == "scala.Option") s"($subj <> invalid)"
          else if (ts.flags.is(Flags.Case) && !ts.fullName.matches("scala\\.Tuple\\d+"))
            s"($subj <> invalid and $subj._cls = ${brStr(ts.name)})"
          else classTest(ts, subj)
        val (it, ib) = patternTest(inner, subj)
        val test = if (it == "true") tag else if (tag == "true") it else s"$tag and $it"
        (test, ib)
      // `List(a, b)`, `Seq(x, rest*)` (a collection's unapplySeq) on what is an array here: as many elements as there
      // are patterns (at least as many, with a trailing `rest*`), each matched in its place. Read as a case class it
      // was tested for a tag no array has, so it never matched, and a pattern that should have did not.
      case Unapply(fun, _, subs) if fun.symbol.exists && fun.symbol.name == "unapplySeq" &&
          fun.symbol.owner.fullName.startsWith("scala.collection") =>
        def starred(p: Tree): Boolean = p match {
          case Bind(_, inner) => starred(inner)
          case Typed(_, tpt) => tpt.tpe.typeSymbol.name == "<repeated>" || tpt.tpe.show.endsWith("*")
          case _ => false
        }
        val star = subs.lastOption.exists(starred)
        val fixed = if (star) subs.init else subs
        val size = if (star) s"$subj.count() >= ${fixed.size}" else s"$subj.count() = ${fixed.size}"
        val elems = fixed.zipWithIndex.map { case (sp, i) => patternTest(sp, s"$subj[$i]") }
        val rest = subs.lastOption.filter(_ => star).toList.collect { case Bind(name, _) =>
          s"${brLocal(name)} = _drop($subj, ${fixed.size})" }
        ((s"(type($subj) = \"roArray\")" :: s"($size)" :: elems.map(_._1)).filter(_ != "true").mkString(" and "),
          elems.flatMap(_._2) ++ rest)
      // Option lowers to a nullable: `Some(x)` is any non-invalid value, bound as-is.
      case u @ Unapply(fun, _, subs)
          if subs.size == 1 && { val c = patternClass(u); c.exists && c.fullName == "scala.Some" } =>
        val (it, ib) = patternTest(subs.head, subj)
        (if (it == "true") s"($subj <> invalid)" else s"($subj <> invalid) and $it", ib)
      // `h :: t` on a List, which is an array here (not a chain of cells): non-empty, its first element, the rest
      case u @ Unapply(_, _, List(hp, tp)) if { val c = patternClass(u); c.exists && c.fullName == "scala.collection.immutable.::" } =>
        val (ht, hb) = patternTest(hp, s"$subj[0]")
        val (tt, tb) = patternTest(tp, s"_drop($subj, 1)")
        ((s"($subj <> invalid and $subj.count() > 0)" :: List(ht, tt)).filter(_ != "true").mkString(" and "), hb ++ tb)
      case u @ Unapply(_, _, subs) =>
        val cls = patternClass(u)
        val fields = if (cls.exists) cls.caseFields.map(_.name) else Nil
        val tag =
          if (cls.exists && !cls.fullName.matches("scala\\.Tuple\\d+")) s"($subj <> invalid and $subj._cls = ${brStr(cls.name)})"
          else if (cls.exists) s"($subj <> invalid)"
          else "true"
        val subT = subs.zipWithIndex.map { case (sp, i) =>
          patternTest(sp, s"$subj.${fields.lift(i).map(brSafe).getOrElse("_" + (i + 1))}") }
        val test = (tag :: subT.map(_._1)).filter(_ != "true").mkString(" and ")
        ((if (test.isEmpty) "true" else test), subT.flatMap(_._2))
      // A stable identifier (a case object, `Nil`, `None`, a val) matches by
      // equality. Without this it fell through to `true` and ALWAYS matched.
      case t: Term if (t match { case Ident(_) | Select(_, _) => true; case _ => false })
                      && t.symbol.exists && (t.symbol.flags.is(Flags.Module) || t.symbol.isValDef) =>
        val v = emitTerm(t)
        // An `object` is a single instance: matching `case Conclusion.Continue =>` is an identity test.
        if (v == "invalid") (s"($subj = invalid)", Nil)
        else if (t.symbol.exists && t.symbol.flags.is(Flags.Module)) (s"RtIdentity($subj, $v)", Nil)
        else (s"_eq($subj, $v)", Nil)
      case _ => ("true", Nil)
    }

    // ── statements, sinks and A-normalisation ────────────────────────────────
    // BrightScript keeps statements and expressions strictly apart: no
    // expression-level if/match/try, no blocks, no `return` from inside an
    // expression, and `_iif(c, a, b)` evaluates BOTH arms. So an expression is
    // emitted INTO a sink — discarded, returned, or assigned to an lvalue —
    // and control flow that appears where a value is needed is hoisted into
    // the statements ahead of the current one (A-normal form), a temp standing
    // in for its value. A sink is "" (discard), `skReturn`, or an lvalue.
    def skReturn: String = "__RT_RET__"
    def sinkLine(sk: String, v: String): String =
      if (sk == "") stmtTerm(v)
      else if (sk == skReturn) s"  return $v"
      else s"  $sk = $v"
    def sinkUnit(sk: String): String =
      if (sk == "") "" else if (sk == skReturn) "  return invalid" else s"  $sk = invalid"

    /** Emit ONE statement: whatever its expressions hoist is written first. */
    def withStatement(f: => String): String = {
      pendingStack.push(mutable.ListBuffer[String]())
      val line = f
      val pre = pendingStack.pop().toList
      (pre :+ line).filter(_.trim.nonEmpty).mkString("\n")
    }

    def hoist(t: Term): String =
      if (pendingStack.isEmpty) todo(t)
      else {
        anfCounter += 1
        val tmp = s"_v$anfCounter"
        pendingStack.top += emitInto(t, tmp)
        tmp
      }

    def hoistBlock(bl: Block): String =
      if (pendingStack.isEmpty) todo(bl)
      else {
        emitStats(bl.statements).foreach { e =>
          if (e.trim.nonEmpty) pendingStack.top += e
        }
        emitTerm(bl.expr)
      }

    def emitInto(t: Term, sk: String): String = t match {
      case Inlined(Some(call), _, _) if rwGenType(call).isDefined => withStatement(sinkLine(sk, emitRwGen(rwGenType(call).get, 0)))
      case Inlined(_, bindings, expansion) if bindings.nonEmpty =>
        (bindings.map(emitStat) :+ emitInto(expansion, sk)).filter(_.trim.nonEmpty).mkString("\n")
      case _ => emitIntoU(unwrap(t), sk)
    }

    def emitIntoU(t: Term, sk: String): String = t match {
      case Block(List(cd: ClassDef), e) if isLocalClassInstantiation(cd, e) =>
        withStatement(sinkLine(sk, emitAnonInstance(cd, e)))
      case Block(List(dd: DefDef), _: Closure) => withStatement(sinkLine(sk, emitClosure(dd)))
      case Block(Nil, e) => emitInto(e, sk)
      // Single-threaded target: `synchronized { body }` in statement or return
      // position IS the body — no thunk to allocate and force. (In expression
      // position it stays `RtSynchronized(thunk)`, preserving evaluation order.)
      case Apply(fn, List(body)) if fn.symbol.exists && fn.symbol.name == "synchronized" =>
        emitInto(body, sk)
      case Block(stats, e) if allValDefs(stats) && isTempVals(stats) && dropsDefaults(e) =>
        withStatement(sinkLine(sk, emitTempValBlock(stats.collect { case v: ValDef => v }, e)))
      case Block(stats, e) =>
        (emitStats(stats) :+ emitInto(e, sk)).filter(_.trim.nonEmpty).mkString("\n")
      case If(c, a, b) =>
        val head = withStatement(s"  if ${emitTerm(c)} then")
        val thenPart = emitInto(a, sk)
        if (sk == "" && isUnit(b)) s"$head\n$thenPart\n  end if"
        else s"$head\n$thenPart\n  else\n${emitInto(b, sk)}\n  end if"
      case Match(scrut, cases) => emitMatchStat(scrut, cases, sk)
      case Try(body, cases, fin) => emitTryStat(body, cases, fin, sk)
      case While(cond, body) =>
        val head = withStatement(s"  while ${emitTerm(cond)}")
        List(head, emitInto(body, ""), "  end while", sinkUnit(sk)).filter(_.trim.nonEmpty).mkString("\n")
      case asg: Assign =>
        List(emitStat(asg), sinkUnit(sk)).filter(_.trim.nonEmpty).mkString("\n")
      // a `return` out of the method being written (see returnTarget), whatever sink the code around it had
      case Return(expr, from) if returnTarget.exists && from == returnTarget =>
        if (expr.tpe.widen =:= TypeRepr.of[Unit]) List(emitInto(expr, ""), sinkUnit(skReturn)).filter(_.trim.nonEmpty).mkString("\n")
        else emitInto(expr, skReturn)
      case e if isUnit(e) => sinkUnit(sk)
      case e => withStatement(sinkLine(sk, emitTerm(e)))
    }

    def isModuleVar(s: Symbol): Boolean =
      s.exists && s.isValDef && s.flags.is(Flags.Mutable) && s.owner.exists && s.owner.flags.is(Flags.Module)

    /** The non-null operand of `x == null` (either side) when a direct
      * `= invalid` test is safe: an object of a concrete class. Never a string,
      * number, boolean or function (BrightScript may refuse the comparison),
      * nor a type that could hold one (Any, AnyRef, a type parameter, java.*). */
    /** A reference to an `object` (a case object such as
      * `ReactionStatus.Continue`, `None`, `Nil`): one instance, so `==` against
      * it is identity — `_eq`'s structural walk reaches the same answer slowly. */
    def isModuleOperand(t: Term): Boolean = {
      val u = unwrap(t)
      (u.symbol.exists && u.symbol.flags.is(Flags.Module))
        || { val ts = u.tpe.widen.typeSymbol; ts.exists && ts.flags.is(Flags.Module) }
    }

    def nullCompared(a: Term, b: Term): Option[Term] = {
      def isNull(t: Term): Boolean = unwrap(t) match {
        case Literal(NullConstant()) => true
        case _                       => false
      }
      val other = if (isNull(b)) Some(a) else if (isNull(a)) Some(b) else None
      other.filter { o =>
        val tp = o.tpe.widen
        val ts = tp.typeSymbol
        ts.exists && ts.isClassDef && !isPrimLike(o) && !isFunctionType(tp)
          && !ts.fullName.startsWith("java.")
          && !(tp =:= TypeRepr.of[Any]) && !(tp =:= TypeRepr.of[AnyRef]) && !(tp =:= TypeRepr.of[Matchable])
          && !(tp <:< TypeRepr.of[String]) && !(tp <:< TypeRepr.of[AnyVal])
      }
    }

    /** A lambda that calls itself (`lazy val turn: () => Unit = () => … turn() …`) is captured into its own
      * environment, and the value was read while it was still being made — a use of an uninitialized variable, which
      * stops the thread. The environment is given the name once the closure exists instead. */
    def selfCaptured(code: String, lhs: String): String = {
      val capture = s", fv_$lhs: $lhs"
      if (!code.contains(s"$lhs = { fn: ") || !code.contains(capture)) code
      else code.replace(capture, "") + s"\n  $lhs.env.fv_$lhs = $lhs"
    }

    def emitStat(st: Statement): String = st match {
      case vd @ ValDef(name, _, Some(rhs)) =>
        val lhs = brLocal(name)
        if (boxedVars.contains(vd.symbol))
          List(emitInto(rhs, lhs), s"  $lhs = { v: $lhs }").filter(_.trim.nonEmpty).mkString("\n")
        else selfCaptured(emitInto(rhs, lhs), lhs)
      case ValDef(_, _, None) => ""
      case Assign(lhs, rhs) if isModuleVar(lhs.symbol) =>
        val k = s"${modulePrefix(lhs.symbol.owner)}_${brSafe(lhs.symbol.name)}"
        val tmp = s"__mvset_$k"
        List(emitInto(rhs, tmp), s"  ${k}__set($tmp)").filter(_.trim.nonEmpty).mkString("\n")
      case Assign(lhs, rhs) => emitInto(rhs, emitTerm(lhs))
      case _: DefDef | _: ClassDef | _: TypeDef => ""
      case term: Term => emitInto(term, "")
      case _ => ""
    }

    /** A block's statements. A local def becomes a closure held in a local of its name, made where the def stands
      * (by then everything it may read is: Scala lets a def name no later val). One that names itself, or a def of the
      * block not made yet, has that capture put in its environment once the closure named exists. */
    def emitStats(stats: List[Statement]): List[String] = {
      val defs = stats.collect { case dd: DefDef if isLocalDef(dd.symbol) => dd.symbol }.toSet
      if (defs.isEmpty) stats.map(emitStat)
      else {
        val made = mutable.Set[Symbol]()
        val waiting = mutable.ListBuffer[(String, Symbol)]()   // (the closure's local, the def it waits for)
        stats.map {
          case dd: DefDef if defs.contains(dd.symbol) =>
            val nm = brLocal(dd.name)
            val later = freeVars(dd).map(_._2).filter(s => defs.contains(s) && !made.contains(s)).toSet
            val line = withStatement(s"  $nm = ${emitClosure(dd, later)}")
            made += dd.symbol
            later.foreach(s => waiting += ((nm, s)))
            val ready = waiting.filter((_, s) => made.contains(s)).toList
            waiting --= ready
            (line :: ready.map((h, s) => s"  $h.env.fv_${brLocal(s.name)} = ${brLocal(s.name)}")).mkString("\n")
          case st => emitStat(st)
        }
      }
    }

    def emitBody(t: Term, wantRet: Boolean): String = emitInto(t, if (wantRet) skReturn else "")
    def emitTail(expr: Term, wantRet: Boolean): String = emitBody(expr, wantRet)
    // a method's body, its `return`s written as returns (see returnTarget)
    def emitMethodBody(method: Symbol, rhs: Term, wantRet: Boolean): String = {
      val saved = returnTarget
      returnTarget = method
      try emitBody(rhs, wantRet) finally returnTarget = saved
    }

    /** Whether both arms may go to `_iif`, which evaluates the two of them whichever the answer: a pure value that
      * cannot fault. A division can (by zero), and the arm guarded against it is exactly where one is written, so an
      * arm holding one is laid out as a statement instead. */
    def iifSafe(a: Term, b: Term): Boolean =
      pureValue(a) && pureValue(b) && !divides(a) && !divides(b)

    /** Whether `t` is a number written down that is not zero — a divisor that needs no guarding. Most division in a
      * layout is by one of these (`/ 2.0`), and they are left to divide inline. */
    def nonZeroLiteral(t: Term): Boolean = unwrap(t) match {
      case Literal(IntConstant(i))    => i != 0
      case Literal(LongConstant(l))   => l != 0L
      case Literal(DoubleConstant(d)) => d != 0.0
      case Literal(FloatConstant(f))  => f != 0.0f
      case _                          => false
    }

    /** Whether a division or a remainder is worked out anywhere in `t`. */
    def divides(t: Term): Boolean = unwrap(t) match {
      case Apply(Select(l, op), List(r)) => op == "/" || op == "%" || divides(l) || divides(r)
      case Select(q, _)                  => divides(q)
      case _                             => false
    }

    def allValDefs(stats: List[Statement]): Boolean =
      stats.nonEmpty && stats.forall { case _: ValDef => true; case _ => false }
    // scalac's own temporaries (`x$1`, default-argument proxies) — never a user's `val`, which is bound once
    def isTempVals(stats: List[Statement]): Boolean =
      stats.forall { case v: ValDef => v.name.contains("$") || v.symbol.flags.is(Flags.Synthetic) || v.symbol.flags.is(Flags.Artifact); case _ => false }

    def isStmtExpr(t: Term): Boolean = unwrap(t) match {
      case _: Assign => true
      case _: If     => true
      case _: Match  => true
      case _: Try    => true
      case _         => false
    }

    def substToken(in: String, name: String, value: String): String = {
      val re = "(?<![A-Za-z0-9_$.])" + java.util.regex.Pattern.quote(name) + "(?![A-Za-z0-9_$])"
      in.replaceAll(re, java.util.regex.Matcher.quoteReplacement(value))
    }

    // The if/elseif chain matching `subj` against `cases`. `noMatch` is the
    // statement for when nothing matches: a MatchError for `match`, a rethrow
    // for `catch`.
    def emitCasesOn(subj: String, cases: List[CaseDef], sk: String, noMatch: String): List[String] = {
      val sb = mutable.ListBuffer[String]()
      var exhaustive = false
      var first = true
      cases.foreach { cd =>
        if (!exhaustive) {
          val (test, binds) = patternTest(cd.pattern, subj)
          val guard = cd.guard.map { g =>
            pendingStack.push(mutable.ListBuffer[String]())
            var ge = emitTerm(g)
            if (pendingStack.pop().nonEmpty) todos += "unsupported: match guard needing statements"
            // A guard reads the pattern's bindings, which are only assigned
            // inside the branch: substitute each bound name by its path.
            binds.foreach { bd =>
              val eq = bd.indexOf(" = ")
              if (eq > 0) ge = substToken(ge, bd.substring(0, eq).trim, bd.substring(eq + 3).trim)
            }
            s" and ($ge)"
          }.getOrElse("")
          sb += s"  ${if (first) "if" else "else if"} $test$guard"
          first = false
          binds.foreach(bd => sb += s"    $bd")
          val body = emitInto(cd.rhs, sk)
          if (body.trim.nonEmpty) sb += body
          if (test == "true" && guard.isEmpty) exhaustive = true
        }
      }
      if (!exhaustive && noMatch.nonEmpty) {
        sb += "  else"
        sb += noMatch
      }
      sb += "  end if"
      sb.toList
    }

    def emitMatchStat(scrut: Term, cases: List[CaseDef], sk: String): String = {
      val mv = s"_mt${matchCounter}"; matchCounter += 1
      val head = withStatement(s"  $mv = ${emitTerm(scrut)}")
      (head :: emitCasesOn(mv, cases, sk, s"    RtThrow(MatchError_new($mv))")).mkString("\n")
    }

    // `try body catch { cases } finally fin`. The body and handlers assign a
    // result temp rather than returning from inside `try` (which would skip
    // `finally`); an unmatched exception is rethrown; with a finally block, an
    // exception from the body OR a handler is held, `finally` runs, then it is
    // rethrown.
    def emitTryStat(body: Term, cases: List[CaseDef], fin: Option[Term], sk: String): String = {
      val n = matchCounter; matchCounter += 1
      val ex = s"_ex$n"
      val wantsValue = sk != "" && !(body.tpe.widen =:= TypeRepr.of[Unit])
      val res = s"_tr$n"
      val inner = if (wantsValue) res else ""
      val sb = mutable.ListBuffer[String]()
      fin match {
        case None =>
          sb += "  try"
          sb += emitInto(body, inner)
          sb += s"  catch $ex"
          sb ++= emitCasesOn(ex, cases, inner, s"    throw $ex")
          sb += "  end try"
        case Some(f) =>
          val err = s"_te$n"
          sb += s"  $err = invalid"
          sb += "  try"
          sb += emitInto(body, inner)
          sb += s"  catch $ex"
          if (cases.isEmpty) sb += s"    $err = $ex"
          else {
            sb += "    try"
            sb ++= emitCasesOn(ex, cases, inner, s"    throw $ex")
            sb += s"    catch ${ex}h"
            sb += s"      $err = ${ex}h"
            sb += "    end try"
          }
          sb += "  end try"
          sb += emitInto(f, "")
          sb += s"  if $err <> invalid then throw $err"
      }
      if (wantsValue) sb += sinkLine(sk, res)
      else if (sk == skReturn) sb += "  return invalid"
      sb.filter(_.trim.nonEmpty).mkString("\n")
    }

    // Scala hoists a facade call's named/default args into temp vals:
    //   { val title$1 = …; val key$2 = Facade.$default$1; …; Facade(title$1, key$2, …) }
    // Inline the REAL ones back into the call by token substitution and STRIP
    // the omitted-default ones (the platform runtime supplies defaults).
    def emitTempValBlock(vals: List[ValDef], expr: Term): String = {
      def sub(in: String, name: String, value: String): String = {
        val re = "(?<![A-Za-z0-9_$])" + java.util.regex.Pattern.quote(name) + "(?![A-Za-z0-9_$])"
        in.replaceAll(re, java.util.regex.Matcher.quoteReplacement(value))
      }
      val marker = "__RT_DEF__"
      def innermost(t: Term): Term = unwrap(t) match {
        case Apply(f, _)     => innermost(f)
        case TypeApply(f, _) => innermost(f)
        case other           => other
      }
      // Only a call into something the emitter does NOT write (a facade,
      // a runtime stub) has defaults to strip; a user function's defaults are
      // real arguments (see argParts).
      val (defaultVals, realVals) =
        if (dropsDefaults(expr)) vals.partition(_.rhs.exists(isDefaultArg)) else (Nil, vals)
      var res = emitTerm(expr)
      // Refs to these temp vals were emitted through brLocal (`title$1` →
      // `title_1`), so substitute the brLocal'd name, not the raw Scala one.
      for (vd <- defaultVals) res = sub(res, brLocal(vd.name), marker)
      // No extra parens: emitTerm already wraps binary ops, and an inlined temp
      // sits in a delimited slot (assoc `key: value` / arg). Wrapping a receiver
      // as `(self.player).setSource(…)` is a BrightScript syntax error (a
      // statement can't start with `(`, and `(expr).method()` isn't allowed).
      for (vd <- realVals.reverse) {
        val rhs = vd.rhs.get
        res = sub(res, brLocal(vd.name), if (isDefaultArg(rhs)) emitDefaultArg(rhs) else emitTerm(rhs))
      }
      val id = "[A-Za-z_$][A-Za-z0-9_$]*"
      res
        .replaceAll(id + ": " + marker + ", ", "")
        .replaceAll(", " + id + ": " + marker, "")
        .replaceAll(marker + ", ", "")
        .replaceAll(", " + marker, "")
        .replaceAll("\\(" + marker + "\\)", "()")
        .replaceAll(id + ": " + marker, "")
        .replaceAll(marker, "invalid")
    }

    // ── object → top-level Obj_member functions ──────────────────────────────
    def emitModule(cd: ClassDef): Unit = {
      val base = modulePrefix(cd.symbol)
      cd.body.foreach {
        case dd @ DefDef(name, pcs, _, Some(rhs))
            if userMember(dd.symbol) && name != "<init>" && !name.endsWith("_=") && keepMember(dd.symbol) =>
          val ps = termParams(pcs)
          out.append(s"function ${base}_${memberName(dd.symbol, name)}(${packParams(ps).map(p => s"$p as dynamic").mkString(", ")}) as dynamic\n")
          out.append(unpackParams(ps))
          out.append(emitMethodBody(dd.symbol, rhs, true)).append("\n")
          out.append("end function\n\n")
        case vd @ ValDef(name, _, Some(rhs)) if userMember(vd.symbol) && modelModuleOf(vd.symbol).isEmpty && liveLazy(vd.symbol) =>
          val mutable = vd.symbol.flags.is(Flags.Mutable)
          out.append(s"function ${base}_${brSafe(name)}() as dynamic\n")
          unwrap(rhs) match {
            case Literal(_) if !mutable => out.append(emitInto(rhs, skReturn)).append("\n")
            case _ =>
              // Scala evaluates an object's vals once; the accessor caches in
              // the global AA. The flag is set BEFORE the value so a val that
              // (indirectly) reads itself during init sees `invalid`, as the
              // JVM would see null, rather than recursing.
              val k = s"${base}_${brSafe(name)}"
              out.append("  ga = GetGlobalAA()\n")
              out.append(s"  cached = ga[${brStr("__mv_" + k)}]\n")
              out.append("  if cached <> invalid then return cached\n")
              out.append(s"  if not ga.DoesExist(${brStr("__mvs_" + k)}) then\n")
              out.append(s"    ga[${brStr("__mvs_" + k)}] = true\n")
              out.append(emitInto(rhs, s"ga[${brStr("__mv_" + k)}]")).append("\n")
              out.append("  end if\n")
              out.append(s"  return ga[${brStr("__mv_" + k)}]\n")
          }
          out.append("end function\n\n")
          // An object `var` also gets a setter. It marks the var initialised so
          // a first READ after a write doesn't re-run the initialiser over it.
          if (mutable) {
            val k = s"${base}_${brSafe(name)}"
            out.append(s"function ${k}__set(v as dynamic) as dynamic\n")
            out.append("  ga = GetGlobalAA()\n")
            out.append(s"  ga[${brStr("__mvs_" + k)}] = true\n")
            out.append(s"  ga[${brStr("__mv_" + k)}] = v\n")
            out.append("  return invalid\nend function\n\n")
          }
        case _ => ()
      }
    }

    // ── class → constructor fn (assoc + method refs) + method fns ────────────
    def emitClass(cd: ClassDef): Unit = {
      val sym = cd.symbol
      val cn  = sym.name
      currentClass = sym
      val ctorParams = termParams(cd.constructor.paramss)

      // constructor
      out.append(s"function ${cn}_new(${packParams(ctorParams).map(p => s"$p as dynamic").mkString(", ")}) as object\n")
      out.append(unpackParams(ctorParams))
      out.append("  self = {}\n")
      out.append(s"  self._cls = ${brStr(cn)}\n")
      { val tag = isaTag(sym); if (tag.nonEmpty) out.append(s"  self._isa = ${brStr(tag)}\n") }
      cd.body.foreach {
        case vd: ValDef if userMember(vd.symbol) && vd.symbol.flags.is(Flags.ParamAccessor) =>
          out.append(s"  self.${brSafe(vd.name)} = ${brLocal(vd.name)}\n")
        case vd @ ValDef(name, _, Some(rhs)) if userMember(vd.symbol) =>
          out.append(emitInto(rhs, s"self.${brSafe(name)}")).append("\n")
        // A constructor STATEMENT (`register(this)` in a class body) runs at
        // construction; it used to be silently dropped.
        case st: Term =>
          val e = emitStat(st)
          if (e.nonEmpty) out.append(e).append("\n")
        case _ => ()
      }
      // expose method function-refs so the runtime can call self.m(self, …)
      cd.body.foreach {
        case dd @ DefDef(name, _, _, Some(_)) if emittableMethod(dd) =>
          val mn = memberName(dd.symbol, name)
          out.append(s"  self.$mn = ${cn}_$mn\n")
        case _ => ()
      }
      out.append("  return self\n")
      out.append("end function\n\n")

      // methods
      cd.body.foreach {
        case dd @ DefDef(name, pcs, ret, Some(rhs)) if emittableMethod(dd) =>
          val own = termParams(pcs)
          val ps = "self as object" :: packParams(own).map(p => s"$p as dynamic")
          out.append(s"function ${cn}_${memberName(dd.symbol, name)}(${ps.mkString(", ")}) as dynamic\n")
          out.append(unpackParams(own))
          val wantRet = !(ret.tpe =:= TypeRepr.of[Unit])
          out.append(emitMethodBody(dd.symbol, rhs, wantRet)).append("\n")
          out.append("  return invalid\n")
          out.append("end function\n\n")
        case _ => ()
      }
      currentClass = Symbol.noSymbol
    }

    // ── object-model type → methods + init chain + (concrete) constructor ──
    def parentInfo(p: Tree): (Symbol, List[Term]) = {
      def peel(t: Term, acc: List[Term]): (Symbol, List[Term]) = t match {
        case Apply(f, as)        => peel(f, as ++ acc)
        case TypeApply(f, _)     => peel(f, acc)
        case Select(New(tpt), _) => (tpt.tpe.typeSymbol, acc)
        case New(tpt)            => (tpt.tpe.typeSymbol, acc)
        case Block(_, e)         => peel(e, acc)
        case other               => (other.tpe.typeSymbol, acc)
      }
      p match {
        case t: Term      => peel(t, Nil)
        case tt: TypeTree => (tt.tpe.typeSymbol, Nil)
        case _            => (Symbol.noSymbol, Nil)
      }
    }

    def isWildcardInit(t: Term): Boolean = unwrap(t) match {
      case Ident("_") => true
      case other      => other.symbol.exists && other.symbol.fullName.endsWith("uninitialized")
    }

    def vtableFor(sym: Symbol): List[(String, String)] = {
      val slots = mutable.LinkedHashMap[String, String]()
      sym.typeRef.baseClasses.filter(userTypeDefs.contains).foreach { b =>
        userTypeDefs(b).body.foreach {
          case dd: DefDef if emittableMethod(dd) && !dd.symbol.flags.is(Flags.Private) =>
            val sl = slotName(dd.symbol)
            if (!slots.contains(sl)) slots(sl) = modelImplName(dd.symbol)
          case vd: ValDef if vd.symbol.flags.is(Flags.Lazy) && vd.rhs.isDefined && !vd.symbol.flags.is(Flags.Private) && !vd.symbol.flags.is(Flags.Module) && liveLazy(vd.symbol) =>
            val sl = slotName(vd.symbol)
            if (!slots.contains(sl)) slots(sl) = modelImplName(vd.symbol)
          case vd: ValDef if isVirtualField(vd.symbol) =>
            val sl = slotName(vd.symbol)
            if (!slots.contains(sl)) slots(sl) = modelImplName(vd.symbol)
          case _ => ()
        }
      }
      slots.toList.reverse
    }

    def emitModelType(cd: ClassDef, sb: StringBuilder): Unit = {
      val sym = cd.symbol
      val cn  = typeName(sym)
      val savedClass = currentClass
      currentClass = sym
      val ctorParams = termParams(cd.constructor.paramss)
      val isTrait = sym.flags.is(Flags.Trait)
      val extraParams = anonCaptures.get(sym).map(cs => "__outer" :: cs.map(c => "__fv_" + brSafe(c.name))).getOrElse(Nil)

      // member implementations
      cd.body.foreach {
        case dd: DefDef if emittableMethod(dd) =>
          val own = termParams(dd.paramss)
          val ps = "self as object" :: packParams(own).map(p => s"$p as dynamic")
          sb.append(s"function ${modelImplName(dd.symbol)}(${ps.mkString(", ")}) as dynamic\n")
          sb.append(unpackParams(own))
          val wantRet = !(dd.returnTpt.tpe =:= TypeRepr.of[Unit])
          sb.append(emitMethodBody(dd.symbol, dd.rhs.get, wantRet)).append("\n")
          sb.append("  return invalid\nend function\n\n")
        case vd: ValDef if vd.symbol.flags.is(Flags.Lazy) && vd.rhs.isDefined && !vd.symbol.flags.is(Flags.Module) && liveLazy(vd.symbol) =>
          val k = brSafe(vd.name)
          sb.append(s"function ${modelImplName(vd.symbol)}(self as object) as dynamic\n")
          sb.append(s"  cached = self[${brStr("__lz_" + k)}]\n")
          sb.append("  if cached <> invalid then return cached\n")
          sb.append(s"  if not self.DoesExist(${brStr("__lzs_" + k)}) then\n")
          sb.append(s"    self[${brStr("__lzs_" + k)}] = true\n")
          sb.append(emitInto(vd.rhs.get, s"self[${brStr("__lz_" + k)}]")).append("\n")
          sb.append("  end if\n")
          sb.append(s"  return self[${brStr("__lz_" + k)}]\nend function\n\n")
        case vd: ValDef if isVirtualField(vd.symbol) =>
          sb.append(s"function ${modelImplName(vd.symbol)}(self as object) as dynamic\n")
          sb.append(s"  return self.${fieldKey(vd.symbol)}\nend function\n\n")
        case _ => ()
      }

      // init: super -> mixins -> own fields and statements, guarded for diamonds
      val guard = brStr("__i_" + cn)
      sb.append(s"function ${cn}__init(self as object${packParams(ctorParams).map(p => s", $p as dynamic").mkString}) as dynamic\n")
      sb.append(unpackParams(ctorParams))
      sb.append(s"  if self.DoesExist($guard) then return invalid\n")
      sb.append(s"  self[$guard] = true\n")
      cd.parents.foreach { p =>
        val (ps, pargs) = parentInfo(p)
        if (ps.exists && ps != sym && userTypeDefs.contains(ps)) {
          val ctor = parentCtor(p)
          inParentArgs = true
          val call = withStatement(s"  ${initName(ps, ctor)}(self${emitArgList(pargs, ctor).map(", " + _).mkString})")
          inParentArgs = false
          sb.append(call).append("\n")
        }
        else if (ps.exists && shimParents.contains(ps.fullName))
          sb.append(s"  ${shimParents(ps.fullName)}_init(self)\n")
      }
      cd.body.foreach {
        case vd: ValDef if vd.symbol.flags.is(Flags.ParamAccessor) =>
          sb.append(s"  self.${fieldKey(vd.symbol)} = ${brLocal(vd.name)}\n")
          // an object is one flat table, so a parameter kept under the name of a member the class inherits overwrites it
          // (`size: Double` on a component replaced its `size` feature, and every layout touching it failed quietly):
          // a gap, so the checks stop it rather than a device
          shadowedBy(vd.symbol).foreach { base =>
            todos += s"parameter `${vd.name}` of ${sym.name} overwrites ${base.name}.${vd.name} (rename it)${where(vd)}"
            sb.append(s"  RtTodo(${brStr("parameter " + vd.name + " of " + sym.name + " overwrites " + base.name + " " + vd.name)})\n")
          }
        case vd @ ValDef(_, _, rhsOpt) if !vd.symbol.flags.is(Flags.Lazy) && userMember(vd.symbol) =>
          rhsOpt match {
            case Some(r) if !isWildcardInit(r) => sb.append(emitInto(r, s"self.${fieldKey(vd.symbol)}")).append("\n")
            case _                             => sb.append(s"  self.${fieldKey(vd.symbol)} = invalid\n")
          }
        case _: DefDef | _: ValDef | _: TypeDef | _: ClassDef => ()
        case st: Term =>
          val e = emitStat(st)
          if (e.trim.nonEmpty) sb.append(e).append("\n")
        case _ => ()
      }
      sb.append("  return invalid\nend function\n\n")

      // secondary constructors: `def this(...) = { this(...); stats }`
      secondaryCtors(sym).zipWithIndex.foreach { case (dd, k) =>
        val ps = termParams(dd.paramss)
        sb.append(s"function ${cn}__init__${k + 1}(self as object${packParams(ps).map(p => s", $p as dynamic").mkString}) as dynamic\n")
        sb.append(unpackParams(ps))
        val stats = unwrap(dd.rhs.get) match {
          case Block(ss, e) => ss :+ e
          case other        => List(other)
        }
        stats.foreach {
          case Apply(f, as) if f.symbol.exists && f.symbol.isClassConstructor =>
            sb.append(withStatement(s"  ${initName(sym, f.symbol)}(self${emitArgList(as, f.symbol).map(", " + _).mkString})")).append("\n")
          case st =>
            val e = emitStat(st)
            if (e.trim.nonEmpty) sb.append(e).append("\n")
        }
        sb.append("  return invalid\nend function\n\n")
      }

      // a module: one instance, stored BEFORE its init runs (as the JVM sets MODULE$), so init can refer to it
      if (sym.flags.is(Flags.Module)) {
        val key = brStr("__mod_" + cn)
        sb.append(s"function ${cn}__module() as object\n")
        sb.append("  ga = GetGlobalAA()\n")
        sb.append(s"  self = ga[$key]\n")
        sb.append("  if self <> invalid then return self\n")
        sb.append("  self = {}\n")
        sb.append(s"  self._cls = ${brStr(cn)}\n")
        { val tag = isaTag(sym); if (tag.nonEmpty) sb.append(s"  self._isa = ${brStr(tag)}\n") }
        sb.append(s"  self._name = ${brStr(sym.name.stripSuffix("$"))}\n")   // the Scala name, for toString
        sym.typeRef.baseClasses.reverse.foreach { bc =>
          shimParents.get(bc.fullName).foreach(shim => sb.append(s"  ${shim}__install(self)\n"))
        }
        vtableFor(sym).foreach { case (slot, impl) => sb.append(s"  self.$slot = $impl\n") }
        sb.append(s"  ga[$key] = self\n")
        sb.append(s"  ${cn}__init(self)\n")
        sb.append("  return self\nend function\n\n")
      }
      // constructor: allocate, captures, runtime-parent slots, vtable, init
      else if (!isTrait && !sym.flags.is(Flags.Abstract)) {
        sb.append(s"function ${cn}_new(${(extraParams ++ packParams(ctorParams)).map(p => s"$p as dynamic").mkString(", ")}) as object\n")
        sb.append("  self = {}\n")
        sb.append(s"  self._cls = ${brStr(cn)}\n")
        { val tag = isaTag(sym); if (tag.nonEmpty) sb.append(s"  self._isa = ${brStr(tag)}\n") }
        extraParams.foreach(ep => sb.append(s"  self.$ep = $ep\n"))
        if (sym.flags.is(Flags.Case))
          sb.append(s"  self._fields = [${sym.caseFields.map(f => brStr(fieldKey(f))).mkString(", ")}]\n")
        sym.typeRef.baseClasses.reverse.foreach { bc =>
          shimParents.get(bc.fullName).foreach(shim => sb.append(s"  ${shim}__install(self)\n"))
        }
        if (vtableFor(sym).nonEmpty) sb.append(s"  self.Append(${cn}__vt())\n")
        sb.append(s"  ${cn}__init(self${packParams(ctorParams).map(p => ", " + p).mkString})\n")
        sb.append("  return self\nend function\n\n")
      }
      // the method slots: one table for the class, made once and copied into each instance. Listed in every
      // constructor they were a third of a large app's code (a component's fifty inherited slots, in each of its
      // subclasses). A class's table is its superclass's with its own slots put in.
      if (!isTrait && !sym.flags.is(Flags.Module) && vtableFor(sym).nonEmpty) {
        val slots = vtableFor(sym)
        val parent = vtParent(cd)
        val inherited = parent.map(p => vtableFor(p).toMap).getOrElse(Map.empty[String, String])
        // (a superclass slot this class has not, or two slots one name but for case: the table is written whole)
        val whole = parent.isEmpty || !inherited.keySet.subsetOf(slots.map(_._1).toSet) ||
          slots.map(_._1.toLowerCase).distinct.size != slots.size
        val key = brStr("__vt_" + cn)
        sb.append(s"function ${cn}__vt() as object\n")
        sb.append("  ga = GetGlobalAA()\n")
        sb.append(s"  vt = ga[$key]\n")
        sb.append("  if vt <> invalid then return vt\n")
        sb.append("  vt = {}\n")
        if (!whole) sb.append(s"  vt.Append(${typeName(parent.get)}__vt())\n")
        slots.foreach { case (slot, impl) =>
          if (whole || !inherited.get(slot).contains(impl)) sb.append(s"  vt.$slot = $impl\n")
        }
        sb.append(s"  ga[$key] = vt\n")
        sb.append("  return vt\nend function\n\n")
      }
      currentClass = savedClass
    }

    /** The superclass whose slot table a class's own starts from: a class of the program's object model, not a trait. */
    def vtParent(cd: ClassDef): Option[Symbol] =
      cd.parents.headOption.map(p => parentInfo(p)._1).filter { ps =>
        ps.exists && ps != cd.symbol && !ps.flags.is(Flags.Trait) && !ps.flags.is(Flags.Module) && userTypeDefs.contains(ps) &&
          isModel(ps) && keepClass(ps) && vtableFor(ps).nonEmpty
      }

    /** `new T { … }` — an anonymous (or other local) class instance. The class
     *  is emitted out of line under a generated name, capturing the enclosing
     *  instance as `__outer` and each enclosing local it reads as `__fv_<x>`. */
    def emitAnonInstance(cd: ClassDef, e: Term): String = {
      val sym = cd.symbol
      if (!typeNameOverride.contains(sym)) {
        anonCounter += 1
        val encl = if (currentClass.exists) typeName(currentClass) else "Anon"
        typeNameOverride(sym) = s"${encl}_anon$anonCounter"
        if (currentClass.exists) anonOuter(sym) = currentClass
        val caps = mutable.LinkedHashSet[Symbol]()
        val trav = new TreeTraverser {
          override def traverseTree(t: Tree)(owner: Symbol): Unit = {
            t match {
              case id: Ident =>
                val sy = id.symbol
                if (sy.exists && sy.isTerm && (!sy.isDefDef || isLocalDef(sy)) && !isFacade(sy) && !moduleSyms.contains(sy.owner)
                   && sy.owner.exists && !sy.owner.isClassDef && (sy.owner.flags.is(Flags.Method) || sy.owner.isValDef)
                   && !isOwnedWithin(sy, sym))
                caps += sy
              case _ =>
            }
            super.traverseTree(t)(owner)
          }
        }
        cd.body.foreach(bd => trav.traverseTree(bd)(sym))
        // and what the parent's constructor is given (`new MaterialIconView(icon, 14.0) { … }` reads `icon`)
        cd.parents.foreach(p => trav.traverseTree(p)(sym))
        anonCaptures(sym) = caps.toList
        userTypeDefs(sym) = cd
        objectModelTypes += sym
        cd.body.foreach { case dd: DefDef => userMethodSyms += dd.symbol; case _ => () }
        val sb = new StringBuilder
        emitModelType(cd, sb)
        closures.append(sb.toString)
      }
      val outerArg = if (currentClass.exists) thisRef(currentClass) else "invalid"
      val capArgs = anonCaptures(sym).map(captureValue)
      s"${typeNameOverride(sym)}_new(${(outerArg :: capArgs).mkString(", ")})"
    }

    /** Local `var`s read or written inside a closure, a by-name argument or a
     *  local class are boxed, so both scopes share one cell. */
    def scanBoxedVars(root: Tree): Unit = {
      val candidates = mutable.Set[Symbol]()
      var nested = 0
      val trav = new TreeTraverser {
        override def traverseTree(t: Tree)(owner: Symbol): Unit = t match {
          case vd: ValDef =>
            val sy = vd.symbol
            if (sy.flags.is(Flags.Mutable) && sy.owner.exists && sy.owner.flags.is(Flags.Method)) candidates += sy
            super.traverseTree(t)(owner)
          case id: Ident =>
            if (nested > 0 && candidates.contains(id.symbol)) boxedVars += id.symbol
          case Block(List(_: DefDef), _: Closure) =>
            nested += 1
            super.traverseTree(t)(owner)
            nested -= 1
          // a local def is a closure too (see `emitStats`)
          case dd: DefDef if isLocalDef(dd.symbol) =>
            nested += 1
            super.traverseTree(t)(owner)
            nested -= 1
          case cd: ClassDef if (try cd.symbol.owner.flags.is(Flags.Method) catch case _: Throwable => false) =>
            nested += 1
            super.traverseTree(t)(owner)
            nested -= 1
          case ap @ Apply(fn, args) =>
            traverseTree(fn)(owner)
            val params = clauseParamsOf(ap)
            args.zipWithIndex.foreach { case (arg, i) =>
              val bn = params.lift(i).exists(isByNameParam)
              if (bn) nested += 1
              traverseTree(arg)(owner)
              if (bn) nested -= 1
            }
          case _ => super.traverseTree(t)(owner)
        }
      }
      trav.traverseTree(root)(Symbol.noSymbol)
    }

    // ── walk ─────────────────────────────────────────────────────────────
    def eachClassDef(tree: Tree)(f: ClassDef => Unit): Unit = tree match {
      case pc: PackageClause => pc.stats.foreach(s => eachClassDef(s)(f))
      case cd: ClassDef      => f(cd); cd.body.foreach(s => eachClassDef(s)(f))
      case _                 => ()
    }

    val mains = mutable.ListBuffer[DefDef]()
    def collectMains(tree: Tree): Unit = tree match {
      case pc: PackageClause => pc.stats.foreach(collectMains)
      case cd: ClassDef      => cd.body.foreach(collectMains)
      case dd: DefDef if isMain(dd.symbol) => mains += dd
      case _                 => ()
    }

    for (tasty <- tastys) collectMains(tasty.ast)
    // every class anywhere, local and anonymous ones included (reachability and the extended-by test)
    locally {
      val collector = new TreeTraverser {
        override def traverseTree(t: Tree)(owner: Symbol): Unit = {
          t match {
            case cd: ClassDef => allClassDefs(cd.symbol) = cd
            case _            => ()
          }
          super.traverseTree(t)(owner)
        }
      }
      for (tasty <- tastys) collector.traverseTree(tasty.ast)(Symbol.noSymbol)
    }
    val mainNames = mains.map(_.name).toSet
    for (tasty <- tastys)
      eachClassDef(tasty.ast) { cd =>
        if (isUserTypeSym(cd.symbol) && !mainNames.contains(cd.symbol.name)) userTypeDefs(cd.symbol) = cd
      }
    // Every user TYPE name (traits and case classes too) claims its namespace,
    // so a companion object's members take the `Obj` infix wherever they would
    // collide — `Reaction.apply` (trait) vs `Reaction.apply` (object).
    for (tasty <- tastys)
      eachClassDef(tasty.ast) { cd =>
        val sy = cd.symbol
        if (sy.flags.is(Flags.Module) && !hasNative(sy) && !isBuiltin(sy) && !isEnumLike(sy)
           && !sy.name.stripSuffix("$").endsWith("$package") && !mainNames.contains(sy.name)
           && sy.typeRef.baseClasses.exists(b => b != sy && userTypeDefs.contains(b))) {
          modelModules += sy
          userTypeDefs(sy) = cd
        }
      }
    for (mc <- modelModules) {
      val base = brIdent(mc.name.stripSuffix("$"))
      val owner = mc.owner
      val qualified =
        if (owner.exists && owner.flags.is(Flags.Module)) brIdent(owner.name.stripSuffix("$")) + "_" + base else base
      typeNameOverride(mc) =
        if (mc.companionClass.exists && userTypeDefs.contains(mc.companionClass)) qualified + "Obj" else qualified
    }
    userClassNames ++= userTypeDefs.keys.filterNot(modelModules.contains).map(_.name)
    for ((_, cd) <- userTypeDefs)
      cd.body.foreach { case dd: DefDef => userMethodSyms += dd.symbol; case _ => () }
    locally {
      val all = userTypeDefs.keys.toList
      for (sy <- all) {
        val bases    = sy.typeRef.baseClasses.filter(b => b != sy && userTypeDefs.contains(b))
        // extended by any class at all — an anonymous `new C { … }` inside a method counts (spice's
        // DurableSocketClient subclasses DurableSocket that way)
        val extended = allClassDefs.keys.exists(o => o != sy && (try o.typeRef.baseClasses.contains(sy) catch case _: Throwable => false))
        val caseWithMethods = sy.flags.is(Flags.Case) &&
          userTypeDefs(sy).body.exists { case dd: DefDef => emittableMethod(dd) && !dd.name.contains("$default$"); case _ => false }
        // A `lazy val` needs the object model's lazy getter; the flat class path would evaluate it eagerly.
        val hasLazy = userTypeDefs(sy).body.exists { case vd: ValDef => vd.symbol.flags.is(Flags.Lazy) && !vd.symbol.flags.is(Flags.Module); case _ => false }
        if (sy.flags.is(Flags.Trait) || bases.nonEmpty || extended || caseWithMethods || hasLazy) objectModelTypes += sy
      }
    }
    for (tasty <- tastys)
      eachClassDef(tasty.ast) { cd => if (isUserModule(cd) && !modelModules.contains(cd.symbol)) moduleSyms.getOrElseUpdate(cd.symbol, modulePrefix(cd.symbol)) }
    for (tasty <- tastys) scanBoxedVars(tasty.ast)

    // ── reachability ─────────────────────────────────────────────────────
    // Only what `main` reaches is emitted. A class is LIVE when something constructs it (`new`, a case-class
    // apply, a module reference) or it is an ancestor of a live class; a member can only run on a live class,
    // so one reached through a type nothing ever constructs is dead and its call site becomes `RtDead`. A
    // method is reached by reference; with it, every same-name-and-arity method of every live class (virtual
    // dispatch, over-approximated) and the names the runtime calls by itself. A live class keeps all its
    // fields (they are initialised with it), so their initialisers are walked too.
    locally {
      val runtimeNames = Set("toString", "equals", "hashCode", "apply", "initialValue", "compare", "compareTo")
      def arity(s: Symbol): Int = try s.paramSymss.flatten.count(_.isTerm) catch case _: Throwable => 0
      val queue = mutable.Queue[Tree]()
      val virtualKeys = mutable.Set[(String, Int)]()
      val pending = mutable.Map[Symbol, mutable.ListBuffer[Symbol]]()   // reached before their class was live
      def memberTree(m: Symbol): Option[Tree] =
        allClassDefs.get(m.owner).flatMap(_.body.collectFirst {
          case dd: DefDef if dd.symbol == m => dd
          case vd: ValDef if vd.symbol == m => vd
        })
      def activate(m: Symbol): Unit = {
        memberTree(m).foreach(queue += _)
        if (m.isDefDef) {
          val key = (m.name, arity(m))
          if (virtualKeys.add(key))
            for (c <- reachableClasses.toList; cd <- allClassDefs.get(c))
              cd.body.foreach {
                case dd: DefDef if dd.symbol != m && dd.name == key._1 && arity(dd.symbol) == key._2 => markMember(dd.symbol)
                case _ => ()
              }
        }
      }
      def markMember(m: Symbol): Unit =
        if (m.exists && !m.isClassConstructor && allClassDefs.contains(m.owner) && reachableMembers.add(m)) {
          // naming a module's member names the module
          if (m.owner.flags.is(Flags.Module)) markClass(m.owner)
          if (reachableClasses.contains(m.owner)) activate(m)
          else pending.getOrElseUpdate(m.owner, mutable.ListBuffer()) += m
        }
      def markClass(c: Symbol): Unit =
        if (c.exists && allClassDefs.contains(c) && reachableClasses.add(c)) {
          val cd = allClassDefs(c)
          (try c.typeRef.baseClasses catch case _: Throwable => Nil).foreach(b => if (b != c) markClass(b))
          queue += cd.constructor
          cd.parents.foreach(queue += _)
          cd.body.foreach {
            case vd: ValDef if vd.symbol.flags.is(Flags.Module) => ()   // a nested object: live only when named
            case vd: ValDef => vd.rhs.foreach(queue += _)
            case _: DefDef | _: ClassDef | _: TypeDef => ()
            case t: Term => queue += t                       // a constructor statement
            case _ => ()
          }
          cd.body.foreach {
            case dd: DefDef if runtimeNames(dd.name) || virtualKeys((dd.name, arity(dd.symbol))) => markMember(dd.symbol)
            case _ => ()
          }
          pending.remove(c).foreach(_.foreach(activate))
        }
      def markRef(s: Symbol): Unit =
        if (s.exists)
          if (s.isClassDef) markClass(s)
          else if (s.flags.is(Flags.Module)) markClass(try s.moduleClass catch case _: Throwable => Symbol.noSymbol)
          else if (s.isTerm)
            if (s.isClassConstructor) markClass(s.owner)
            else {
              markMember(s)
              // a companion `apply` constructs the case class
              if (s.name == "apply" && s.owner.exists && s.owner.flags.is(Flags.Module)) {
                val cc = try s.owner.companionClass catch case _: Throwable => Symbol.noSymbol
                if (cc.exists && cc.flags.is(Flags.Case)) markClass(cc)
              }
            }
      // A derived codec constructs its case class, and every case class in its fields, from the JSON it reads: one that
      // is only ever read (a server's answer) is live through its codec alone.
      val read = mutable.Set[Symbol]()
      def markRead(tp: TypeRepr, depth: Int): Unit = if (depth < 6) {
        (try tp.typeArgs catch case _: Throwable => Nil).foreach(a => markRead(a, depth))
        val sym = tp.typeSymbol
        if (sym.exists && sym.flags.is(Flags.Case) && !isBuiltin(sym) && allClassDefs.contains(sym) && read.add(sym)) {
          markClass(sym)
          (try sym.caseFields catch case _: Throwable => Nil).foreach { f =>
            try f.tree match {
              case vd: ValDef => markRead(vd.tpt.tpe, depth + 1)
              case _ => ()
            } catch case _: Throwable => ()
          }
        }
      }
      val walker = new TreeTraverser {
        override def traverseTree(t: Tree)(owner: Symbol): Unit = {
          t match {
            case New(tpt)             => try markClass(tpt.tpe.typeSymbol) catch case _: Throwable => ()
            case _: Ident | _: Select =>
              try markRef(t.symbol) catch case _: Throwable => ()
              // a codec named (a context bound's `rw`) reads its case class
              try {
                t match {
                  case term: Term =>
                    val w = term.tpe.widen
                    if (fqnOf(w).startsWith("fabric.rw.")) w.typeArgs.foreach(a => markRead(a, 0))
                  case _ => ()
                }
              } catch case _: Throwable => ()
            case Inlined(Some(call), _, _) => rwGenType(call).foreach(tp => markRead(tp, 0))
            case _                    => ()
          }
          super.traverseTree(t)(owner)
        }
      }
      for (dd <- mains; body <- dd.rhs) queue += body
      while (queue.nonEmpty) walker.traverseTree(queue.dequeue())(Symbol.noSymbol)
      // No `@main` (a library emitted alone, for the gap meter): nothing is a root, so everything is kept.
      if (mains.nonEmpty) pruning = true
    }

    for (tasty <- tastys)
      eachClassDef(tasty.ast) { cd =>
        if (!keepClass(cd.symbol)) ()
        else if (isModel(cd.symbol)) emitModelType(cd, out)
        else if (isUserClass(cd) && !mainNames.contains(cd.symbol.name)) emitClass(cd)
        else if (isUserModule(cd)) emitModule(cd)
      }
    // @main → rtMain(): the entry the scene calls.
    out.append("sub rtMain()\n")
    for (mc <- modelModules.toList.filter(_.flags.is(Flags.Case)).filter(keepClass).sortBy(typeName))
      out.append(s"  ${typeName(mc)}__module()\n")
    for (dd <- mains; body <- dd.rhs)
      out.append(emitBody(body, false)).append("\n")
    out.append("end sub\n")
  }

  // ── output ─────────────────────────────────────────────────────────────
  def writeOutput(): Unit = {
    val title = if (projectName.trim.nonEmpty) projectName else "App"
    write("manifest", manifest(title))
    write("source/Main.brs", mainBrs)
    write("components/AppScene.brs", sceneBrs)
    val appCode = out.toString + "\n' ── defunctionalised closures ──\n" + closures.toString
    val parts = RokuEmitter.split(appBrsHeader + RokuEmitter.compact(injectGa(appCode)), RokuEmitter.partBytes)
    // a part from a larger build before this one would still be listed nowhere, but it would ship
    val components = outDir.resolve("components")
    if (Files.isDirectory(components)) {
      val listing = Files.list(components)
      try listing.toArray.toList.map(_.asInstanceOf[Path])
        .filter(p => p.getFileName.toString.matches("App[0-9]+\\.brs")).foreach(p => Files.delete(p))
      finally listing.close()
    }
    parts.zipWithIndex.foreach { case (code, i) => write("components/" + RokuEmitter.partName(i), code) }
    write("components/AppScene.xml", sceneXml.replace(RokuEmitter.appScript(0),
      parts.indices.map(RokuEmitter.appScript).mkString("\n  ")))
    write("components/ScalaRuntime.brs", runtimeBrs + "\n" + shimsBrs + collectionShimsBrs)
  }

  // Facade access is emitted as `ga.<Module>.<member>` (see the Select case).
  // `ga` is a local alias for GetGlobalAA() — needed because a BrightScript
  // statement can't start with a call (`GetGlobalAA().Nav.push(…)` is a syntax
  // error) but CAN start with an identifier (`ga.Nav.push(…)`). So bind
  // `ga = GetGlobalAA()` at the top of every emitted function that references it.
  private def injectGa(code: String): String = {
    val lines = code.split("\n", -1)
    val sb = new StringBuilder
    var i = 0
    while (i < lines.length) {
      val line = lines(i)
      sb.append(line).append("\n")
      val t = line.trim
      if (t.startsWith("function ") || t.startsWith("sub ")) {
        var j = i + 1
        val body = new StringBuilder
        while (j < lines.length && !{ val e = lines(j).trim; e.startsWith("end function") || e.startsWith("end sub") }) {
          body.append(lines(j)).append("\n"); j += 1
        }
        val b = body.toString
        if (b.contains("ga.") && !b.contains("ga = GetGlobalAA()"))
          sb.append("  ga = GetGlobalAA()\n")
      }
      i += 1
    }
    if (sb.nonEmpty && sb.last == '\n') sb.deleteCharAt(sb.length - 1)
    sb.toString
  }

  private def write(rel: String, content: String): Unit = {
    val p = outDir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.write(p, content.getBytes(StandardCharsets.UTF_8))
  }

  // ── channel templates ─────────────────────────────────────────────────────
  // The launch keys a certified channel needs: a deep link taken while the channel is already running
  // (supports_input_launch, an roInputEvent), and Instant Resume (sdk_instant_resume with run_as_process), which keeps
  // the channel in memory when the viewer goes to another and calls the scene's resume handler when they come back:
  // the one way a channel hears that it is in front again. The version is RokuChannel's to set from the release.
  private def manifest(title: String): String =
    s"""# Generated by everyui's Roku compiler
       |title=$title
       |major_version=1
       |minor_version=0
       |build_version=1
       |ui_resolutions=fhd
       |rsg_version=1.3
       |splash_color=#FFFFFF
       |splash_min_time=0
       |supports_input_launch=1
       |sdk_instant_resume=1
       |run_as_process=1
       |""".stripMargin

  // The launch parameters (a deep link's contentId and mediaType, or anything else ECP's /launch was given, over the
  // `pkg:/<name>.txt` files a test sideload adds, which only this thread may list) go on the global node BEFORE the
  // scene is made, since the scene's init runs the app, which reads them as it starts. A deep link into the running
  // channel comes as an roInputEvent, held for as long as the loop runs (an roInput let go stops them coming), and is
  // handed to the scene.
  private val mainBrs: String =
    """' Generated by everyui's Roku compiler — channel entry point.
      |sub Main(args as dynamic)
      |    screen = CreateObject("roSGScreen")
      |    port = CreateObject("roMessagePort")
      |    screen.setMessagePort(port)
      |    launch = {}
      |    fs = CreateObject("roFileSystem")
      |    for each f in fs.GetDirectoryListing("pkg:/")
      |        if LCase(Right(f, 4)) = ".txt" then launch[Left(f, Len(f) - 4)] = ReadAsciiFile("pkg:/" + f)
      |    end for
      |    if type(args) = "roAssociativeArray" then launch.Append(args)
      |    screen.getGlobalNode().addFields({ launchArgs: launch })
      |    scene = screen.CreateScene("AppScene")
      |    screen.show()
      |    input = CreateObject("roInput")
      |    input.setMessagePort(port)
      |    ' how much of its memory the channel is using, said on the console every half minute and when the box warns
      |    ' of it: a channel over its limit is closed by the box with nothing but EXIT_CHANNEL_MEM_LIMIT_FG to show for it
      |    memory = CreateObject("roAppMemoryMonitor")
      |    memory.setMessagePort(port)
      |    memory.EnableMemoryWarningEvent(true)
      |    print "[memory] limit "; memory.GetChannelMemoryLimit(); " KB"
      |    while true
      |        msg = wait(30000, port)
      |        kind = type(msg)
      |        if msg = invalid
      |            print "[memory] "; memory.GetMemoryLimitPercent(); "% of the limit, "; memory.GetChannelAvailableMemory(); " KB free"
      |        else if kind = "roAppMemoryNotificationEvent"
      |            print "[memory] WARNING "; FormatJson(msg.GetInfo())
      |        else if kind = "roSGScreenEvent"
      |            if msg.isScreenClosed() then return
      |        else if kind = "roInputEvent"
      |            if msg.isInput()
      |                info = msg.getInfo()
      |                if info <> invalid then scene.inputArgs = info
      |            end if
      |        end if
      |    end while
      |end sub
      |""".stripMargin

  private val sceneXml: String =
    """<?xml version="1.0" encoding="utf-8" ?>
      |<!-- Generated by everyui's Roku compiler — root scene. -->
      |<component name="AppScene" extends="Scene">
      |  <interface>
      |    <field id="inputArgs" type="assocarray" alwaysNotify="true" onChange="onInputArgs" />
      |  </interface>
      |  <customization suspendhandler="onSuspend" />
      |  <customization resumehandler="onResume" />
      |  <script type="text/brightscript" uri="pkg:/components/AppScene.brs" />
      |  <script type="text/brightscript" uri="pkg:/components/ScalaRuntime.brs" />
      |  <script type="text/brightscript" uri="pkg:/components/App.brs" />
      |  <children>
      |    <Rectangle id="bg" width="1920" height="1080" color="0xFFFFFFFF" />
      |    <Group id="root" />
      |  </children>
      |</component>
      |""".stripMargin

  private val sceneBrs: String =
    """' Generated by everyui's Roku compiler.
      |sub init()
      |    ga = GetGlobalAA()
      |    ga.appScene = m.top
      |    ga.appRoot = m.top.findNode("root")
      |    ga.uiLaunchParams = m.global.launchArgs
      |    m.top.setFocus(true)
      |    rtMain()
      |    RtAfterTurn()
      |end sub
      |
      |function onKeyEvent(key as string, press as boolean) as boolean
      |    return RtKeyEvent(key, press)
      |end function
      |
      |' A deep link into the running channel (Main's roInputEvent)
      |sub onInputArgs()
      |    uiLaunched(m.top.inputArgs)
      |    RtAfterTurn()
      |end sub
      |
      |' Instant Resume: the viewer went to another channel (or the device's screensaver came up), and came back
      |sub onSuspend(_args as dynamic)
      |    uiSuspended()
      |end sub
      |
      |sub onResume(args as dynamic)
      |    uiResumed(args)
      |    RtAfterTurn()
      |end sub
      |""".stripMargin

  private val appBrsHeader: String =
    "' Generated by everyui's Roku compiler — the translated example app.\n\n"

  /** Every function the runtime defines, lower-cased (BrightScript names are case-insensitive). A local or a parameter
    * named like one (`set` beside `Set`, `failure` beside `Failure`) shadows it in that function, which brighterscript
    * refuses (BS1104), so such a local is written with a trailing `_` (see `brLocal`). The platform runtime
    * (UniversalRuntime.brs) is copied in after the emitter runs, and its functions are all `ui`/`_`/`Rt` names no
    * Scala local takes. */
  private lazy val runtimeFnNames: Set[String] =
    "(?im)^\\s*(?:function|sub)\\s+([A-Za-z_][A-Za-z0-9_]*)".r
      .findAllMatchIn(runtimeBrs + "\n" + shimsBrs + collectionShimsBrs).map(_.group(1).toLowerCase).toSet

  /** The core of the Scala runtime: string conversion, collection, String,
   *  Option and Try/Either operations, closure invocation (`RtInvoke`) and the
   *  `RtTodo` placeholder. The object model and stdlib shims are `shimsBrs`. */
  private val runtimeBrs: String =
    """' everyui Roku runtime (generated by the Roku compiler).
      |
      |' ── misc helpers ──
      |' AppScene's onKeyEvent: the remote's keys, by the web's names, to the hook uiOnKey set (everyui.focus.Focus).
      |' Back goes to the hook as well, and is the Roku's (leaving the channel) only when the app does not take it.
      |function RtKeyEvent(key as string, press as boolean) as boolean
      |    ga = GetGlobalAA()
      |    if ga.uiKey = invalid then return false
      |    names = { back: "Back", up: "ArrowUp", down: "ArrowDown", left: "ArrowLeft", right: "ArrowRight", OK: "Enter", play: "MediaPlayPause", rewind: "MediaRewind", fastforward: "MediaFastForward", replay: "MediaReplay", options: "ContextMenu" }
      |    name = names[key]
      |    if name = invalid then name = key
      |    taken = RtGuard(ga.uiKey, [name, press])
      |    return taken = true
      |end function
      |function _str(v as dynamic) as string
      |    if v = invalid then return ""
      |    if type(v) = "roString" or type(v) = "String" then return v
      |    k = _rtKind(v)
      |    if k = "aa" then return _rtObjStr(v)
      |    if k = "arr" then return _rtArrStr(v)
      |    if k = "fn" then return "<function>"
      |    return v.toStr()
      |end function
      |function _rtIsStr(v as dynamic) as boolean
      |    t = type(v)
      |    return t = "roString" or t = "String"
      |end function
      |function _iif(c as dynamic, a as dynamic, b as dynamic) as dynamic
      |    if c then return a
      |    return b
      |end function
      |function _append(xs as object, x as dynamic) as object
      |    r = []
      |    if xs <> invalid
      |        for each e in xs
      |            r.push(e)
      |        end for
      |    end if
      |    r.push(x)
      |    return r
      |end function
      |function _prepend(x as dynamic, xs as object) as object
      |    r = [x]
      |    if xs <> invalid
      |        for each e in xs
      |            r.push(e)
      |        end for
      |    end if
      |    return r
      |end function
      |function _concat(xs as object, ys as object) as object
      |    r = []
      |    if xs <> invalid
      |        for each e in xs
      |            r.push(e)
      |        end for
      |    end if
      |    if ys <> invalid
      |        for each e in ys
      |            r.push(e)
      |        end for
      |    end if
      |    return r
      |end function
      |' Scala's println → BrightScript print (a statement, wrapped as a function
      |' so emitted call sites `println(x)` work).
      |function println(s as dynamic) as dynamic
      |    print s
      |    return invalid
      |end function
      |' ── collection ops (roArray has no map/filter/…; these are the runtime) ──
      |' NOTE: BrightScript has no single-line for loops — every for..end for spans
      |' multiple lines, and a block `if` never uses a trailing `then`.
      |' The elements to loop over by index. BrightScript's for each keeps its place in the object it walks, so a loop
      |' whose callback walks the same array (a reaction reading a container's children while its layout places them)
      |' ends the outer loop early; anything that is not an array is copied first.
      |function _rtItems(xs as dynamic) as object
      |    if type(xs) = "roArray" then return xs
      |    r = []
      |    ' a String walked as a collection (StringOps: exists, forall, count, foreach…) is its characters
      |    if _rtIsStr(xs)
      |        for i = 1 to Len(xs)
      |            r.push(Mid(xs, i, 1))
      |        end for
      |        return r
      |    end if
      |    if xs <> invalid
      |        for each e in xs
      |            r.push(e)
      |        end for
      |    end if
      |    return r
      |end function
      |function _arrMap(xs as object, cl as dynamic) as object
      |    r = []
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            e = items[ii]
      |            r.push(RtInvoke(cl, [e]))
      |        end for
      |    end if
      |    return r
      |end function
      |function _arrFlatMap(xs as object, cl as dynamic) as object
      |    r = []
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            e = items[ii]
      |            sublist = RtInvoke(cl, [e])
      |            if sublist <> invalid and GetInterface(sublist, "ifArray") <> invalid
      |                for each s in sublist
      |                    r.push(s)
      |                end for
      |            else if sublist <> invalid
      |                r.push(sublist)
      |            end if
      |        end for
      |    end if
      |    return r
      |end function
      |function _arrFilter(xs as object, cl as dynamic) as object
      |    r = []
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            e = items[ii]
      |            if RtInvoke(cl, [e]) then r.push(e)
      |        end for
      |    end if
      |    return r
      |end function
      |function _arrFilterNot(xs as object, cl as dynamic) as object
      |    r = []
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            e = items[ii]
      |            if not RtInvoke(cl, [e]) then r.push(e)
      |        end for
      |    end if
      |    return r
      |end function
      |sub _arrForeach(xs as object, cl as dynamic)
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            RtInvoke(cl, [items[ii]])
      |        end for
      |    end if
      |end sub
      |function _arrExists(xs as object, cl as dynamic) as boolean
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            e = items[ii]
      |            if RtInvoke(cl, [e]) then return true
      |        end for
      |    end if
      |    return false
      |end function
      |function _arrForall(xs as object, cl as dynamic) as boolean
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            e = items[ii]
      |            if not RtInvoke(cl, [e]) then return false
      |        end for
      |    end if
      |    return true
      |end function
      |function _arrFind(xs as object, cl as dynamic) as dynamic
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            e = items[ii]
      |            if RtInvoke(cl, [e]) then return e
      |        end for
      |    end if
      |    return invalid
      |end function
      |function _arrSet(xs as object, i as dynamic, v as dynamic) as dynamic
      |    xs[i] = v
      |    return invalid
      |end function
      |function _arrCount(xs as object, cl as dynamic) as integer
      |    n = 0
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            if RtInvoke(cl, [items[ii]]) then n = n + 1
      |        end for
      |    end if
      |    return n
      |end function
      |function _arrIndexWhere(xs as object, cl as dynamic) as integer
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            if RtInvoke(cl, [items[ii]]) then return ii
      |        end for
      |    end if
      |    return -1
      |end function
      |function _contains(xs as object, x as dynamic) as boolean
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            e = items[ii]
      |            if _eq(e, x) then return true
      |        end for
      |    end if
      |    return false
      |end function
      |function _mkString(xs as object, sep as dynamic) as string
      |    s = "" : first = true
      |    if xs <> invalid
      |        for each e in xs
      |            if first then first = false else s = s + _str(sep)
      |            s = s + _str(e)
      |        end for
      |    end if
      |    return s
      |end function
      |' a String's take and drop (StringOps) come here too, and answer with a String
      |function _take(xs as dynamic, n as integer) as dynamic
      |    if _rtIsStr(xs) then return Left(xs, _iif(n < 0, 0, n))
      |    r = [] : i = 0
      |    if xs <> invalid
      |        for each e in xs
      |            if i < n then r.push(e)
      |            i = i + 1
      |        end for
      |    end if
      |    return r
      |end function
      |function _slice(xs as object, from as integer, until as integer) as object
      |    r = [] : i = 0
      |    if xs <> invalid
      |        for each e in xs
      |            if i >= from and i < until then r.push(e)
      |            i = i + 1
      |        end for
      |    end if
      |    return r
      |end function
      |' xs.patch(from, other, replaced): xs up to `from`, then other, then xs after the `replaced` elements from there
      |function _patch(xs as object, from as integer, other as dynamic, replaced as integer) as object
      |    r = [] : i = 0 : put = false
      |    if from < 0 then from = 0
      |    if xs <> invalid
      |        for each e in xs
      |            if i = from and not put
      |                r.Append(_rtItems(other))
      |                put = true
      |            end if
      |            if i < from or i >= from + replaced then r.push(e)
      |            i = i + 1
      |        end for
      |    end if
      |    if not put then r.Append(_rtItems(other))
      |    return r
      |end function
      |' xs.flatten: each element that is a collection given up for its elements, an empty Option (invalid) left out
      |function _flatten(xs as dynamic) as object
      |    r = []
      |    if xs <> invalid
      |        for each e in xs
      |            if type(e) = "roArray"
      |                r.Append(e)
      |            else if e <> invalid
      |                r.push(e)
      |            end if
      |        end for
      |    end if
      |    return r
      |end function
      |function _dropRight(xs as dynamic, n as integer) as dynamic
      |    if _rtIsStr(xs) then return Left(xs, _iif(Len(xs) - n < 0, 0, Len(xs) - n))
      |    items = _rtItems(xs)
      |    keep = items.count() - n
      |    r = []
      |    for i = 0 to keep - 1
      |        r.push(items[i])
      |    end for
      |    return r
      |end function
      |' `keeping` true takes the run that satisfies the test, false drops it and keeps the rest.
      |function _takeWhile(xs as object, cl as dynamic, keeping as boolean) as object
      |    items = _rtItems(xs)
      |    at = items.count()
      |    for i = 0 to items.count() - 1
      |        if not RtInvoke(cl, [items[i]])
      |            at = i
      |            exit for
      |        end if
      |    end for
      |    r = []
      |    if keeping
      |        for i = 0 to at - 1
      |            r.push(items[i])
      |        end for
      |    else
      |        for i = at to items.count() - 1
      |            r.push(items[i])
      |        end for
      |    end if
      |    return r
      |end function
      |function _arrLastIndexWhere(xs as object, cl as dynamic) as integer
      |    items = _rtItems(xs)
      |    found = -1
      |    for i = 0 to items.count() - 1
      |        if RtInvoke(cl, [items[i]]) then found = i
      |    end for
      |    return found
      |end function
      |' The least (or greatest) of them, or invalid where there are none: Option is the value itself here.
      |function _minOption(xs as object, least as boolean) as dynamic
      |    items = _rtItems(xs)
      |    if items.count() = 0 then return invalid
      |    if least then return _minList(items)
      |    return _maxList(items)
      |end function
      |function _strTakeWhile(s as dynamic, cl as dynamic, keeping as boolean) as string
      |    if s = invalid then return ""
      |    at = Len(s)
      |    for i = 1 to Len(s)
      |        if not RtInvoke(cl, [Mid(s, i, 1)])
      |            at = i - 1
      |            exit for
      |        end if
      |    end for
      |    if keeping then return Left(s, at)
      |    return Mid(s, at + 1)
      |end function
      |function _strCapitalize(s as dynamic) as string
      |    if s = invalid or Len(s) = 0 then return ""
      |    return UCase(Left(s, 1)) + Mid(s, 2)
      |end function
      |function _drop(xs as dynamic, n as integer) as dynamic
      |    if _rtIsStr(xs) then return Mid(xs, _iif(n < 0, 0, n) + 1)
      |    r = [] : i = 0
      |    if xs <> invalid
      |        for each e in xs
      |            if i >= n then r.push(e)
      |            i = i + 1
      |        end for
      |    end if
      |    return r
      |end function
      |function _sortBy(xs as object, cl as dynamic) as object
      |    ' decorate-sort-undecorate; keys must be sortable (num/string)
      |    keyed = []
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            e = items[ii]
      |            keyed.push({ k: RtInvoke(cl, [e]), v: e })
      |        end for
      |    end if
      |    n = keyed.count()
      |    if n > 1
      |        for i = 0 to n - 2
      |            for j = 0 to n - 2 - i
      |                if keyed[j].k > keyed[j + 1].k
      |                    tmp = keyed[j] : keyed[j] = keyed[j + 1] : keyed[j + 1] = tmp
      |                end if
      |            end for
      |        end for
      |    end if
      |    r = []
      |    for each e in keyed
      |        r.push(e.v)
      |    end for
      |    return r
      |end function
      |function _last(xs as object) as dynamic
      |    if xs = invalid or xs.count() = 0 then return invalid
      |    return xs[xs.count() - 1]
      |end function
      |function _headOption(xs as object) as dynamic
      |    if xs = invalid or xs.count() = 0 then return invalid
      |    return xs[0]
      |end function
      |function _lastOption(xs as object) as dynamic
      |    return _last(xs)
      |end function
      |function _reverse(xs as object) as object
      |    r = []
      |    if xs <> invalid
      |        for i = xs.count() - 1 to 0 step -1
      |            r.push(xs[i])
      |        end for
      |    end if
      |    return r
      |end function
      |function _distinct(xs as object) as object
      |    r = []
      |    if xs <> invalid
      |        for each e in xs
      |            if not _contains(r, e) then r.push(e)
      |        end for
      |    end if
      |    return r
      |end function
      |function _sum(xs as object) as dynamic
      |    t = 0
      |    if xs <> invalid
      |        for each e in xs
      |            t = t + e
      |        end for
      |    end if
      |    return t
      |end function
      |function _minList(xs as object) as dynamic
      |    if xs = invalid or xs.count() = 0 then return invalid
      |    mv = xs[0]
      |    for each e in xs
      |        if e < mv then mv = e
      |    end for
      |    return mv
      |end function
      |function _maxList(xs as object) as dynamic
      |    if xs = invalid or xs.count() = 0 then return invalid
      |    mv = xs[0]
      |    for each e in xs
      |        if e > mv then mv = e
      |    end for
      |    return mv
      |end function
      |function _arrFoldLeft(xs as object, z as dynamic, cl as dynamic) as dynamic
      |    acc = z
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            acc = RtInvoke(cl, [acc, items[ii]])
      |        end for
      |    end if
      |    return acc
      |end function
      |function _zip(xs as object, ys as object) as object
      |    r = []
      |    if xs <> invalid and ys <> invalid
      |        a = _rtItems(xs) : b = _rtItems(ys)
      |        n = a.count()
      |        if b.count() < n then n = b.count()
      |        for ii = 0 to n - 1
      |            r.push({ _1: a[ii], _2: b[ii] })
      |        end for
      |    end if
      |    return r
      |end function
      |function _zipWithIndex(xs as object) as object
      |    r = [] : i = 0
      |    if xs <> invalid
      |        for each e in xs
      |            r.push({ _1: e, _2: i })
      |            i = i + 1
      |        end for
      |    end if
      |    return r
      |end function
      |function _until(a as integer, b as integer) as object
      |    r = []
      |    for i = a to b - 1
      |        r.push(i)
      |    end for
      |    return r
      |end function
      |function _toIncl(a as integer, b as integer) as object
      |    r = []
      |    for i = a to b
      |        r.push(i)
      |    end for
      |    return r
      |end function
      |' ── String helpers (Scala String methods → BrightScript; Mid() is 1-indexed) ──
      |function _substr(s as dynamic, a as integer, b as integer) as string
      |    return Mid(_str(s), a + 1, b - a)
      |end function
      |function _substr1(s as dynamic, a as integer) as string
      |    return Mid(_str(s), a + 1)
      |end function
      |function _trim(s as dynamic) as string
      |    t = _str(s)
      |    return t.Trim()
      |end function
      |' a Char separator arrives as its code
      |' s.split(sep) as Java splits on a plain separator: an empty piece between two separators or before the first is
      |' kept, the empty ones at the end are dropped, and a string with no separator in it is itself. (Tokenize dropped
      |' every empty piece and split on each character of the separator: "?q=1".split('?')(0) came out "q=1", and "" gave
      |' nothing at all to take the first of.)
      |function _split(s as dynamic, sep as dynamic) as object
      |    t = _str(s)
      |    if _rtKind(sep) = "n" then sep = Chr(sep)
      |    d = _str(sep)
      |    if d = "" then return t.Split("")
      |    if t = "" then return [""]
      |    r = []
      |    n = Len(d)
      |    start = 1
      |    i = Instr(start, t, d)
      |    while i > 0
      |        r.Push(Mid(t, start, i - start))
      |        start = i + n
      |        i = Instr(start, t, d)
      |    end while
      |    r.Push(Mid(t, start))
      |    while r.Count() > 0 and r[r.Count() - 1] = ""
      |        r.Pop()
      |    end while
      |    return r
      |end function
      |' s.contains(sub), or a Char
      |function _strContains(s as dynamic, x as dynamic) as boolean
      |    return _strIndexOf(s, x) >= 0
      |end function
      |' s.indexOf(sub), or of a Char (its code, as Java's indexOf(int) takes)
      |function _strIndexOf(s as dynamic, x as dynamic) as integer
      |    if _rtKind(x) = "n" then x = Chr(x)
      |    return Instr(1, _str(s), _str(x)) - 1
      |end function
      |' s.indexOf(x, from): from `from` on (at the start for a negative one), -1 where it is not there
      |function _strIndexFrom(s as dynamic, x as dynamic, from as integer) as integer
      |    if _rtKind(x) = "n" then x = Chr(x)
      |    t = _str(s)
      |    if from < 0 then from = 0
      |    if from >= Len(t) then return -1
      |    return Instr(from + 1, t, _str(x)) - 1
      |end function
      |function _strFilter(s as dynamic, cl as dynamic, keep as boolean) as string
      |    t = _str(s) : r = ""
      |    for i = 1 to Len(t)
      |        c = Mid(t, i, 1)
      |        if RtInvoke(cl, [c]) = keep then r = r + c
      |    end for
      |    return r
      |end function
      |function _strLastIndexOf(s as dynamic, x as dynamic) as integer
      |    if _rtKind(x) = "n" then x = Chr(x)
      |    t = _str(s) : f = _str(x)
      |    if f = "" then return Len(t)
      |    at = -1 : i = Instr(1, t, f)
      |    while i > 0
      |        at = i - 1
      |        i = Instr(i + 1, t, f)
      |    end while
      |    return at
      |end function
      |function _startsWith(s as dynamic, pre as dynamic) as boolean
      |    a = _str(s) : b = _str(pre)
      |    if Len(b) > Len(a) then return false
      |    return Left(a, Len(b)) = b
      |end function
      |function _endsWith(s as dynamic, suf as dynamic) as boolean
      |    a = _str(s) : b = _str(suf)
      |    if Len(b) > Len(a) then return false
      |    return Right(a, Len(b)) = b
      |end function
      |function _stripPrefix(s as dynamic, pre as dynamic) as string
      |    a = _str(s)
      |    if _startsWith(a, pre) then return Mid(a, Len(_str(pre)) + 1)
      |    return a
      |end function
      |function _stripSuffix(s as dynamic, suf as dynamic) as string
      |    a = _str(s)
      |    if _endsWith(a, suf) then return Left(a, Len(a) - Len(_str(suf)))
      |    return a
      |end function
      |' String.compareTo: a three-way lexicographic compare (roString has no such member); < 0, 0, > 0
      |function _strCompare(s as dynamic, o as dynamic) as integer
      |    a = _str(s) : b = _str(o)
      |    if a < b then return -1
      |    if a > b then return 1
      |    return 0
      |end function
      |' xs.indexOf(x): the first index of an equal element, or -1
      |function _arrIndexOf(xs as object, x as dynamic) as integer
      |    i = 0
      |    if xs <> invalid
      |        items = _rtItems(xs)
      |        for ii = 0 to items.count() - 1
      |            e = items[ii]
      |            if _eq(e, x) then return i
      |            i = i + 1
      |        end for
      |    end if
      |    return -1
      |end function
      |' ── String → number, as Scala reads one: the whole string or nothing (the Option forms give invalid) ──
      |function _strToIntOpt(s as dynamic) as dynamic
      |    t = _str(s).Trim()
      |    if not CreateObject("roRegex", "^[+-]?[0-9]+$", "").IsMatch(t) then return invalid
      |    return t.ToInt()
      |end function
      |function _strToDoubleOpt(s as dynamic) as dynamic
      |    t = _str(s).Trim()
      |    if not CreateObject("roRegex", "^[+-]?([0-9]+\.?[0-9]*|\.[0-9]+)([eE][+-]?[0-9]+)?$", "").IsMatch(t) then return invalid
      |    return Cdbl(Val(t))
      |end function
      |function _strToInt(s as dynamic) as dynamic
      |    n = _strToIntOpt(s)
      |    if n = invalid then RtThrow({ _cls: "NumberFormatException", message: "For input string: " + Chr(34) + _str(s) + Chr(34) })
      |    return n
      |end function
      |function _strToDouble(s as dynamic) as dynamic
      |    n = _strToDoubleOpt(s)
      |    if n = invalid then RtThrow({ _cls: "NumberFormatException", message: "For input string: " + Chr(34) + _str(s) + Chr(34) })
      |    return n
      |end function
      |' ── dividing, where the divisor may be zero ──
      |' BrightScript stops the thread on a division by zero, wherever it happens; the JVM throws on whole numbers
      |' and answers with an infinity or a NaN on fractions. Whole numbers throw the same here, so the fault can be
      |' caught and read. A fraction cannot: BrightScript has no way to write an infinity down, so the answer is the
      |' largest number it can hold, with the sign the IEEE one would have (and zero for 0/0). That is a divergence,
      |' and a deliberate one: the arithmetic that follows (a `min`, a clamp) then lands where infinity would put it,
      |' rather than taking the channel down.
      |function _quot(a as dynamic, b as dynamic) as dynamic
      |    if b = 0 then RtThrow({ _cls: "ArithmeticException", message: "/ by zero" })
      |    return a \ b
      |end function
      |function _rem(a as dynamic, b as dynamic) as dynamic
      |    if b = 0 then RtThrow({ _cls: "ArithmeticException", message: "/ by zero" })
      |    return a mod b
      |end function
      |function _div(a as dynamic, b as dynamic) as dynamic
      |    if b <> 0 then return a / b
      |    if a = 0 then return 0.0
      |    if a > 0 then return 3.4e38
      |    return -3.4e38
      |end function
      |function _replace(s as dynamic, a as dynamic, b as dynamic) as string
      |    t = _str(s)
      |    return t.Replace(_str(a), _str(b))
      |end function
      |' ── Option ops (Option is a plain nullable: Some(x)→x, None→invalid) ──
      |function _optMap(o as dynamic, cl as dynamic) as dynamic
      |    if o = invalid then return invalid
      |    return RtInvoke(cl, [o])
      |end function
      |sub _optForeach(o as dynamic, cl as dynamic)
      |    if o <> invalid then RtInvoke(cl, [o])
      |end sub
      |function _getOrElse(o as dynamic, default as dynamic) as dynamic
      |    if o = invalid then return default
      |    return o
      |end function
      |function _optFilter(o as dynamic, cl as dynamic) as dynamic
      |    if o = invalid then return invalid
      |    if RtInvoke(cl, [o]) then return o
      |    return invalid
      |end function
      |function _optFilterNot(o as dynamic, cl as dynamic) as dynamic
      |    if o = invalid then return invalid
      |    if RtInvoke(cl, [o]) then return invalid
      |    return o
      |end function
      |function _optForall(o as dynamic, cl as dynamic) as boolean
      |    if o = invalid then return true
      |    return RtInvoke(cl, [o])
      |end function
      |function _optExists(o as dynamic, cl as dynamic) as boolean
      |    if o = invalid then return false
      |    return RtInvoke(cl, [o])
      |end function
      |function _optFold(o as dynamic, ifEmpty as dynamic, cl as dynamic) as dynamic
      |    if o = invalid then return ifEmpty
      |    return RtInvoke(cl, [o])
      |end function
      |' ── Try / Either ops ({_cls:"Success",value}/{_cls:"Failure",error};
      |'    {_cls:"Right",value}/{_cls:"Left",value}) ──
      |function _tryMap(t as dynamic, cl as dynamic) as dynamic
      |    if t <> invalid and (t._cls = "Success" or t._cls = "Right")
      |        return { _cls: t._cls, value: RtInvoke(cl, [t.value]) }
      |    end if
      |    return t
      |end function
      |function _tryFlatMap(t as dynamic, cl as dynamic) as dynamic
      |    if t <> invalid and (t._cls = "Success" or t._cls = "Right")
      |        return RtInvoke(cl, [t.value])
      |    end if
      |    return t
      |end function
      |function _tryFold(t as dynamic, fa as dynamic, fb as dynamic) as dynamic
      |    if t <> invalid and (t._cls = "Failure" or t._cls = "Left")
      |        arg = t.error
      |        if arg = invalid then arg = t.value
      |        return RtInvoke(fa, [arg])
      |    end if
      |    v = invalid
      |    if t <> invalid then v = t.value
      |    return RtInvoke(fb, [v])
      |end function
      |function _tryGetOrElse(t as dynamic, default as dynamic) as dynamic
      |    if t <> invalid and (t._cls = "Success" or t._cls = "Right") then return t.value
      |    return default
      |end function
      |function _tryToOption(t as dynamic) as dynamic
      |    if t <> invalid and (t._cls = "Success" or t._cls = "Right") then return t.value
      |    return invalid
      |end function
      |function RtInvoke(cl as dynamic, args as object) as dynamic
      |    if type(cl) = "roAssociativeArray"
      |        if cl.fn <> invalid then return cl.fn(cl.env, args)
      |        return invalid
      |    end if
      |    if cl <> invalid and GetInterface(cl, "ifAssociativeArray") <> invalid and cl.fn <> invalid
      |        return cl.fn(cl.env, args)
      |    end if
      |    return invalid
      |end function
      |function _node(nodeType as string) as object
      |    return CreateObject("roSGNode", nodeType)
      |end function
      |function RtTodo(label as string) as object
      |    n = _node("Label")
      |    n.text = "[todo: " + label + "]"
      |    n.color = "0x49454FFF"
      |    return n
      |end function
      |' A gap standing in a value position, answering in the shape its caller expects rather than with a node: an
      |' empty list can be iterated, an empty string can be joined, and None is what an Option already is here. Each
      |' says so once on the console, so a method nothing translates is findable on the device as well as in the build.
      |function RtTodoSay(label as string) as void
      |    ga = GetGlobalAA()
      |    if ga.rtTodoSaid = invalid then ga.rtTodoSaid = {}
      |    if ga.rtTodoSaid[label] = invalid
      |        ga.rtTodoSaid[label] = true
      |        print "[todo] no translation for "; label
      |    end if
      |end function
      |function RtTodoList(label as string) as object
      |    RtTodoSay(label)
      |    return []
      |end function
      |function RtTodoStr(label as string) as string
      |    RtTodoSay(label)
      |    return ""
      |end function
      |function RtTodoOpt(label as string) as dynamic
      |    RtTodoSay(label)
      |    return invalid
      |end function
      |' A call into a type nothing constructs (the emitter's reachability analysis): it cannot run.
      |function RtDead(what as string) as dynamic
      |    RtThrow("unreachable: " + what)
      |    return invalid
      |end function
      |
      |' ── Scala stdlib collections + synthetics (best-effort; keep the channel loading) ──
      |function Map(o = invalid as dynamic) as object : return _iif(o = invalid, {}, o) : end function
      |function Set(o = invalid as dynamic) as object : return _iif(o = invalid, [], o) : end function
      |function Tuple2(a = invalid as dynamic, b = invalid as dynamic) as object : return { _1: a, _2: b } : end function
      |function Tuple3(a = invalid as dynamic, b = invalid as dynamic, c = invalid as dynamic) as object : return { _1: a, _2: b, _3: c } : end function
      |function Tuple4(a = invalid as dynamic, b = invalid as dynamic, c = invalid as dynamic, d = invalid as dynamic) as object : return { _1: a, _2: b, _3: c, _4: d } : end function
      |function wrapRefArray(a = invalid as dynamic) as dynamic : return a : end function
      |function wrapIntArray(a = invalid as dynamic) as dynamic : return a : end function
      |function wrapLongArray(a = invalid as dynamic) as dynamic : return a : end function
      |function wrapDoubleArray(a = invalid as dynamic) as dynamic : return a : end function
      |function wrapBooleanArray(a = invalid as dynamic) as dynamic : return a : end function
      |function intWrapper(x = invalid as dynamic) as dynamic : return x : end function
      |' -- Scala stdlib shims added for everyui's component model --
      |function RtMin(a as dynamic, b as dynamic) as dynamic
      |    if a < b then return a
      |    return b
      |end function
      |function RtMax(a as dynamic, b as dynamic) as dynamic
      |    if a > b then return a
      |    return b
      |end function
      |' scala.concurrent.duration.FiniteDuration, in milliseconds
      |function RtDuration(ms as dynamic) as object
      |    return { _cls: "FiniteDuration", _fields: ["ms"], ms: ms }
      |end function
      |function RtDurationMs(d as dynamic) as dynamic
      |    if d = invalid then return 0
      |    return d.ms
      |end function
      |' StringContext#s: parts interleaved with the arguments
      |function _strInterp(parts as object, args as object) as string
      |    r = ""
      |    for i = 0 to parts.count() - 1
      |        r = r + _str(parts[i])
      |        if i < args.count() then r = r + _str(args[i])
      |    end for
      |    return r
      |end function
      |' option.toList: nothing, or the one value
      |function _optToList(o as dynamic) as object
      |    if o = invalid then return []
      |    return [o]
      |end function
      |' A Map again from the pairs a map was walked into: a keyed assoc array when every key is a String, which a table of
      |' thousands (an app's messages) is built into at once, where the entry list copies itself for every pair it takes
      |function _mapOfPairs(pairs as object) as object
      |    keyed = true
      |    for each t in pairs
      |        if not _rtIsStr(t._1) then keyed = false
      |    end for
      |    if not keyed then return _mapFromPairs(pairs)
      |    m = {}
      |    m.SetModeCaseSensitive()
      |    for each t in pairs
      |        m[t._1] = t._2
      |    end for
      |    return m
      |end function
      |' Map(k -> v, ...) and map.toList
      |function _mapFromPairs(pairs as object) as object
      |    m = _mapEmpty()
      |    for each t in pairs
      |        m = _mapPut(m, t._1, t._2)
      |    end for
      |    return m
      |end function
      |function _mapToList(m as dynamic) as object
      |    r = []
      |    if m = invalid then return r
      |    if _rtIsEntryMap(m)
      |        for each e in m.entries
      |            r.push(Tuple2(e.k, e.v))
      |        end for
      |    else
      |        for each k in m
      |            r.push(Tuple2(k, m[k]))
      |        end for
      |    end if
      |    return r
      |end function
""".stripMargin

  /** Runtime support for emitting GENERAL Scala libraries (reactify, unmodified):
   *  identity and structural equality, virtual dispatch, real exceptions,
   *  by-name forcing, and Scala stdlib shims (Option, Try, Set, object-keyed
   *  Map, ThreadLocal, AtomicBoolean, Promise). A separate constant from
   *  `runtimeBrs` because one JVM string constant is capped at 65,535 bytes. */
  private val shimsBrs: String =
    """
      |' ── object model, equality, and Scala stdlib shims ──
      |'
      |' Additions for emitting general Scala libraries (reactify, unmodified).
      |' Everything here follows the emitted object model: an instance is an assoc
      |' array carrying `_cls`, its vtable slots, and its fields; a method takes the
      |' receiver as an explicit first `self` argument.
      |
      |' ── identity and equality ──────────────────────────────────────────────────
      |
      |function RtNextId() as integer
      |    ga = GetGlobalAA()
      |    if ga.__rtIdSeq = invalid then ga.__rtIdSeq = 0
      |    ga.__rtIdSeq = ga.__rtIdSeq + 1
      |    return ga.__rtIdSeq
      |end function
      |
      |' A coarse type family, so `=` is only ever applied to comparable operands.
      |function _rtKind(v as dynamic) as string
      |    ' Fast path on the type names seen on every call, then the interface test
      |    ' for anything else: a value read back out of an array or assoc array can be
      |    ' boxed and report a different name (roInteger), and every numeric family
      |    ' answers ifInt / ifFloat / ifDouble / ifLongInt whether boxed or not.
      |    t = type(v)
      |    if t = "roAssociativeArray" then return "aa"
      |    if t = "String" or t = "roString" then return "s"
      |    if t = "Integer" or t = "Float" or t = "Double" or t = "LongInteger" then return "n"
      |    if t = "Boolean" then return "b"
      |    if t = "roArray" then return "arr"
      |    if t = "Invalid" then return "nil"
      |    if t = "Function" or t = "roFunction" then return "fn"
      |    if v = invalid then return "nil"
      |    if GetInterface(v, "ifString") <> invalid then return "s"
      |    if GetInterface(v, "ifInt") <> invalid then return "n"
      |    if GetInterface(v, "ifFloat") <> invalid then return "n"
      |    if GetInterface(v, "ifDouble") <> invalid then return "n"
      |    if GetInterface(v, "ifLongInt") <> invalid then return "n"
      |    if GetInterface(v, "ifBoolean") <> invalid then return "b"
      |    if GetInterface(v, "ifAssociativeArray") <> invalid then return "aa"
      |    if GetInterface(v, "ifArray") <> invalid then return "arr"
      |    if GetInterface(v, "ifFunction") <> invalid then return "fn"
      |    return LCase(type(v))
      |end function
      |
      |function _rtHasFn(aa as object) as boolean
      |    for each k in aa
      |        if _rtKind(aa[k]) = "fn" then return true
      |    end for
      |    return false
      |end function
      |
      |' `case x: SomeClass` for a class that is not a case class. Every object this
      |' compiler makes carries `_isa`, its own name and its ancestors' between bars,
      |' so a subclass answers to its parent's name too. Anything with no `_isa` (a
      |' string, a number, a record from the runtime) answers yes, as it did before
      |' there was a test at all: the tag only narrows what it knows about.
      |function RtIsA(v as dynamic, tag as string) as boolean
      |    if v = invalid then return false
      |    if type(v) <> "roAssociativeArray" then return true
      |    s = v._isa
      |    if s = invalid then return true
      |    return Instr(1, s, tag) > 0
      |end function
      |
      |' Reference identity (Scala `eq`). BrightScript has no identity operator for
      |' objects, so an assoc array is stamped with a unique `_id` the first time its
      |' identity is asked for; two references are the same object iff ids match.
      |function RtIdentity(a as dynamic, b as dynamic) as boolean
      |    if type(a) = "roAssociativeArray" and type(b) = "roAssociativeArray"
      |        if a._id = invalid then a._id = RtNextId()
      |        if b._id = invalid then b._id = RtNextId()
      |        return a._id = b._id
      |    end if
      |    ka = _rtKind(a)
      |    kb = _rtKind(b)
      |    if ka = "nil" or kb = "nil" then return ka = kb
      |    if ka <> kb then return false
      |    if ka = "aa"
      |        if a._id = invalid then a._id = RtNextId()
      |        if b._id = invalid then b._id = RtNextId()
      |        return a._id = b._id
      |    end if
      |    if ka = "arr" then return _eq(a, b)
      |    return a = b
      |end function
      |
      |' Scala `==`. Structural for case classes (`_fields`), maps, tuples and plain
      |' data records; element-wise for sequences; by value for primitives; reference
      |' identity for any other object (anything carrying a method slot).
      |function _eq(a as dynamic, b as dynamic) as boolean
      |    ' Fast path: identical primitive types compare directly.
      |    ta = type(a)
      |    ' Instances of classes (and case objects) without case fields are equal only to themselves.
      |    if ta = "roAssociativeArray" and type(b) = "roAssociativeArray"
      |        if a._fields = invalid and b._fields = invalid and (a._cls <> invalid or b._cls <> invalid) then return RtIdentity(a, b)
      |    end if
      |    if ta = type(b)
      |        if ta = "Integer" or ta = "Float" or ta = "Double" or ta = "String" or ta = "Boolean" then return a = b
      |        if ta = "roString" or ta = "roInteger" or ta = "roFloat" or ta = "roDouble" or ta = "roBoolean" then return a = b
      |        if ta = "LongInteger" or ta = "roLongInteger" then return a = b
      |        if ta = "Invalid" then return true
      |    end if
      |    ka = _rtKind(a)
      |    kb = _rtKind(b)
      |    if ka = "nil" or kb = "nil" then return ka = kb
      |    if ka <> kb then return false
      |    if ka = "s" or ka = "n" or ka = "b" or ka = "fn" then return a = b
      |    if ka = "arr"
      |        if a.count() <> b.count() then return false
      |        for i = 0 to a.count() - 1
      |            x = a[i]
      |            y = b[i]
      |            same = false
      |            if type(x) = "roAssociativeArray" and type(y) = "roAssociativeArray"
      |                if x._id <> invalid and y._id <> invalid then same = (x._id = y._id)
      |            end if
      |            if not same and not _eq(x, y) then return false
      |        end for
      |        return true
      |    end if
      |    if ka <> "aa" then return false
      |    if a.DoesExist("_map") and b.DoesExist("_map") then return _mapEq(a, b)
      |    if a._fields <> invalid and b._fields <> invalid
      |        if a._cls <> b._cls then return false
      |        for each f in a._fields
      |            if not _eq(a[f], b[f]) then return false
      |        end for
      |        return true
      |    end if
      |    if _rtHasFn(a) or _rtHasFn(b) then return RtIdentity(a, b)
      |    na = 0
      |    for each k in a
      |        if LCase(k) <> "_id"
      |            na = na + 1
      |            if not b.DoesExist(k) then return false
      |            if not _eq(a[k], b[k]) then return false
      |        end if
      |    end for
      |    nb = 0
      |    for each k in b
      |        if LCase(k) <> "_id" then nb = nb + 1
      |    end for
      |    return na = nb
      |end function
      |
      |' ── dispatch ────────────────────────────────────────────────────────────────
      |' A virtual call whose receiver must not be evaluated twice (`recv.m(recv, …)`
      |' would). The receiver is bound once, then its slot is looked up and invoked.
      |
      |function RtVcall0(r as object, n as string) as dynamic
      |    f = r[n]
      |    return f(r)
      |end function
      |function RtVcall1(r as object, n as string, a1 as dynamic) as dynamic
      |    f = r[n]
      |    return f(r, a1)
      |end function
      |function RtVcall2(r as object, n as string, a1 as dynamic, a2 as dynamic) as dynamic
      |    f = r[n]
      |    return f(r, a1, a2)
      |end function
      |function RtVcall3(r as object, n as string, a1 as dynamic, a2 as dynamic, a3 as dynamic) as dynamic
      |    f = r[n]
      |    return f(r, a1, a2, a3)
      |end function
      |function RtVcall4(r as object, n as string, a1 as dynamic, a2 as dynamic, a3 as dynamic, a4 as dynamic) as dynamic
      |    f = r[n]
      |    return f(r, a1, a2, a3, a4)
      |end function
      |function RtVcall5(r as object, n as string, a1 as dynamic, a2 as dynamic, a3 as dynamic, a4 as dynamic, a5 as dynamic) as dynamic
      |    f = r[n]
      |    return f(r, a1, a2, a3, a4, a5)
      |end function
      |function RtVcall6(r as object, n as string, a1 as dynamic, a2 as dynamic, a3 as dynamic, a4 as dynamic, a5 as dynamic, a6 as dynamic) as dynamic
      |    f = r[n]
      |    return f(r, a1, a2, a3, a4, a5, a6)
      |end function
      |function RtVcall7(r as object, n as string, a1 as dynamic, a2 as dynamic, a3 as dynamic, a4 as dynamic, a5 as dynamic, a6 as dynamic, a7 as dynamic) as dynamic
      |    f = r[n]
      |    return f(r, a1, a2, a3, a4, a5, a6, a7)
      |end function
      |function RtVcall8(r as object, n as string, a1 as dynamic, a2 as dynamic, a3 as dynamic, a4 as dynamic, a5 as dynamic, a6 as dynamic, a7 as dynamic, a8 as dynamic) as dynamic
      |    f = r[n]
      |    return f(r, a1, a2, a3, a4, a5, a6, a7, a8)
      |end function
      |
      |' A by-name argument arrives as a zero-arg closure; force it. A plain value
      |' (a caller that already evaluated) passes through unchanged.
      |function RtForce(x as dynamic) as dynamic
      |    if type(x) = "roAssociativeArray"
      |        if x.fn <> invalid then return x.fn(x.env, [])
      |    end if
      |    return x
      |end function
      |
      |' ── exceptions ─────────────────────────────────────────────────────────────
      |' A real throw. BrightScript throws a string or an assoc array with `message`;
      |' the Scala exception object is thrown as-is so a `catch` can match its `_cls`.
      |
      |sub RtThrow(e as dynamic)
      |    if _rtKind(e) <> "aa" then e = { _cls: "RuntimeException", message: _str(e) }
      |    if _rtKind(e.message) <> "s" then e.message = _str(e._cls)
      |    throw e
      |end sub
      |
      |function RuntimeException_new(msg = invalid as dynamic) as object
      |    return { _cls: "RuntimeException", message: _str(msg) }
      |end function
      |function IllegalStateException_new(msg = invalid as dynamic) as object
      |    return { _cls: "IllegalStateException", message: _str(msg) }
      |end function
      |function IllegalArgumentException_new(msg = invalid as dynamic) as object
      |    return { _cls: "IllegalArgumentException", message: _str(msg) }
      |end function
      |
      |sub RtAssert(cond as dynamic, msg = invalid as dynamic)
      |    if not cond
      |        msgText = "assertion failed"
      |        if msg <> invalid then msgText = msgText + ": " + _str(RtForce(msg))
      |        RtThrow({ _cls: "AssertionError", message: msgText })
      |    end if
      |end sub
      |
      |' Single-threaded target: a synchronized block is just the block.
      |function RtSynchronized(thunk as dynamic) as dynamic
      |    return RtForce(thunk)
      |end function
      |
      |' The Unit VALUE `()`. Distinct from `invalid` (None), so `Some(())` survives.
      |function RtUnit() as object
      |    ga = GetGlobalAA()
      |    if ga.__rtUnit = invalid then ga.__rtUnit = { _cls: "Unit", _fields: [] }
      |    return ga.__rtUnit
      |end function
      |
      |function RtDoubleMax() as double
      |    return 1.7976931348623157e+308#
      |end function
      |function RtDoubleMin() as double
      |    return -1.7976931348623157e+308#
      |end function
      |
      |' ── Option (a nullable: Some(x) is x, None is invalid) ─────────────────────
      |
      |function _optContains(o as dynamic, x as dynamic) as boolean
      |    if o = invalid then return false
      |    return _eq(o, x)
      |end function
      |function _optOrElse(o as dynamic, alt as dynamic) as dynamic
      |    if o <> invalid then return o
      |    return RtForce(alt)
      |end function
      |function _getOrElseThunk(o as dynamic, alt as dynamic) as dynamic
      |    if o <> invalid then return o
      |    return RtForce(alt)
      |end function
      |
      |' ── Try ────────────────────────────────────────────────────────────────────
      |
      |function Success(v as dynamic) as object
      |    return { _cls: "Success", value: v, _fields: ["value"] }
      |end function
      |function Failure(e as dynamic) as object
      |    return { _cls: "Failure", exception: e, error: e, _fields: ["exception"] }
      |end function
      |' Either: BrightScript's Left and Right are its string functions
      |function RtLeft(v as dynamic) as object
      |    return { _cls: "Left", value: v, _fields: ["value"] }
      |end function
      |function RtRight(v as dynamic) as object
      |    return { _cls: "Right", value: v, _fields: ["value"] }
      |end function
      |
      |' ── java.util.concurrent.atomic.AtomicBoolean ─────────────────────────────
      |
      |function AtomicBoolean_new(v = false as dynamic) as object
      |    return { _cls: "AtomicBoolean", value: v }
      |end function
      |function AtomicBoolean_get(self as object) as boolean
      |    return self.value
      |end function
      |function AtomicBoolean_set(self as object, v as dynamic) as dynamic
      |    self.value = v
      |    return invalid
      |end function
      |function AtomicBoolean_compareAndSet(self as object, expect as dynamic, update as dynamic) as boolean
      |    if self.value = expect
      |        self.value = update
      |        return true
      |    end if
      |    return false
      |end function
      |
      |' ── rapid.Task (the network module) ───────────────────────────────────────
      |' One thread: a task is a continuation-passing computation, { _cls: "Task", run: closure } where `run`
      |' takes [cb] and `cb` takes [ok, value]. A Completable holds its waiters instead. `sync` returns only a
      |' task that completes at once; anything that would block is an error here. `sleep` is a SceneGraph Timer.
      |function RtTask(runFn as function, env as object) as object
      |    return { _cls: "Task", run: { fn: runFn, env: env } }
      |end function
      |function RtCb(fn as function, env as object) as object
      |    return { fn: fn, env: env }
      |end function
      |function RapidTask_run(t as dynamic, cb as dynamic) as dynamic
      |    if t = invalid
      |        RtInvoke(cb, [true, invalid])
      |    else if t._cls = "Completable"
      |        if t.done then RtInvoke(cb, [t.ok, t.value]) else t.waiters.push(cb)
      |    else
      |        RtInvoke(t.run, [cb])
      |    end if
      |    return invalid
      |end function
      |function RapidTaskObj_pure(v as dynamic) as object
      |    return RtTask(RtTaskPureRun, { v: v })
      |end function
      |function RtTaskPureRun(env as object, args as object) as dynamic
      |    RtInvoke(args[0], [true, env.v])
      |    return invalid
      |end function
      |function RapidTaskObj_unit() as object
      |    return RapidTaskObj_pure(invalid)
      |end function
      |function RapidTaskObj_error(e as dynamic) as object
      |    return RtTask(RtTaskErrorRun, { e: e })
      |end function
      |function RtTaskErrorRun(env as object, args as object) as dynamic
      |    RtInvoke(args[0], [false, env.e])
      |    return invalid
      |end function
      |function RapidTaskObj_apply(thunk as dynamic) as object
      |    return RtTask(RtTaskApplyRun, { thunk: thunk })
      |end function
      |function RtTaskApplyRun(env as object, args as object) as dynamic
      |    try
      |        v = RtForce(env.thunk)
      |    catch e
      |        RtInvoke(args[0], [false, e])
      |        return invalid
      |    end try
      |    RtInvoke(args[0], [true, v])
      |    return invalid
      |end function
      |function RapidTaskObj_defer(thunk as dynamic) as object
      |    return RtTask(RtTaskDeferRun, { thunk: thunk })
      |end function
      |function RtTaskDeferRun(env as object, args as object) as dynamic
      |    try
      |        t = RtForce(env.thunk)
      |    catch e
      |        RtInvoke(args[0], [false, e])
      |        return invalid
      |    end try
      |    RapidTask_run(t, args[0])
      |    return invalid
      |end function
      |function RapidTaskObj_sleep(d as dynamic) as object
      |    return RtTask(RtTaskSleepRun, { ms: RtDurationMs(d) })
      |end function
      |function RtTaskSleepRun(env as object, args as object) as dynamic
      |    RtSetTimeout(env.ms, RtCb(RtTaskSleepFire, { cb: args[0] }))
      |    return invalid
      |end function
      |function RtTaskSleepFire(env as object, _args as object) as dynamic
      |    RtInvoke(env.cb, [true, invalid])
      |    return invalid
      |end function
      |function RapidTaskObj_completable() as object
      |    return { _cls: "Completable", done: false, ok: false, value: invalid, waiters: [] }
      |end function
      |function RapidTask_success(c as object, v as dynamic) as dynamic
      |    return RtTaskComplete(c, true, v)
      |end function
      |function RapidTask_failure(c as object, e as dynamic) as dynamic
      |    return RtTaskComplete(c, false, e)
      |end function
      |function RtTaskComplete(c as object, ok as boolean, v as dynamic) as dynamic
      |    if c.done then return invalid
      |    c.done = true
      |    c.ok = ok
      |    c.value = v
      |    ws = c.waiters
      |    c.waiters = []
      |    for each w in ws
      |        RtInvoke(w, [ok, v])
      |    end for
      |    return invalid
      |end function
      |function RapidTask_map(t as dynamic, f as dynamic) as object
      |    return RtTask(RtTaskMapRun, { t: t, f: f })
      |end function
      |function RtTaskMapRun(env as object, args as object) as dynamic
      |    RapidTask_run(env.t, RtCb(RtTaskMapCb, { f: env.f, cb: args[0] }))
      |    return invalid
      |end function
      |function RtTaskMapCb(env as object, args as object) as dynamic
      |    if not args[0]
      |        RtInvoke(env.cb, [false, args[1]])
      |        return invalid
      |    end if
      |    try
      |        r = RtInvoke(env.f, [args[1]])
      |    catch e
      |        RtInvoke(env.cb, [false, e])
      |        return invalid
      |    end try
      |    RtInvoke(env.cb, [true, r])
      |    return invalid
      |end function
      |function RapidTask_flatMap(t as dynamic, f as dynamic) as object
      |    return RtTask(RtTaskFlatMapRun, { t: t, f: f })
      |end function
      |function RtTaskFlatMapRun(env as object, args as object) as dynamic
      |    RapidTask_run(env.t, RtCb(RtTaskFlatMapCb, { f: env.f, cb: args[0] }))
      |    return invalid
      |end function
      |function RtTaskFlatMapCb(env as object, args as object) as dynamic
      |    if not args[0]
      |        RtInvoke(env.cb, [false, args[1]])
      |        return invalid
      |    end if
      |    try
      |        nt = RtInvoke(env.f, [args[1]])
      |    catch e
      |        RtInvoke(env.cb, [false, e])
      |        return invalid
      |    end try
      |    RapidTask_run(nt, env.cb)
      |    return invalid
      |end function
      |function RapidTask_handleError(t as dynamic, f as dynamic) as object
      |    return RtTask(RtTaskHandleRun, { t: t, f: f })
      |end function
      |function RtTaskHandleRun(env as object, args as object) as dynamic
      |    RapidTask_run(env.t, RtCb(RtTaskHandleCb, { f: env.f, cb: args[0] }))
      |    return invalid
      |end function
      |function RtTaskHandleCb(env as object, args as object) as dynamic
      |    if args[0]
      |        RtInvoke(env.cb, [true, args[1]])
      |        return invalid
      |    end if
      |    try
      |        nt = RtInvoke(env.f, [args[1]])
      |    catch e
      |        RtInvoke(env.cb, [false, e])
      |        return invalid
      |    end try
      |    RapidTask_run(nt, env.cb)
      |    return invalid
      |end function
      |function RapidTask_guarantee(t as dynamic, thunk as dynamic) as object
      |    return RtTask(RtTaskGuaranteeRun, { t: t, thunk: thunk })
      |end function
      |' task.attempt: always succeeds, with the task's outcome as a Try
      |function RapidTask_attempt(t as dynamic) as object
      |    return RtTask(RtTaskAttemptRun, { t: t })
      |end function
      |function RtTaskAttemptRun(env as object, args as object) as dynamic
      |    RapidTask_run(env.t, RtCb(RtTaskAttemptCb, { cb: args[0] }))
      |    return invalid
      |end function
      |function RtTaskAttemptCb(env as object, args as object) as dynamic
      |    if args[0]
      |        RtInvoke(env.cb, [true, Success(args[1])])
      |    else
      |        RtInvoke(env.cb, [true, Failure(args[1])])
      |    end if
      |    return invalid
      |end function
      |function RtTaskGuaranteeRun(env as object, args as object) as dynamic
      |    RapidTask_run(env.t, RtCb(RtTaskGuaranteeCb, { thunk: env.thunk, cb: args[0] }))
      |    return invalid
      |end function
      |function RtTaskGuaranteeCb(env as object, args as object) as dynamic
      |    RapidTask_run(RtForce(env.thunk), RtCb(RtTaskGuaranteeDone, { ok: args[0], v: args[1], cb: env.cb }))
      |    return invalid
      |end function
      |function RtTaskGuaranteeDone(env as object, _args as object) as dynamic
      |    RtInvoke(env.cb, [env.ok, env.v])
      |    return invalid
      |end function
      |function RapidTask_timeout(t as dynamic, d as dynamic) as object
      |    return RtTask(RtTaskTimeoutRun, { t: t, ms: RtDurationMs(d) })
      |end function
      |function RtTaskTimeoutRun(env as object, args as object) as dynamic
      |    state = { fired: false, cb: args[0], ms: env.ms }
      |    RtSetTimeout(env.ms, RtCb(RtTaskTimeoutFire, { state: state }))
      |    RapidTask_run(env.t, RtCb(RtTaskTimeoutCb, { state: state }))
      |    return invalid
      |end function
      |function RtTaskTimeoutFire(env as object, _args as object) as dynamic
      |    if env.state.fired then return invalid
      |    env.state.fired = true
      |    RtInvoke(env.state.cb, [false, { _cls: "TimeoutException", message: "Task timed out after " + _str(env.state.ms) + " ms" }])
      |    return invalid
      |end function
      |function RtTaskTimeoutCb(env as object, args as object) as dynamic
      |    if env.state.fired then return invalid
      |    env.state.fired = true
      |    RtInvoke(env.state.cb, args)
      |    return invalid
      |end function
      |function RapidTask_unit(t as dynamic) as object
      |    return RapidTask_map(t, RtCb(RtTaskConstUnit, {}))
      |end function
      |function RtTaskConstUnit(_env as object, _args as object) as dynamic
      |    return invalid
      |end function
      |function RapidTask_foreach(t as dynamic, f as dynamic) as dynamic
      |    return RapidTask_start(RapidTask_map(t, f))
      |end function
      |function RapidTask_start(t as dynamic) as object
      |    RapidTask_run(t, RtCb(RtTaskStartCb, {}))
      |    return RapidTaskObj_pure(invalid)
      |end function
      |function RapidTask_startUnit(t as dynamic) as dynamic
      |    RapidTask_start(t)
      |    return invalid
      |end function
      |function RtTaskStartCb(_env as object, args as object) as dynamic
      |    if not args[0] then print "[rt] task failed: "; _getMessage(args[1])
      |    return invalid
      |end function
      |function RapidTask_sync(t as dynamic) as dynamic
      |    res = { done: false, ok: false, v: invalid }
      |    RapidTask_run(t, RtCb(RtTaskSyncCb, { box: res }))
      |    if not res.done then RtThrow({ _cls: "IllegalStateException", message: "Task.sync: the task did not complete at once (one thread)" })
      |    if not res.ok then RtThrow(res.v)
      |    return res.v
      |end function
      |function RtTaskSyncCb(env as object, args as object) as dynamic
      |    env.box.done = true
      |    env.box.ok = args[0]
      |    env.box.v = args[1]
      |    return invalid
      |end function
      |' a one-shot timer on the scene's thread: `cb` runs after `ms`
      |function RtSetTimeout(ms as dynamic, cb as dynamic) as dynamic
      |    ga = GetGlobalAA()
      |    if ga.__rtTimers = invalid
      |        ga.__rtTimers = {}
      |        ga.__rtTimerSeq = 0
      |    end if
      |    ga.__rtTimerSeq = ga.__rtTimerSeq + 1
      |    t = CreateObject("roSGNode", "Timer")
      |    t.id = "rtTimer" + ga.__rtTimerSeq.ToStr()
      |    t.repeat = false
      |    seconds = ms / 1000.0
      |    if seconds < 0.001 then seconds = 0.001
      |    t.duration = seconds
      |    ga.__rtTimers[t.id] = cb
      |    ga.appScene.appendChild(t)
      |    t.observeField("fire", "RtOnTimer")
      |    t.control = "start"
      |    return invalid
      |end function
      |' `cl(args)`, for the handlers the platform calls (a timer, a frame, a key, a download, a picture, a player, the socket):
      |' an error it throws goes to the app's error hook (UniversalRuntime's uiOnError sets it) and the console, and the
      |' channel goes on, where it would otherwise stop (a sideloaded channel in the debugger, one from the store closing)
      |' with nothing told to anyone.
      |function RtGuard(cl as dynamic, args as object) as dynamic
      |    answer = invalid
      |    try
      |        answer = RtInvoke(cl, args)
      |    catch e
      |        RtReportError(e)
      |    end try
      |    RtAfterTurn()
      |    return answer
      |end function
      |' The end of a handler the platform called, before the scene is drawn again: what the program's runtime has to put
      |' right by then (UniversalRuntime's motions, uiMotionsSettle), called with what it keeps.
      |sub RtAfterTurn()
      |    ga = GetGlobalAA()
      |    hook = ga.rtAfterTurn
      |    if hook <> invalid then hook.fn(hook.env)
      |end sub
      |
      |sub RtReportError(e as dynamic)
      |    ga = GetGlobalAA()
      |    message = ""
      |    stack = ""
      |    if type(e) = "roAssociativeArray"
      |        if e.message <> invalid then message = _str(e.message)
      |        if message = "" and e._cls <> invalid then message = _str(e)
      |        if e.number <> invalid then message = message + " (&h" + StrI(e.number, 16) + ")"
      |        if e.backtrace <> invalid
      |            lines = []
      |            for each frame in e.backtrace
      |                lines.push(_str(frame["function"]) + " " + _str(frame.filename) + ":" + _str(frame.line_number))
      |            end for
      |            lines.Reverse()
      |            stack = lines.Join(Chr(10))
      |        end if
      |    else
      |        message = _str(e)
      |    end if
      |    print "[error] "; message
      |    if stack <> "" then print stack
      |    if ga.uiErrorHook = invalid or ga.uiReporting = true then return
      |    ga.uiReporting = true
      |    try
      |        RtInvoke(ga.uiErrorHook, [message, stack])
      |    catch again
      |        print "[error] the error hook failed too: "; again.message
      |    end try
      |    ga.uiReporting = false
      |end sub
      |sub RtOnTimer(event as object)
      |    ga = GetGlobalAA()
      |    n = event.getRoSGNode()
      |    cb = ga.__rtTimers[n.id]
      |    if cb = invalid then return
      |    ga.__rtTimers.Delete(n.id)
      |    ga.appScene.removeChild(n)
      |    RtGuard(cb, [])
      |end sub
      |
      |' ── fabric.Json (the network module): the native JSON values — an assoc array, an array, a string, a number,
      |' a boolean, invalid — and BrightScript's own parser and formatter ────────
      |' Bad JSON throws, as fabric's parser does, where ParseJson answers invalid; and nothing, or only spaces, is
      |' bad JSON asked of no parser, which logs "Data is empty" for every one
      |function _jsonParse(text as dynamic) as dynamic
      |    if _rtKind(text) <> "s" then RtThrow(RuntimeException_new("no JSON to parse"))
      |    body = text.Trim()
      |    if body = "" then RtThrow(RuntimeException_new("no JSON to parse"))
      |    j = ParseJson(body)
      |    if j = invalid and body <> "null" then RtThrow(RuntimeException_new("not JSON: " + Left(body, 80)))
      |    return j
      |end function
      |function _jsonFormat(j as dynamic) as string
      |    return FormatJson(j)
      |end function
      |function _jsonGet(j as dynamic, key as dynamic) as dynamic
      |    if j = invalid then return invalid
      |    k = _rtKind(j)
      |    if k = "aa" then return j[key]
      |    if k = "arr" and _rtKind(key) = "n" then return j[key]
      |    return invalid
      |end function
      |function _jsonOpt(j as dynamic, key as dynamic) as dynamic
      |    return _jsonGet(j, key)
      |end function
      |function _jsonObj(pairs as object) as object
      |    aa = {}
      |    aa.SetModeCaseSensitive()
      |    for each p in pairs
      |        aa[p._1] = p._2
      |    end for
      |    return aa
      |end function
      |function _json_asString(j as dynamic) as string
      |    return _str(j)
      |end function
      |function _json_asLong(j as dynamic) as dynamic
      |    if _rtKind(j) = "s" then return Val(j, 10)
      |    return j
      |end function
      |function _json_asInt(j as dynamic) as dynamic
      |    return _json_asLong(j)
      |end function
      |function _json_asDouble(j as dynamic) as dynamic
      |    if _rtKind(j) = "s" then return Val(j)
      |    return j
      |end function
      |function _json_asFloat(j as dynamic) as dynamic
      |    return _json_asDouble(j)
      |end function
      |function _json_asBoolean(j as dynamic) as dynamic
      |    return j
      |end function
      |function _json_asVector(j as dynamic) as dynamic
      |    return j
      |end function
      |function _json_asArr(j as dynamic) as dynamic
      |    return j
      |end function
      |function _json_asObj(j as dynamic) as dynamic
      |    return j
      |end function
      |function _json_asMap(j as dynamic) as dynamic
      |    return j
      |end function
      |function _json_isNull(j as dynamic) as boolean
      |    return j = invalid
      |end function
      |function _json_isObj(j as dynamic) as boolean
      |    return type(j) = "roAssociativeArray"
      |end function
      |function _json_isArr(j as dynamic) as boolean
      |    return type(j) = "roArray"
      |end function
      |function _json_isStr(j as dynamic) as boolean
      |    return _rtKind(j) = "s"
      |end function
      |' Json.value: an object's fields as a Scala Map; anything else is its own value
      |function _json_value(j as dynamic) as dynamic
      |    if type(j) <> "roAssociativeArray" then return j
      |    m = _mapEmpty()
      |    for each k in j
      |        m = _mapPut(m, k, j[k])
      |    end for
      |    return m
      |end function
      |function _json_toString(j as dynamic) as string
      |    return FormatJson(j)
      |end function
      |' fabric.rw: codecs. A primitive, a String or a Json value is itself (`_rw(name)`); a case class reads and
      |' writes by its field names with a codec per field (`_rwGen`, the emitter's derivation of `RW.gen`); Option,
      |' List and Map wrap their element's codec.
      |function _rw(name as string) as object
      |    return { _cls: "RW", name: name }
      |end function
      |function _rwGen(cls as string, keys as object, slots as object, rws as object, ctor as dynamic, defs = invalid as dynamic, has = invalid as dynamic) as object
      |    return { _cls: "RW", kind: "case", cls: cls, keys: keys, slots: slots, rws: rws, ctor: ctor, defs: defs, has: has }
      |end function
      |function _rwOpt(inner as object) as object
      |    return { _cls: "RW", kind: "opt", inner: inner }
      |end function
      |function _rwList(inner as object) as object
      |    return { _cls: "RW", kind: "list", inner: inner }
      |end function
      |function _rwMap(inner as object) as object
      |    return { _cls: "RW", kind: "map", inner: inner }
      |end function
      |' JSON → value
      |function _rwRead(rw as dynamic, j as dynamic) as dynamic
      |    if rw = invalid or rw.kind = invalid or j = invalid then return j
      |    if rw.kind = "opt" then return _rwRead(rw.inner, j)
      |    if rw.kind = "list"
      |        r = []
      |        for each x in j
      |            r.push(_rwRead(rw.inner, x))
      |        end for
      |        return r
      |    end if
      |    if rw.kind = "map"
      |        m = _mapEmpty()
      |        for each k in j
      |            m = _mapPut(m, k, _rwRead(rw.inner, j[k]))
      |        end for
      |        return m
      |    end if
      |    n = rw.keys.count()
      |    args = []
      |    for i = 0 to n - 1
      |        ' a field the JSON leaves out, or sends as null, takes the case class's default, where it has one
      |        if rw.has <> invalid and rw.has[i] = true and (not j.DoesExist(rw.keys[i]) or j[rw.keys[i]] = invalid)
      |            args.push(rw.defs[i])
      |        else
      |            args.push(_rwRead(rw.rws[i], j[rw.keys[i]]))
      |        end if
      |    end for
      |    if rw.ctor <> invalid then return RtCallArgs(rw.ctor, args)
      |    o = { _cls: rw.cls, _fields: rw.slots }
      |    for i = 0 to n - 1
      |        o[rw.slots[i]] = args[i]
      |    end for
      |    return o
      |end function
      |' value → JSON
      |function _rwWrite(rw as dynamic, v as dynamic) as dynamic
      |    if rw = invalid or rw.kind = invalid or v = invalid then return v
      |    if rw.kind = "opt" then return _rwWrite(rw.inner, v)
      |    if rw.kind = "list"
      |        r = []
      |        for each x in v
      |            r.push(_rwWrite(rw.inner, x))
      |        end for
      |        return r
      |    end if
      |    if rw.kind = "map"
      |        aa = {}
      |        aa.SetModeCaseSensitive()
      |        for each t in _mapToList(v)
      |            aa[_str(t._1)] = _rwWrite(rw.inner, t._2)
      |        end for
      |        return aa
      |    end if
      |    aa = {}
      |    aa.SetModeCaseSensitive()
      |    n = rw.keys.count()
      |    for i = 0 to n - 1
      |        aa[rw.keys[i]] = _rwWrite(rw.rws[i], v[rw.slots[i]])
      |    end for
      |    return aa
      |end function
      |' a function reference applied to an argument list (BrightScript cannot spread one)
      |function RtCallArgs(f as function, args as object) as dynamic
      |    n = args.count()
      |    ' past 31, the emitted function takes the rest as one array (see packParams)
      |    if n > 31 then
      |        packed = []
      |        more = []
      |        for i = 0 to n - 1
      |            if i < 30 then packed.push(args[i]) else more.push(args[i])
      |        end for
      |        packed.push(more)
      |        return RtCallArgs(f, packed)
      |    end if
      |    if n = 0 then return f()
      |    if n = 1 then return f(args[0])
      |    if n = 2 then return f(args[0], args[1])
      |    if n = 3 then return f(args[0], args[1], args[2])
      |    if n = 4 then return f(args[0], args[1], args[2], args[3])
      |    if n = 5 then return f(args[0], args[1], args[2], args[3], args[4])
      |    if n = 6 then return f(args[0], args[1], args[2], args[3], args[4], args[5])
      |    if n = 7 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6])
      |    if n = 8 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7])
      |    if n = 9 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8])
      |    if n = 10 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9])
      |    if n = 11 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10])
      |    if n = 12 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11])
      |    if n = 13 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12])
      |    if n = 14 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13])
      |    if n = 15 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14])
      |    if n = 16 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15])
      |    if n = 17 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16])
      |    if n = 18 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17])
      |    if n = 19 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18])
      |    if n = 20 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19])
      |    if n = 21 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20])
      |    if n = 22 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20], args[21])
      |    if n = 23 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20], args[21], args[22])
      |    if n = 24 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20], args[21], args[22], args[23])
      |    if n = 25 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20], args[21], args[22], args[23], args[24])
      |    if n = 26 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20], args[21], args[22], args[23], args[24], args[25])
      |    if n = 27 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20], args[21], args[22], args[23], args[24], args[25], args[26])
      |    if n = 28 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20], args[21], args[22], args[23], args[24], args[25], args[26], args[27])
      |    if n = 29 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20], args[21], args[22], args[23], args[24], args[25], args[26], args[27], args[28])
      |    if n = 30 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20], args[21], args[22], args[23], args[24], args[25], args[26], args[27], args[28], args[29])
      |    if n = 31 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20], args[21], args[22], args[23], args[24], args[25], args[26], args[27], args[28], args[29], args[30])
      |    if n = 32 then return f(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15], args[16], args[17], args[18], args[19], args[20], args[21], args[22], args[23], args[24], args[25], args[26], args[27], args[28], args[29], args[30], args[31])
      |    RtThrow("RtCallArgs: more than 32 arguments, which is as many as a BrightScript function takes")
      |    return invalid
      |end function
      |' A Scala 3 enum's simple case: one singleton per (enum, case), named by `toString`, ordered by `ordinal`.
      |function RtEnum(cls as string, name as string, ordinal as integer) as object
      |    ga = GetGlobalAA()
      |    key = "__enum_" + cls + "_" + name
      |    e = ga[key]
      |    if e <> invalid then return e
      |    e = { _cls: cls, _enum: name, ordinal: ordinal, toString: RtEnumToString }
      |    ga[key] = e
      |    return e
      |end function
      |function RtEnumToString(self as object) as string
      |    return self._enum
      |end function
      |
      |' ── java.lang.Object: `new Object` is a lock (a synchronized block is just the block), so any distinct value does ──
      |function Object_new() as object
      |    return { _cls: "Object" }
      |end function
      |
      |' ── java.util.concurrent.ConcurrentHashMap, AtomicLong / AtomicInteger, UUID, System, scribe ──
      |function ConcurrentHashMap_new() as object
      |    m = {}
      |    m.SetModeCaseSensitive()
      |    return { _cls: "ConcurrentHashMap", m: m }
      |end function
      |function ConcurrentHashMap_put(self as object, k as dynamic, v as dynamic) as dynamic
      |    key = _str(k)
      |    old = self.m[key]
      |    self.m[key] = v
      |    return old
      |end function
      |function ConcurrentHashMap_get(self as object, k as dynamic) as dynamic
      |    return self.m[_str(k)]
      |end function
      |function ConcurrentHashMap_remove(self as object, k as dynamic) as dynamic
      |    key = _str(k)
      |    old = self.m[key]
      |    self.m.Delete(key)
      |    return old
      |end function
      |function ConcurrentHashMap_getOrDefault(self as object, k as dynamic, d as dynamic) as dynamic
      |    v = self.m[_str(k)]
      |    if v = invalid then return d
      |    return v
      |end function
      |function ConcurrentHashMap_containsKey(self as object, k as dynamic) as boolean
      |    return self.m.DoesExist(_str(k))
      |end function
      |function ConcurrentHashMap_computeIfAbsent(self as object, k as dynamic, f as dynamic) as dynamic
      |    key = _str(k)
      |    v = self.m[key]
      |    if v = invalid
      |        v = RtInvoke(f, [k])
      |        self.m[key] = v
      |    end if
      |    return v
      |end function
      |function ConcurrentHashMap_values(self as object) as object
      |    r = []
      |    for each key in self.m
      |        r.push(self.m[key])
      |    end for
      |    return r
      |end function
      |function ConcurrentHashMap_size(self as object) as integer
      |    return self.m.Count()
      |end function
      |function ConcurrentHashMap_isEmpty(self as object) as boolean
      |    return self.m.Count() = 0
      |end function
      |function ConcurrentHashMap_clear(self as object) as dynamic
      |    self.m.Clear()
      |    return invalid
      |end function
      |function ConcurrentHashMap_forEach(self as object, f as dynamic) as dynamic
      |    for each key in self.m.Keys()
      |        RtInvoke(f, [key, self.m[key]])
      |    end for
      |    return invalid
      |end function
      |function ConcurrentLinkedQueue_new() as object
      |    return { _cls: "ConcurrentLinkedQueue", items: [] }
      |end function
      |function ConcurrentLinkedQueue_add(self as object, x as dynamic) as boolean
      |    self.items.push(x)
      |    return true
      |end function
      |function ConcurrentLinkedQueue_offer(self as object, x as dynamic) as boolean
      |    self.items.push(x)
      |    return true
      |end function
      |function ConcurrentLinkedQueue_poll(self as object) as dynamic
      |    if self.items.count() = 0 then return invalid
      |    return self.items.shift()
      |end function
      |function ConcurrentLinkedQueue_peek(self as object) as dynamic
      |    if self.items.count() = 0 then return invalid
      |    return self.items[0]
      |end function
      |function ConcurrentLinkedQueue_isEmpty(self as object) as boolean
      |    return self.items.count() = 0
      |end function
      |function ConcurrentLinkedQueue_size(self as object) as integer
      |    return self.items.count()
      |end function
      |function ConcurrentLinkedQueue_clear(self as object) as dynamic
      |    self.items.clear()
      |    return invalid
      |end function
      |function AtomicLong_new(v = 0 as dynamic) as object
      |    return { _cls: "AtomicLong", value: v }
      |end function
      |function AtomicInteger_new(v = 0 as dynamic) as object
      |    return { _cls: "AtomicInteger", value: v }
      |end function
      |function AtomicLong_get(self as object) as dynamic
      |    return self.value
      |end function
      |function AtomicLong_set(self as object, v as dynamic) as dynamic
      |    self.value = v
      |    return invalid
      |end function
      |function AtomicLong_incrementAndGet(self as object) as dynamic
      |    self.value = self.value + 1
      |    return self.value
      |end function
      |function AtomicLong_decrementAndGet(self as object) as dynamic
      |    self.value = self.value - 1
      |    return self.value
      |end function
      |function AtomicLong_getAndIncrement(self as object) as dynamic
      |    old = self.value
      |    self.value = old + 1
      |    return old
      |end function
      |function AtomicLong_addAndGet(self as object, d as dynamic) as dynamic
      |    self.value = self.value + d
      |    return self.value
      |end function
      |function AtomicLong_compareAndSet(self as object, expect as dynamic, update as dynamic) as boolean
      |    if self.value = expect
      |        self.value = update
      |        return true
      |    end if
      |    return false
      |end function
      |function _uuid() as string
      |    return CreateObject("roDeviceInfo").GetRandomUUID()
      |end function
      |function _nowMillis() as dynamic
      |    dt = CreateObject("roDateTime")
      |    return dt.AsSecondsLong() * 1000 + dt.GetMilliseconds()
      |end function
      |' System.identityHashCode: a number an object keeps for its life, given it when first asked
      |function _identityHash(o as dynamic) as integer
      |    if type(o) <> "roAssociativeArray" then return 0
      |    if o.__identityHash = invalid
      |        ga = GetGlobalAA()
      |        if ga.__identityHashes = invalid then ga.__identityHashes = 0
      |        ga.__identityHashes = ga.__identityHashes + 1
      |        o.__identityHash = ga.__identityHashes
      |    end if
      |    return o.__identityHash
      |end function
      |function _toHex(n as dynamic) as string
      |    return LCase(StrI(n, 16))
      |end function
      |' new Array[T](n): n slots holding z
      |function _arrNew(n as dynamic, z as dynamic) as object
      |    xs = CreateObject("roArray", n, true)
      |    for i = 1 to n
      |        xs.Push(z)
      |    end for
      |    return xs
      |end function
      |' Iterator.iterate(z)(f).takeWhile(p), gathered
      |function _iterateWhile(z as dynamic, f as dynamic, p as dynamic) as object
      |    xs = []
      |    x = z
      |    while RtInvoke(p, [x])
      |        xs.Push(x)
      |        x = RtInvoke(f, [x])
      |    end while
      |    return xs
      |end function
      |' Try(body): a Success of what the body answered, or a Failure of what it threw
      |function _tryApply(thunk as dynamic) as object
      |    r = invalid
      |    try
      |        r = Success(RtForce(thunk))
      |    catch e
      |        r = Failure(e)
      |    end try
      |    return r
      |end function
      |function Scribe_log(level as string, msgs as object) as dynamic
      |    s = ""
      |    for each msg in msgs
      |        s = s + _str(RtForce(msg))
      |    end for
      |    print "[" + level + "] " + s
      |    return invalid
      |end function
      |function _getMessage(e as dynamic) as dynamic
      |    if _rtKind(e) = "aa" and e.message <> invalid then return e.message
      |    return _str(e)
      |end function
      |function _clsName(x as dynamic) as string
      |    if _rtKind(x) = "aa" and x._name <> invalid then return _str(x._name)
      |    if _rtKind(x) = "aa" and x._cls <> invalid then return _str(x._cls)
      |    return type(x)
      |end function
      |function _regex(pattern as string) as object
      |    return CreateObject("roRegex", pattern, "")
      |end function
      |
      |' ── java.lang.ThreadLocal (one thread: a slot, lazily seeded) ─────────────
      |' Modelled as a class so an anonymous subclass overriding `initialValue`
      |' composes: its constructor runs ThreadLocal__install then installs its own
      |' `initialValue` slot on top.
      |
      |sub ThreadLocal__install(self as object)
      |    self.initialValue = ThreadLocal_initialValue
      |end sub
      |function ThreadLocal_init(self as object) as dynamic
      |    self.__tlSet = false
      |    self.__tlValue = invalid
      |    return invalid
      |end function
      |function ThreadLocal_new() as object
      |    self = { _cls: "ThreadLocal" }
      |    ThreadLocal__install(self)
      |    ThreadLocal_init(self)
      |    return self
      |end function
      |function ThreadLocal_initialValue(_self as object) as dynamic
      |    return invalid
      |end function
      |function ThreadLocal_get(self as object) as dynamic
      |    seeded = false
      |    if self.DoesExist("__tlSet") then seeded = self.__tlSet
      |    if not seeded
      |        self.__tlValue = self.initialValue(self)
      |        self.__tlSet = true
      |    end if
      |    return self.__tlValue
      |end function
      |function ThreadLocal_set(self as object, v as dynamic) as dynamic
      |    self.__tlValue = v
      |    self.__tlSet = true
      |    return invalid
      |end function
      |function ThreadLocal_remove(self as object) as dynamic
      |    self.__tlSet = false
      |    self.__tlValue = invalid
      |    return invalid
      |end function
      |
      |' ── ordering ───────────────────────────────────────────────────────────────
      |
      |function _cmpNum(a as dynamic, b as dynamic) as integer
      |    if a < b then return -1
      |    if a > b then return 1
      |    return 0
      |end function
      |
      |' `xs.sorted` over Ordered elements: a STABLE sort (Scala's is) calling each
      |' element's `compare` slot.
      |function _sortedOrdered(xs as object) as object
      |    r = []
      |    for each e in xs
      |        r.push(e)
      |    end for
      |    n = r.count()
      |    if n > 1
      |        for i = 0 to n - 2
      |            for j = 0 to n - 2 - i
      |                if r[j].compare(r[j], r[j + 1]) > 0
      |                    tmp = r[j]
      |                    r[j] = r[j + 1]
      |                    r[j + 1] = tmp
      |                end if
      |            end for
      |        end for
      |    end if
      |    return r
      |end function
      |
      |' ── scala.concurrent Promise / Future (callback-completed, single thread) ─
      |
      |function Promise_new() as object
      |    return { _cls: "Promise", done: false, value: invalid, callbacks: [] }
      |end function
      |function _promiseSuccess(p as object, v as dynamic) as object
      |    if p.done then RtThrow({ _cls: "IllegalStateException", message: "Promise already completed." })
      |    p.done = true
      |    p.value = v
      |    callbacks = p.callbacks
      |    p.callbacks = []
      |    for each cb in callbacks
      |        RtInvoke(cb, [v])
      |    end for
      |    return p
      |end function
      |function _futureOnSuccess(f as object, cb as dynamic) as dynamic
      |    if f.done
      |        RtInvoke(cb, [f.value])
      |    else
      |        f.callbacks.push(cb)
      |    end if
      |    return invalid
      |end function
      |function _futureMap(f as object, fn as dynamic) as object
      |    r = Promise_new()
      |    _futureOnSuccess(f, { fn: _rtFutureMapStep, env: { r: r, fn: fn } })
      |    return r
      |end function
      |function _rtFutureMapStep(env as object, args as object) as dynamic
      |    _promiseSuccess(env.r, RtInvoke(env.fn, [args[0]]))
      |    return invalid
      |end function
      |
      |' ── helpers referenced by the emitter's stdlib and pattern lowering ────────
      |
      |function Exception_new(msg = "" as dynamic) as object
      |    return { _cls: "Exception", message: _str(msg) }
      |end function
      |function MatchError_new(v = invalid as dynamic) as object
      |    return { _cls: "MatchError", message: "MatchError: " + _str(v) }
      |end function
      |function AssertionError_new(msg = invalid as dynamic) as object
      |    msgText = "assertion failed"
      |    if msg <> invalid then msgText = msgText + ": " + _str(msg)
      |    return { _cls: "AssertionError", message: msgText }
      |end function
      |
      |' `c.copy(field = x)`: a shallow copy with the given fields replaced. The copy
      |' is a new object, so its identity stamp is not carried over.
      |function _caseCopy(src as object, overrides as object) as object
      |    r = {}
      |    for each k in src
      |        if LCase(k) <> "_id" then r[k] = src[k]
      |    end for
      |    for each k in overrides
      |        r[k] = overrides[k]
      |    end for
      |    return r
      |end function
      |
      |' `xs.sorted` over primitives (numbers or strings); stable.
      |function _sortedPrim(xs as object) as object
      |    r = []
      |    for each e in xs
      |        r.push(e)
      |    end for
      |    n = r.count()
      |    if n > 1
      |        for i = 0 to n - 2
      |            for j = 0 to n - 2 - i
      |                if r[j] > r[j + 1]
      |                    tmp = r[j]
      |                    r[j] = r[j + 1]
      |                    r[j + 1] = tmp
      |                end if
      |            end for
      |        end for
      |    end if
      |    return r
      |end function
      |
      |' ── string conversion of objects (used by the runtime's _str) ─────────────
      |' An object with a `toString` slot formats itself; a case class prints as
      |' `Cls(f1, f2)`; an exception prints its message; a sequence as `List(…)`.
      |function _rtObjStr(o as object) as string
      |    if o.DoesExist("toString")
      |        if _rtKind(o.toString) = "fn" then return o.toString(o)
      |    end if
      |    if o.DoesExist("_fields") and o.DoesExist("_cls")
      |        parts = []
      |        for each f in o._fields
      |            parts.push(_str(o[f]))
      |        end for
      |        return _str(o._cls) + "(" + parts.Join(",") + ")"
      |    end if
      |    if o.DoesExist("message") then return _str(o.message)
      |    if o.DoesExist("_name") then return _str(o._name)
      |    if o.DoesExist("_cls") then return _str(o._cls)
      |    return "<object>"
      |end function
      |function _rtArrStr(a as object) as string
      |    parts = []
      |    for each e in a
      |        parts.push(_str(e))
      |    end for
      |    return "List(" + parts.Join(", ") + ")"
      |end function
      |' ── Scala stdlib implicits the emitter emits by name ──
      |' Predef.refArrayOps wraps an Array only to give it operations a BrightScript roArray already has, so it is
      |' the array itself.
      |function refArrayOps(a as object) as object
      |    return a
      |end function
      |function intArrayOps(a as object) as object : return a : end function
      |function longArrayOps(a as object) as object : return a : end function
      |function doubleArrayOps(a as object) as object : return a : end function
      |function booleanArrayOps(a as object) as object : return a : end function
      |' Option.option2Iterable, for an Option used where a collection is wanted (a for-comprehension, a flatMap):
      |' None is invalid here as everywhere, Some is its value, and the iterable is empty or the one element.
      |function option2Iterable(o as dynamic) as object
      |    if o = invalid then return []
      |    return [o]
      |end function
      |' RichChar predicates. A Char is a one-character string in this runtime, so these test its code point.
      |' A Char's code, for arithmetic on it: a one-character string here, or already a number (a Char literal passed on)
      |function _chCode(c as dynamic) as dynamic
      |    if _rtIsStr(c) then return Asc(c)
      |    return c
      |end function
      |function _ch_isDigit(c as dynamic) as boolean
      |    if c = invalid or Len(c) = 0 then return false
      |    n = Asc(c)
      |    return n >= 48 and n <= 57
      |end function
      |function _ch_isLetter(c as dynamic) as boolean
      |    if c = invalid or Len(c) = 0 then return false
      |    n = Asc(c)
      |    return (n >= 65 and n <= 90) or (n >= 97 and n <= 122)
      |end function
      |function _ch_isLetterOrDigit(c as dynamic) as boolean
      |    return _ch_isLetter(c) or _ch_isDigit(c)
      |end function
      |function _ch_isWhitespace(c as dynamic) as boolean
      |    if c = invalid or Len(c) = 0 then return false
      |    n = Asc(c)
      |    return n = 32 or n = 9 or n = 10 or n = 13 or n = 12 or n = 11
      |end function
      |function _ch_isSpaceChar(c as dynamic) as boolean
      |    return c = " "
      |end function
      |function _ch_isUpper(c as dynamic) as boolean
      |    if c = invalid or Len(c) = 0 then return false
      |    n = Asc(c)
      |    return n >= 65 and n <= 90
      |end function
      |function _ch_isLower(c as dynamic) as boolean
      |    if c = invalid or Len(c) = 0 then return false
      |    n = Asc(c)
      |    return n >= 97 and n <= 122
      |end function
      |' Json.isNum / Json.isBoolean, the siblings of _json_isStr the family was missing.
      |function _json_isNum(j as dynamic) as boolean
      |    return _rtKind(j) = "n"
      |end function
      |function _json_isBoolean(j as dynamic) as boolean
      |    return _rtKind(j) = "b"
      |end function
      |""".stripMargin

  /** The immutable Set and Map of the runtime: a constant of their own, as `shimsBrs` is one, under the JVM's cap. */
  private val collectionShimsBrs: String =
    """
      |' ── immutable Set (array-backed, `_eq` membership) ─────────────────────────
      |
      |function _setAdd(s as object, x as dynamic) as object
      |    r = []
      |    found = false
      |    for each e in s
      |        r.push(e)
      |        if not found
      |            if _eq(e, x) then found = true
      |        end if
      |    end for
      |    if not found then r.push(x)
      |    return r
      |end function
      |function _setRemove(s as object, x as dynamic) as object
      |    r = []
      |    for each e in s
      |        if not _eq(e, x) then r.push(e)
      |    end for
      |    return r
      |end function
      |function _setDiff(a as object, b as object) as object
      |    if a.count() = 0 then return a
      |    if b.count() = 0 then return a
      |    r = []
      |    for each e in a
      |        if not _setHas(b, e) then r.push(e)
      |    end for
      |    return r
      |end function
      |function _setUnion(a as object, b as object) as object
      |    r = []
      |    for each e in a
      |        r.push(e)
      |    end for
      |    for each e in b
      |        if not _setHas(r, e) then r.push(e)
      |    end for
      |    return r
      |end function
      |function _setIntersect(a as object, b as object) as object
      |    r = []
      |    for each e in a
      |        if _setHas(b, e) then r.push(e)
      |    end for
      |    return r
      |end function
      |function _setHas(s as object, x as dynamic) as boolean
      |    for each e in s
      |        if _eq(e, x) then return true
      |    end for
      |    return false
      |end function
      |
      |' ── immutable Map with OBJECT keys (an entry list; AA keys are strings) ────
      |
      |function _mapEmpty() as object
      |    return { _map: true, entries: [] }
      |end function
      |' A Scala Map is an entry list (object keys, `_eq` lookup). A Map built by the
      |' runtime's `Map(...)` is a plain string-keyed assoc array; every map
      |' helper accepts both.
      |function _rtIsEntryMap(m as dynamic) as boolean
      |    if _rtKind(m) <> "aa" then return false
      |    return m.DoesExist("_map")
      |end function
      |function _mapSize(m as dynamic) as integer
      |    if m = invalid then return 0
      |    if _rtIsEntryMap(m) then return m.entries.count()
      |    return m.count()
      |end function
      |function _mapPutT(m as dynamic, t as object) as object
      |    return _mapPut(m, t._1, t._2)
      |end function
      |function _mapFind(m as object, k as dynamic) as integer
      |    n = m.entries.count()
      |    for i = 0 to n - 1
      |        if _eq(m.entries[i].k, k) then return i
      |    end for
      |    return -1
      |end function
      |function _mapGet(m as dynamic, k as dynamic) as dynamic
      |    if m = invalid then return invalid
      |    if not _rtIsEntryMap(m) then return m[_str(k)]
      |    i = _mapFind(m, k)
      |    if i < 0 then return invalid
      |    return m.entries[i].v
      |end function
      |function _mapGetOrElse(m as dynamic, k as dynamic, d as dynamic) as dynamic
      |    if _mapHas(m, k) then return _mapGet(m, k)
      |    return d
      |end function
      |function _mapHas(m as dynamic, k as dynamic) as boolean
      |    if m = invalid then return false
      |    if not _rtIsEntryMap(m) then return m.DoesExist(_str(k))
      |    return _mapFind(m, k) >= 0
      |end function
      |function _mapPut(m as dynamic, k as dynamic, v as dynamic) as object
      |    if m = invalid then m = _mapEmpty()
      |    if not _rtIsEntryMap(m)
      |        c = {}
      |        c.Append(m)
      |        c[_str(k)] = v
      |        return c
      |    end if
      |    r = { _map: true, entries: [] }
      |    replaced = false
      |    for each e in m.entries
      |        if not replaced and _eq(e.k, k)
      |            r.entries.push({ k: k, v: v })
      |            replaced = true
      |        else
      |            r.entries.push(e)
      |        end if
      |    end for
      |    if not replaced then r.entries.push({ k: k, v: v })
      |    return r
      |end function
      |function _mapRemove(m as dynamic, k as dynamic) as object
      |    if m = invalid then return _mapEmpty()
      |    if not _rtIsEntryMap(m)
      |        c = {}
      |        c.Append(m)
      |        c.Delete(_str(k))
      |        return c
      |    end if
      |    r = { _map: true, entries: [] }
      |    for each e in m.entries
      |        if not _eq(e.k, k) then r.entries.push(e)
      |    end for
      |    return r
      |end function
      |' map ++ pairs (a Map's, a List's, an Option's): a copy of the map with each pair put in it, the later winning
      |function _mapConcat(a as dynamic, pairs as object) as object
      |    if a = invalid then a = _mapEmpty()
      |    if pairs.count() = 0 then return a
      |    if not _rtIsEntryMap(a)
      |        r = {}
      |        r.SetModeCaseSensitive()
      |        r.Append(a)
      |        for each t in pairs
      |            r[_str(t._1)] = t._2
      |        end for
      |        return r
      |    end if
      |    r = { _map: true, entries: [] }
      |    r.entries.Append(a.entries)
      |    for each t in pairs
      |        i = _mapFind(r, t._1)
      |        if i < 0 then r.entries.push({ k: t._1, v: t._2 }) else r.entries[i] = { k: t._1, v: t._2 }
      |    end for
      |    return r
      |end function
      |' map -- keys
      |function _mapRemoveAll(a as dynamic, keys as object) as object
      |    r = a
      |    for each k in keys
      |        r = _mapRemove(r, k)
      |    end for
      |    return r
      |end function
      |function _mapKeys(m as dynamic) as object
      |    if m = invalid then return []
      |    if not _rtIsEntryMap(m) then return m.Keys()
      |    r = []
      |    for each e in m.entries
      |        r.push(e.k)
      |    end for
      |    return r
      |end function
      |function _mapValues(m as dynamic) as object
      |    if m = invalid then return []
      |    if not _rtIsEntryMap(m)
      |        vs = []
      |        for each k in m
      |            vs.push(m[k])
      |        end for
      |        return vs
      |    end if
      |    r = []
      |    for each e in m.entries
      |        r.push(e.v)
      |    end for
      |    return r
      |end function
      |function _mapEq(a as object, b as object) as boolean
      |    if a.entries.count() <> b.entries.count() then return false
      |    for each e in a.entries
      |        i = _mapFind(b, e.k)
      |        if i < 0 then return false
      |        if not _eq(b.entries[i].v, e.v) then return false
      |    end for
      |    return true
      |end function
      |
      |' ── sequences (arrays here): the library methods past the core ones ───────────────────────────────────────
      |
      |' xs.lift(i): the element at i, or None (invalid) outside the sequence
      |function _lift(xs as dynamic, i as dynamic) as dynamic
      |    items = _rtItems(xs)
      |    if i < 0 or i >= items.count() then return invalid
      |    return items[i]
      |end function
      |function _liftCall(env as object, args as object) as dynamic
      |    return _lift(env.xs, args[0])
      |end function
      |function _arrIndexWhereFrom(xs as dynamic, cl as dynamic, at as integer) as integer
      |    items = _rtItems(xs)
      |    if at < 0 then at = 0
      |    for ii = at to items.count() - 1
      |        if RtInvoke(cl, [items[ii]]) then return ii
      |    end for
      |    return -1
      |end function
      |' a to b by stride, a until b by stride, and List.range(a, b, stride)
      |function _rangeBy(a as dynamic, b as dynamic, stride as dynamic, incl as boolean) as object
      |    r = []
      |    if stride = 0 then return r
      |    v = a
      |    if stride > 0
      |        while v < b or (incl and v = b)
      |            r.push(v)
      |            v = v + stride
      |        end while
      |    else
      |        while v > b or (incl and v = b)
      |            r.push(v)
      |            v = v + stride
      |        end while
      |    end if
      |    return r
      |end function
      |' Seq.fill(n)(elem), the element made again for each; Seq.tabulate(n)(f)
      |function _fill(n as integer, thunk as dynamic) as object
      |    r = []
      |    for i = 1 to n
      |        r.push(RtForce(thunk))
      |    end for
      |    return r
      |end function
      |function _tabulate(n as integer, cl as dynamic) as object
      |    r = []
      |    for i = 0 to n - 1
      |        r.push(RtInvoke(cl, [i]))
      |    end for
      |    return r
      |end function
      |function _partition(xs as dynamic, cl as dynamic) as object
      |    yes = []
      |    no = []
      |    for each x in _rtItems(xs)
      |        if RtInvoke(cl, [x]) then yes.push(x) else no.push(x)
      |    end for
      |    return Tuple2(yes, no)
      |end function
      |' xs.sortWith(lt), stable as Scala's: an element goes before an earlier one only if it is less
      |function _sortWith(xs as dynamic, lt as dynamic) as object
      |    r = []
      |    for each x in _rtItems(xs)
      |        i = r.count()
      |        while i > 0
      |            if not RtInvoke(lt, [x, r[i - 1]]) then exit while
      |            i = i - 1
      |        end while
      |        r.push(x)
      |        j = r.count() - 1
      |        while j > i
      |            r[j] = r[j - 1]
      |            j = j - 1
      |        end while
      |        r[i] = x
      |    end for
      |    return r
      |end function
      |function _reduce(xs as dynamic, op as dynamic) as dynamic
      |    items = _rtItems(xs)
      |    if items.count() = 0 then RtThrow(RuntimeException_new("empty.reduceLeft"))
      |    acc = items[0]
      |    for ii = 1 to items.count() - 1
      |        acc = RtInvoke(op, [acc, items[ii]])
      |    end for
      |    return acc
      |end function
      |function _seqUpdated(xs as dynamic, i as integer, x as dynamic) as object
      |    items = _rtItems(xs)
      |    if i < 0 or i >= items.count() then RtThrow(RuntimeException_new(_str(i) + " is out of bounds (0 until " + _str(items.count()) + ")"))
      |    r = []
      |    r.Append(items)
      |    r[i] = x
      |    return r
      |end function
      |' xs.collect { case … } (and collectFirst): the cases are one function, and an element none of them takes throws its
      |' MatchError, which leaves it out
      |function _collect(xs as dynamic, pf as dynamic, first as boolean) as dynamic
      |    r = []
      |    for each x in _rtItems(xs)
      |        taken = false
      |        try
      |            v = RtInvoke(pf, [x])
      |            taken = true
      |        catch e
      |            if e._cls <> "MatchError" then RtThrow(e)
      |        end try
      |        if taken
      |            if first then return v
      |            r.push(v)
      |        end if
      |    end for
      |    if first then return invalid
      |    return r
      |end function
      |""".stripMargin}

object RokuEmitter {
  /** Whether emitted code refers to `self`, the object a closure was written in. */
  def usesSelf(code: String): Boolean = "\\bself\\b".r.findFirstIn(code).nonEmpty

  /** How large one file of the program may grow. A Roku refuses to load a script much over 2 MB ("Error loading
    * file", compile error &hb9, seen on Roku OS 14.10 with a 2.16 MB file), and a refused install takes the channel's
    * registry with it; the program goes out in parts well under that. */
  val partBytes: Int = 1000000

  /** The program as the Roku is given it, smaller: every byte of it is compiled at each launch (a large app's 12 MB took
    * ten seconds), and most were ceremony. Each function's parameters lose `as dynamic` and `as object` (what an
    * untyped one is, or less), and its answer `as dynamic`; a closure loses the `self` it never reads and the arguments
    * it never reads (a warning apiece on the device); a `return invalid` after the function's last return goes; a
    * closure's environment no longer carries `self: invalid` (a missing key reads as invalid); and closures written
    * alike are one closure. */
  def compact(code: String): String = {
    val lines = code.replace("env: { self: invalid }", "env: {}").replace("env: { self: invalid, ", "env: { ").split("\n", -1)
    val header = "^(function|sub) ([A-Za-z0-9_]+)\\((.*)\\)(.*)$".r
    val argLine = "^\\s*([A-Za-z0-9_]+) = args\\[\\d+\\]$".r
    val closureName = "closure[0-9]+"
    def uses(body: Seq[String], name: String): Boolean = {
      val re = ("(?i)(?<![A-Za-z0-9_.])" + java.util.regex.Pattern.quote(name) + "(?![A-Za-z0-9_])").r
      body.exists(l => re.findFirstIn(l).nonEmpty)
    }
    def untyped(params: String): String = params.replace(" as dynamic", "").replace(" as object", "")
    // the program as pieces: a function (header, body, end) or a line outside one
    val pieces = mutable.ArrayBuffer[Either[String, (String, String, String, Vector[String], String)]]()
    var i = 0
    while (i < lines.length) {
      lines(i) match {
        case header(kind, name, params, rest) if !rest.contains(": end ") =>
          var j = i + 1
          while (j < lines.length && lines(j) != "end " + kind) j += 1
          if (j >= lines.length) {
            pieces += Left(lines(i))
            i += 1
          }
          else {
            pieces += Right((kind, name, params, lines.slice(i + 1, j).toVector, rest))
            i = j + 1
          }
        case other =>
          pieces += Left(other)
          i += 1
      }
    }
    val tidied = pieces.map {
      case Left(l) => Left(l)
      case Right((kind, name, params, body0, rest)) =>
        var body = body0
        if (name.matches(closureName)) {
          body = body.filterNot(l => l.trim == "self = env.self" && !uses(body.filterNot(_ eq l), "self"))
          body = body.filterNot {
            case l @ argLine(v) => !uses(body.filterNot(_ eq l), v)
            case _ => false
          }
        }
        body = dropUnusedLocals(dropUnusedLocals(body, paramNames(params)), paramNames(params))
        val kept = body.filterNot(_.trim.isEmpty)
        if (kept.size >= 2 && kept.last.trim == "return invalid" && kept(kept.size - 2).trim.startsWith("return"))
          body = body.take(body.lastIndexWhere(_.trim == "return invalid"))
        val answer = if (rest.trim == "as dynamic") "" else rest
        Right((kind, name, quiet(untyped(params), body), body, answer))
    }
    // closures alike are one: the first of each, and the others' names made its name
    val first = mutable.HashMap[String, String]()
    val alias = mutable.HashMap[String, String]()
    tidied.foreach {
      case Right((kind, name, params, body, rest)) if name.matches(closureName) =>
        val key = kind + "(" + params + ")" + rest + "\n" + body.mkString("\n")
        first.get(key) match {
          case Some(f) => alias(name) = f
          case None    => first(key) = name
        }
      case _ => ()
    }
    val sb = new StringBuilder
    tidied.foreach {
      case Left(l) => sb.append(l).append('\n')
      case Right((kind, name, params, body, rest)) if !alias.contains(name) =>
        sb.append(kind).append(' ').append(name).append('(').append(params).append(')').append(rest).append('\n')
        body.foreach(l => sb.append(l).append('\n'))
        sb.append("end ").append(kind).append('\n')
      case _ => ()
    }
    if (sb.nonEmpty) sb.setLength(sb.length - 1)
    val tidy = sb.toString
    val named =
      if (alias.isEmpty) tidy
      else "\\bclosure[0-9]+\\b".r.replaceAllIn(tidy, m => java.util.regex.Matcher.quoteReplacement(alias.getOrElse(m.matched, m.matched)))
    tighten(named)
  }

  private val identifier = "[A-Za-z_][A-Za-z0-9_]*"

  /** A parameter the body never reads, renamed with a leading underscore: the device warns of an unused variable for
    * every one (a closure's env and args, a method's self, which every call passes), and not for a name so written. The
    * new name is one nothing in the function has. */
  private def quiet(params: String, body: Vector[String]): String = {
    if (params.trim.isEmpty) params
    else {
      val parts = params.split(",").map(_.trim).toVector
      val names = parts.map(_.takeWhile(c => c.isLetterOrDigit || c == '_'))
      val taken = mutable.Set[String]() ++ names.map(_.toLowerCase)
      def used(n: String): Boolean = {
        val re = ("(?i)(?<![A-Za-z0-9_.])" + java.util.regex.Pattern.quote(n) + "(?![A-Za-z0-9_])").r
        body.exists(l => re.findFirstIn(l).nonEmpty)
      }
      parts.zip(names).map { case (part, n) =>
        if (n.isEmpty || n.startsWith("_") || used(n)) part
        else {
          var fresh = "_" + n
          var k = 1
          while (taken(fresh.toLowerCase) || used(fresh)) { fresh = "_" + n + k; k += 1 }
          taken += fresh.toLowerCase
          fresh + part.drop(n.length)
        }
      }.mkString(", ")
    }
  }
  private def paramNames(params: String): Set[String] =
    params.split(",").map(_.trim.takeWhile(c => c.isLetterOrDigit || c == '_').toLowerCase).filter(_.nonEmpty).toSet
  private val assignment = ("^\\s*(" + identifier + ") = (.*)$").r
  // what can be dropped unread: a name, a field or an index of one, a literal
  private val pureValue = ("^(?:" + identifier + "(?:\\." + identifier + "|\\[[0-9]+\\])*|-?[0-9.]+#?|\"(?:[^\"]|\"\")*\"|invalid|true|false)$").r
  private val callOnly = ("^" + identifier + "(?:\\." + identifier + ")*\\(.*\\)$").r

  /** A local a function sets and never reads (an unused variable, which the device warns about): its assignment goes
    * where what it is given is only read, and is left a call where it is a call. */
  private def dropUnusedLocals(body: Vector[String], params: Set[String]): Vector[String] = {
    val targets = body.collect { case assignment(v, _) => v.toLowerCase }.toSet -- params
    val unread = targets.filter { v =>
      val re = ("(?i)(?<![A-Za-z0-9_.])" + java.util.regex.Pattern.quote(v) + "(?![A-Za-z0-9_])").r
      body.forall { l =>
        l match {
          case assignment(t, rhs) if t.toLowerCase == v => re.findFirstIn(rhs).isEmpty
          case _ => re.findFirstIn(l).isEmpty
        }
      }
    }
    if (unread.isEmpty) body
    else body.flatMap {
      case l @ assignment(t, rhs) if unread(t.toLowerCase) =>
        val indent = l.takeWhile(_ == ' ')
        if (pureValue.matches(rhs.trim)) None
        else if (callOnly.matches(rhs.trim) && closes(rhs.trim)) Some(indent + rhs.trim)
        else Some(l)
      case l => Some(l)
    }
  }

  /** Whether a call's first parenthesis is closed by its last (`f(a)`, not `f(a) + g(b)`), strings aside. */
  private def closes(call: String): Boolean = {
    var depth = 0
    var inString = false
    var i = call.indexOf('(')
    var closedAt = -1
    while (i < call.length && closedAt < 0) {
      val c = call(i)
      if (c == '"') inString = !inString
      else if (!inString && c == '(') depth += 1
      else if (!inString && c == ')') { depth -= 1; if (depth == 0) closedAt = i }
      i += 1
    }
    closedAt == call.length - 1
  }

  /** The program's lines without what the device does not need to read them: their indentation, the spaces after
    * commas and around `=`, a block `if`'s `then`, and `self` (the object a method or closure runs on) as `_s`, where
    * nothing else has that name. Strings and comments are left as they are. */
  private def tighten(code: String): String = {
    val selfRe = "(?<![A-Za-z0-9_.])self(?![A-Za-z0-9_])".r
    val renameSelf = "(?<![A-Za-z0-9_.])_s(?![A-Za-z0-9_])".r.findFirstIn(code).isEmpty
    def tightCode(seg: String): String = {
      val renamed =
        if (!renameSelf) seg
        else selfRe.replaceAllIn(seg, m => {
          // (`{ self: …` names an environment's field, which keeps its name)
          val before = seg.substring(0, m.start).reverse.dropWhile(_ == ' ')
          val after = seg.substring(m.end).dropWhile(_ == ' ')
          if (after.startsWith(":") && (before.startsWith("{") || before.startsWith(","))) "self" else "_s"
        })
      renamed.replace(", ", ",").replace(" = ", "=")
    }
    code.split("\n", -1).map { line0 =>
      val line = line0.dropWhile(c => c == ' ' || c == '\t')
      val sb = new StringBuilder
      var i = 0
      var start = 0
      var comment = false
      while (i < line.length && !comment) {
        line(i) match {
          case '"' =>
            sb.append(tightCode(line.substring(start, i)))
            var j = i + 1
            while (j < line.length && !(line(j) == '"' && !(j + 1 < line.length && line(j + 1) == '"'))) j += (if (line(j) == '"') 2 else 1)
            sb.append(line.substring(i, math.min(j + 1, line.length)))
            i = j + 1
            start = i
          case '\'' =>
            comment = true
          case _ => i += 1
        }
      }
      if (comment) sb.append(tightCode(line.substring(start, i))).append(line.substring(i))
      else if (start < line.length) sb.append(tightCode(line.substring(start)))
      val out = sb.toString
      val lower = out.toLowerCase
      if (!comment && (lower.startsWith("if ") || lower.startsWith("else if ")) && lower.endsWith(" then")) out.dropRight(5)
      else out
    }.mkString("\n")
  }

  /** The program's files: `App.brs`, then `App2.brs`, `App3.brs` and on. */
  def partName(i: Int): String = if (i == 0) "App.brs" else "App" + (i + 1) + ".brs"

  /** The scene's line loading part `i`. */
  def appScript(i: Int): String = s"""<script type="text/brightscript" uri="pkg:/components/${partName(i)}" />"""

  /** `code` cut before a top-level function or sub once a part has passed `limit` bytes (a function is never cut, so a
    * part is over the limit by at most the function that ends it). The parts share the scene's scope. */
  def split(code: String, limit: Int): List[String] = {
    val parts = mutable.ListBuffer[String]()
    val part = new StringBuilder
    var bytes = 0
    code.split("\n", -1).foreach { line =>
      if (bytes > limit && (line.startsWith("function ") || line.startsWith("sub "))) {
        parts += part.toString
        part.clear()
        bytes = 0
      }
      part.append(line).append('\n')
      bytes += line.getBytes(StandardCharsets.UTF_8).length + 1
    }
    parts += part.toString
    parts.toList
  }
}
