package universal.compiler

import scala.quoted.*
import scala.tasty.inspector.*
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import scala.collection.mutable

/** universal-ui's Roku compiler: Scala 3 TASTy → a Roku channel.
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
) extends Inspector:

  private val out      = new StringBuilder   // emitted app BrightScript
  private val closures = new StringBuilder    // defunctionalised lambda bodies
  private val todos    = mutable.ListBuffer[String]()

  def unsupported: List[String] = todos.toList.distinct

  def inspect(using q: Quotes)(tastys: List[Tasty[q.type]]): Unit =
    import q.reflect.*

    // ── helpers ──────────────────────────────────────────────────────────
    def hasNative(s: Symbol): Boolean =
      s.exists && s.annotations.exists(a => a.tpe.typeSymbol.fullName == "universal.compiler.api.native")

    val mainFqns = Set("scala.main", "scala.annotation.main", "scala.annotation.newMain")
    def isMain(s: Symbol): Boolean =
      s.exists && s.annotations.exists(a => mainFqns.contains(a.tpe.typeSymbol.fullName))

    def isBuiltin(s: Symbol): Boolean =
      s.exists && (s.fullName.startsWith("scala.") || s.fullName.startsWith("java."))

    def unwrap(t: Term): Term = t match
      case Inlined(_, _, e) => unwrap(e)
      case Typed(e, _)      => unwrap(e)
      case _                => t

    def isUnit(t: Term): Boolean = unwrap(t) match
      case Literal(UnitConstant()) => true
      case Block(Nil, e)           => isUnit(e)
      case _                       => false

    def isSuper(t: Term): Boolean = t match
      case Super(_, _) => true
      case _           => false

    // BrightScript string literal: escape `"` by doubling; splice newlines.
    def brStr(s: String): String =
      val esc = s.replace("\"", "\"\"").replace("\n", "\" + Chr(10) + \"")
      "\"" + esc + "\""

    def emitConst(c: Constant): String = c match
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

    def todo(t: Tree): String =
      val label = t.getClass.getSimpleName
      todos += s"unsupported: $label"
      s"RtTodo(${brStr(label)})"

    // Class-emission context (mirrors JsEmitter): the class whose method we're
    // emitting; members resolve through the `self` param.
    var currentClass: Symbol = Symbol.noSymbol
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
    def brIdent(name: String): String =
      val cleaned = name.map(c => if (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' then c else '_')
      if cleaned.nonEmpty && cleaned.head >= '0' && cleaned.head <= '9' then "_" + cleaned else cleaned
    def modulePrefix(s: Symbol): String =
      val raw = s.name.stripSuffix("$")
      val base = brIdent(raw)
      if userClassNames.contains(raw) then base + "Obj" else base
    def isEnumLike(s: Symbol): Boolean =
      s.exists && (s.flags.is(Flags.Enum)
        || (s.companionClass.exists && s.companionClass.flags.is(Flags.Enum))
        || (s.companionModule.exists && s.companionModule.flags.is(Flags.Enum)))
    def isUserModule(cd: ClassDef): Boolean =
      val s = cd.symbol
      s.exists && s.flags.is(Flags.Module) && !s.flags.is(Flags.Case) && !hasNative(s)
        && !isEnumLike(s) && !s.name.stripSuffix("$").endsWith("$package") && !isBuiltin(s)
        && !(s.companionClass.exists && s.companionClass.flags.is(Flags.Case))

    def isUserClassSym(s: Symbol): Boolean =
      s.exists && s.isClassDef && !s.flags.is(Flags.Module) && !s.flags.is(Flags.Case)
        && !s.flags.is(Flags.Trait) && !s.flags.is(Flags.Enum) && !hasNative(s) && !isBuiltin(s)
    def isUserClass(cd: ClassDef): Boolean = isUserClassSym(cd.symbol)

    def userMember(s: Symbol): Boolean =
      s.exists && !s.flags.is(Flags.Synthetic) && !s.flags.is(Flags.Artifact)

    def isFacade(s: Symbol): Boolean =
      s.exists && (hasNative(s) || hasNative(s.owner))
    def isFacadeCall(fn: Term): Boolean = isFacade(fn.symbol)

    def repeatedElemsOpt(t: Term): Option[List[Term]] = unwrap(t) match
      case Repeated(es, _) => Some(es)
      case _               => None
    def flattenVarargs(args: List[Term]): List[Term] =
      args.flatMap(a => repeatedElemsOpt(a).getOrElse(List(a)))

    def stripTypeApply(f: Term): Term = f match
      case TypeApply(inner, _) => stripTypeApply(inner)
      case _                   => f

    def isListApply(fn: Term): Boolean = fn match
      case Select(q, "apply") =>
        q.symbol.name == "List" || (q match { case Ident("List") => true; case _ => false })
      case _ => false

    def isDefaultArg(t: Term): Boolean = unwrap(t) match
      case Select(_, n) if n.contains("$default$") => true
      case Ident(n) if n.contains("$default$")     => true
      case Apply(f, _)                             => isDefaultArg(f)
      case TypeApply(f, _)                         => isDefaultArg(f)
      case NamedArg(_, v)                          => isDefaultArg(v)
      case _                                       => false

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
      if name.exists(brOpFrag.contains) then
        name.iterator.map(c => brOpFrag.getOrElse(c, c.toString)).mkString
      else name

    def brSafe(name: String): String =
      val s = brIdent(brMangleOps(name.replace("$", "_")))
      if brReserved(s.toLowerCase) then s + "_" else s


    /** BrightScript has ONE name per function, Scala allows overloads
     *  (`Transaction.commit()` and `Transaction.commit(v)`). Overloaded members
     *  get an arity suffix so each variant keeps its own function; a name that
     *  is not overloaded is untouched, so nothing else in the output moves.
     */
    def overloadSuffix(s: Symbol): String =
      if !s.exists || !s.owner.exists then ""
      else
        val sameName =
          try s.owner.declaredMethods.count(_.name == s.name)
          catch case _: Throwable => 1
        if sameName <= 1 then ""
        else "__" + s.paramSymss.flatten.count(_.isTerm).toString

    /** The emitted name of a class/object member, disambiguated if overloaded. */
    def memberName(s: Symbol, name: String): String = brSafe(name) + overloadSuffix(s)


    def termParams(pcs: List[ParamClause]): List[String] =
      pcs.collect { case c: TermParamClause => c }.flatMap(_.params).map(p => brSafe(p.name))
    def hasTermClause(pcs: List[ParamClause]): Boolean =
      pcs.exists { case _: TermParamClause => true; case _ => false }

    val binOps = Map(
      "==" -> "=", "!=" -> "<>", "+" -> "+", "-" -> "-", "*" -> "*",
      "/" -> "/", "%" -> "mod", "<" -> "<", ">" -> ">", "<=" -> "<=", ">=" -> ">=",
      "&&" -> "and", "||" -> "or"
    )

    def isCollType(t: Term): Boolean =
      val s = t.tpe.dealias.typeSymbol
      s.exists && s.fullName.startsWith("scala.collection.")

    // A function VALUE (`T => R`, i.e. scala.FunctionN). Such values are
    // defunctionalised {fn, env} closures and must be invoked via RtInvoke.
    def isFunctionType(tpe: TypeRepr): Boolean =
      val s = tpe.widen.dealias.typeSymbol
      s.exists && s.fullName.matches("scala\\.Function\\d+")

    // Option lowers to a plain nullable (Some(x)→x, None→invalid), so its ops
    // (map/flatMap/fold/getOrElse/…) become invalid-checks via runtime helpers.
    def isOptionType(t: Term): Boolean =
      val s = t.tpe.widen.dealias.typeSymbol
      s.exists && (s.fullName == "scala.Option"
        || s.fullName.endsWith(".Some") || s.fullName.endsWith(".None"))
    def isTryType(t: Term): Boolean =
      val s = t.tpe.widen.dealias.typeSymbol
      s.exists && (s.fullName == "scala.util.Try"
        || s.fullName.endsWith(".Success") || s.fullName.endsWith(".Failure"))
    def isEitherType(t: Term): Boolean =
      val s = t.tpe.widen.dealias.typeSymbol
      s.exists && (s.fullName == "scala.util.Either"
        || s.fullName.endsWith(".Right") || s.fullName.endsWith(".Left"))

    // `opt.fold(ifEmpty)(f)` — two arg clauses (a leading type-arg on `fold`):
    // returns (receiver, ifEmpty, f) when `receiver` is an Option.
    def optionFold(x: Term): Option[(Term, Term, Term)] =
      def peel(y: Term): Term = y match { case TypeApply(z, _) => peel(z); case _ => y }
      x match
        case Apply(inner, List(f)) =>
          peel(inner) match
            case Apply(sel, List(ifEmpty)) =>
              peel(sel) match
                case Select(recv, "fold") if isOptionType(recv) => Some((recv, ifEmpty, f))
                case _ => None
            case _ => None
        case _ => None

    // Case-class construction → an assoc `{ _cls, field: … }` (the `_cls` lets
    // `match` / the runtime dispatch on type). Only for actual construction.
    def caseApply(resultTpe: TypeRepr): Option[List[String]] =
      val ts = resultTpe.typeSymbol
      if ts.exists && ts.flags.is(Flags.Case) && !isBuiltin(ts) then Some(ts.caseFields.map(_.name)) else None
    def isCaseCtor(fn: Term, resultTpe: TypeRepr): Boolean = fn match
      case Select(New(_), _) => true
      case Select(q, "apply") =>
        val comp = resultTpe.typeSymbol.companionModule
        comp.exists && q.symbol.exists && q.symbol == comp
      case _ => false

    // ── closures (defunctionalisation) ──────────────────────────────────────
    var closureCounter = 0
    var matchCounter = 0
    def asClosure(t: Term): Option[DefDef] =
      def peel(x: Term): Term = x match
        case Inlined(_, _, e) => peel(e)
        case Typed(e, _)      => peel(e)
        case Block(Nil, e)    => peel(e)
        case _                => x
      peel(t) match
        case Block(List(dd @ DefDef(_, _, _, _)), Closure(_, _)) => Some(dd)
        case _                                                   => None

    // Emit a lambda as a top-level `function closureN(env, args)` and return the
    // `{ fn: closureN, env: { self: self } }` value. `env.self` is the enclosing
    // instance (invalid at top level); params bind from the `args` array.
    // Free variables of a lambda: Idents referencing an enclosing def's
    // param/local (not `self`-members, not its own params/locals, not
    // modules/facades). BrightScript can't capture, so these ride in the env.
    def freeVarsIn(body: Tree, ownParams: Set[Symbol]): List[(String, Symbol)] =
      val bound = mutable.Set[Symbol]() ++ ownParams
      val found = mutable.LinkedHashMap[Symbol, String]()
      val trav = new TreeTraverser:
        override def traverseTree(t: Tree)(owner: Symbol): Unit =
          t match
            case v: ValDef => bound += v.symbol
            // a name a pattern binds (`case (name, e) =>`) is local to the case, not captured from outside
            case b: Bind => bound += b.symbol
            case id: Ident =>
              val s = id.symbol
              val parentParam = inParentArgs && s.exists && s.flags.is(Flags.ParamAccessor) && currentClass.exists && s.owner == currentClass
              if parentParam && !bound.contains(s) then found.getOrElseUpdate(s, brSafe(id.name))
              else if s.exists && s.isTerm && !s.isDefDef && !bound.contains(s)
                 && !isFacade(s) && !moduleSyms.contains(s.owner)
                 && s.owner.exists && !s.owner.isClassDef && (s.owner.flags.is(Flags.Method) || s.owner.isValDef)
                 && anonCaptureRef(s).isEmpty
              then found.getOrElseUpdate(s, brSafe(id.name))
            case sel @ Select(q: This, n) if inParentArgs && sel.symbol.exists && sel.symbol.flags.is(Flags.ParamAccessor)
                && currentClass.exists && sel.symbol.owner == currentClass && !bound.contains(sel.symbol) =>
              found.getOrElseUpdate(sel.symbol, brSafe(n))
            case _ =>
          super.traverseTree(t)(owner)
      trav.traverseTree(body)(Symbol.noSymbol)
      found.toList.map((s, nm) => (nm, s))

    def freeVars(dd: DefDef): List[(String, Symbol)] =
      val own = dd.paramss.collect { case c: TermParamClause => c }.flatMap(_.params).map(_.symbol).toSet
      dd.rhs.map(r => freeVarsIn(r, own)).getOrElse(Nil)

    def emitClosure(dd: DefDef): String =
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
      if binds.nonEmpty then closures.append(binds).append("\n")
      if body.nonEmpty then closures.append(body).append("\n")
      if !wantRet then closures.append("  return invalid\n")
      closures.append("end function\n\n")
      val envSelf = if currentClass.exists then "self" else "invalid"
      // A captured box / thunk travels as itself (raw name), never unboxed.
      val envFvs = fvs.map { case (nm, _) => s", fv_$nm: $nm" }.mkString
      s"{ fn: $name, env: { self: $envSelf$envFvs } }"

    /** A by-name argument: a zero-arg closure over the expression, forced by
     *  the callee at each use. Passing a by-name parameter straight through
     *  hands over the existing thunk instead of wrapping it again. */
    def emitThunk(body0: Term): String =
      val body = unwrap(body0)
      body match
        case id: Ident if isByNameParam(id.symbol) && anonCaptureRef(id.symbol).isEmpty => brSafe(id.name)
        case _ =>
          val n = closureCounter; closureCounter += 1
          val name = s"closure$n"
          val fvs = freeVarsIn(body, Set.empty)
          val binds = fvs.map { case (nm, _) => s"  $nm = env.fv_$nm" }.mkString("\n")
          val unitBody = body.tpe.widen =:= TypeRepr.of[Unit]
          val code = emitInto(body, if unitBody then "" else skReturn)
          closures.append(s"function $name(env as object, args as object) as dynamic\n")
          closures.append("  self = env.self\n")
          if binds.nonEmpty then closures.append(binds).append("\n")
          if code.trim.nonEmpty then closures.append(code).append("\n")
          closures.append("  return invalid\n")
          closures.append("end function\n\n")
          val envSelf = if currentClass.exists then "self" else "invalid"
          val envFvs = fvs.map { case (nm, _) => s", fv_$nm: $nm" }.mkString
          s"{ fn: $name, env: { self: $envSelf$envFvs } }"

    def emitArgVal(t: Term): String = asClosure(t) match
      case Some(dd) => emitClosure(dd)
      case None     => emitTerm(t)

    // Positional args for user calls / ctors. Omitted defaults are REAL
    // arguments to an emitted function (BrightScript checks arity), so they
    // are emitted — not dropped as they are for facade constructor options.
    def emitArgList(args: List[Term], callee: Symbol = Symbol.noSymbol): List[String] =
      emitOrdered(argParts(args, callee)).filter(_ != dropArg)
    def emitArgs(args: List[Term], callee: Symbol = Symbol.noSymbol): String =
      emitArgList(args, callee).mkString(", ")

    // A @native class construction → one options assoc keyed by parameter
    // name, which the platform runtime's constructor reads by field.
    def emitFacadeCtorArgs(ctor: Symbol, args: List[Term]): String =
      val params = if ctor.exists then ctor.paramSymss.flatten.filter(_.isTerm) else Nil
      val parts = mutable.ListBuffer[String]()
      var i = 0
      args.foreach { a =>
        if !isDefaultArg(a) then
          a match
            case NamedArg(n, v) => parts += s"$n: ${emitArgVal(v)}"
            case other =>
              params.lift(i) match
                case Some(p) => parts += s"${p.name}: ${emitArgVal(other)}"
                case None    => parts += emitArgVal(other)
        i += 1
      }
      "{ " + parts.mkString(", ") + " }"

    def emitFacadeArgs(args: List[Term]): String =
      val kept = args.filterNot(isDefaultArg)
      val pos   = mutable.ListBuffer[String]()
      val named = mutable.ListBuffer[String]()
      kept.foreach {
        case NamedArg(n, v) => named += s"$n: ${emitArgVal(v)}"
        case other          => pos += emitArgVal(other)
      }
      if named.isEmpty then pos.mkString(", ")
      else if pos.isEmpty then "{ " + named.mkString(", ") + " }"
      else pos.mkString(", ") + ", { " + named.mkString(", ") + " }"

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
    def topDecl(s: Symbol): Symbol =
      val chain = overriddenUser(s)
      if chain.isEmpty then s else chain.last
    def slotName(s: Symbol): String =
      val top = topDecl(s)
      val base = brSafe(top.name)
      val owner = top.owner
      val arities =
        if owner.exists && userTypeDefs.contains(owner) then
          owner.typeRef.baseClasses.filter(userTypeDefs.contains)
            .flatMap(b => try b.declaredMethods.filter(_.name == top.name) catch case _: Throwable => Nil)
            .map(arityOf).distinct
        else Nil
      val named0 = if arities.size > 1 then base + "__" + arityOf(top) else base
      // A method named like a FIELD of the same object (case class `AnimateIn(easing: Easing)` with
      // `def easing(e)`) would share the field's key and be overwritten by its value.
      val fieldClash = top.isDefDef && owner.exists && userTypeDefs.contains(owner) &&
        owner.typeRef.baseClasses.filter(userTypeDefs.contains).exists(b =>
          try b.declaredFields.exists(f => f.name == top.name) catch case _: Throwable => false)
      val named = if fieldClash then named0 + "__m" else named0
      if aaIfaceMethods.contains(named.toLowerCase) then named + "_" else named

    // A `val` (or case-class param) that overrides a `def` — `override val
    // priority` in FunctionReaction over `def priority` in Reaction. Its slot
    // must hold a getter, so the value itself is stored under `__v_<name>`.
    def isVirtualField(s: Symbol): Boolean =
      s.exists && !s.isDefDef && s.owner.exists && isModel(s.owner) && !s.flags.is(Flags.Lazy)
        && overriddenUser(s).exists(_.isDefDef)
    def fieldKey(s: Symbol): String =
      if isVirtualField(s) then "__v_" + brSafe(s.name) else brSafe(s.name)
    def isModelMember(s: Symbol): Boolean =
      s.exists && s.owner.exists && isModel(s.owner) && !s.isClassConstructor
        && ((s.isDefDef && !s.flags.is(Flags.FieldAccessor)) || s.flags.is(Flags.Lazy) || isVirtualField(s))
    def isStaticModelMember(s: Symbol): Boolean =
      s.flags.is(Flags.Private) || s.flags.is(Flags.Final)
    def modelImplName(s: Symbol): String =
      if s.flags.is(Flags.Lazy) then s"${typeName(s.owner)}_${brSafe(s.name)}__lazy"
      else if isVirtualField(s) then s"${typeName(s.owner)}_${brSafe(s.name)}__get"
      else s"${typeName(s.owner)}_${memberName(s, s.name)}"

    def emittableMethod(dd: DefDef): Boolean =
      dd.rhs.isDefined && dd.name != "<init>" && !dd.symbol.flags.is(Flags.FieldAccessor)
        && (userMember(dd.symbol) || dd.name.contains("$default$"))

    // A receiver that is safe to evaluate twice (`recv.m(recv, …)`): a local,
    // `this`, a literal, or a plain field chain. Anything else — a call, a
    // cached module val, a lazy getter — goes through `RtVcallN` instead.
    def isSimpleRecv(t: Term): Boolean = unwrap(t) match
      case This(_) | Literal(_) => true
      case id: Ident =>
        val sy = id.symbol
        sy.exists && !sy.isDefDef && !isModelMember(sy) && !(sy.owner.exists && sy.owner.flags.is(Flags.Module))
      case sel @ Select(q, _) =>
        val sy = sel.symbol
        sy.exists && !sy.isDefDef && !isModelMember(sy) && !(sy.owner.exists && sy.owner.flags.is(Flags.Module))
          && isSimpleRecv(q)
      case _ => false

    def emitModelCall(recv: Option[Term], sy: Symbol, args: List[Term]): String =
      val recvPart: () => String = () => recv match
        case None                 => "self"
        case Some(r) if isThis(r) => "self"
        case Some(r)              => emitTerm(r)
      val all = emitOrdered(recvPart :: argParts(args, sy)).filter(_ != dropArg)
      val recvStr = all.head
      val argList = all.tail
      val tail = argList.map(", " + _).mkString
      if isStaticModelMember(sy) then s"${modelImplName(sy)}($recvStr$tail)"
      else
        val simple = recv match
          case None    => true
          case Some(r) => isThis(r) || isSimpleRecv(r) || isTrivialExpr(recvStr)
        if simple || argList.size > 8 then s"$recvStr.${slotName(sy)}($recvStr$tail)"
        else s"RtVcall${argList.size}($recvStr, ${brStr(slotName(sy))}$tail)"

    // ── arguments ────────────────────────────────────────────────────────────
    def isTermClause(c: List[Symbol]): Boolean = c.headOption.forall(_.isTerm)
    def isByNameParam(p: Symbol): Boolean =
      if !p.exists || p.isDefDef then false
      else
        try p.tree match
          case vd: ValDef => vd.tpt.tpe match { case ByNameType(_) => true; case _ => false }
          case _          => false
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
    def emitOrdered(parts: List[() => String]): List[String] =
      val outs = mutable.ArrayBuffer[String]()
      parts.foreach { part =>
        val before = if pendingStack.nonEmpty then pendingStack.top.size else 0
        val v = part()
        if pendingStack.nonEmpty && pendingStack.top.size > before then
          var at = before
          for j <- outs.indices do
            if outs(j) != dropArg && !isTrivialExpr(outs(j)) then
              anfCounter += 1
              val tmp = s"_a$anfCounter"
              pendingStack.top.insert(at, s"  $tmp = ${outs(j)}")
              at += 1
              outs(j) = tmp
        outs += v
      }
      outs.toList

    /** One deferred emitter per argument. A by-name parameter receives a
     *  zero-arg closure (a thunk); an omitted default is a real argument for a
     *  function the emitter writes, and dropped for anything else (a facade,
     *  a runtime function), whose runtime supplies it. */
    def isRepeatedParam(p: Symbol): Boolean =
      try p.tree match
        case vd: ValDef =>
          val tp = vd.tpt.tpe
          tp.typeSymbol.name.contains("repeated") || tp.show.endsWith("*")
            || (tp match { case AppliedType(t, _) => t.typeSymbol.name.contains("repeated"); case _ => false })
        case _ => false
      catch case _: Throwable => false
    def isSplice(t: Term): Boolean = t match
      case Typed(_, tpt) => tpt.tpe.typeSymbol.name == "<repeated>" && repeatedElemsOpt(t).isEmpty
      case _             => false
    /** A varargs argument as one array: its elements, or the spliced sequence itself (`xs*`). */
    def varargsArray(args: List[Term]): String =
      if args.size == 1 && isSplice(args.head) then emitTerm(unwrap(args.head))
      else "[" + flattenVarargs(args).map(emitArgVal).mkString(", ") + "]"

    def argParts(args: List[Term], callee: Symbol): List[() => String] =
      val params =
        if userEmittedCallee(callee) && callee.isDefDef then callee.paramSymss.filter(isTermClause).flatten else Nil
      val repeatedAt =
        val byParam = params.indexWhere(isRepeatedParam)
        if byParam >= 0 then byParam
        else if params.nonEmpty then args.indexWhere(a => repeatedElemsOpt(a).isDefined || isSplice(a))
        else -1
      if repeatedAt >= 0 then
        val fixed = argParts(args.take(repeatedAt), Symbol.noSymbol).zipWithIndex.map { case (part, i) =>
          val arg = args(i)
          val inner = arg match { case NamedArg(_, v) => v; case other => other }
          if params.lift(i).exists(isByNameParam) && !isDefaultArg(arg) then (() => emitThunk(inner)) else part
        }
        return fixed :+ (() => varargsArray(args.drop(repeatedAt)))
      val flat = flattenVarargs(args)
      flat.zipWithIndex.map { case (arg, i) => () =>
        val inner = arg match { case NamedArg(_, v) => v; case other => other }
        val byName = params.lift(i).exists(isByNameParam)
        if isDefaultArg(arg) then
          val gs = unwrap(inner).symbol
          val owner = if gs.exists && gs.owner.exists then gs.owner else Symbol.noSymbol
          if !isUserDefaultOwner(owner) then dropArg
          else
            val term = if owner.flags.is(Flags.Module) then defaultGetterRhs(gs).getOrElse(inner) else inner
            if byName then emitThunk(term) else emitArgVal(term)
        else if byName && isStableValue(inner) && !byNameIsFunction(params.lift(i)) then emitArgVal(inner)
        else if byName then emitThunk(inner)
        else emitArgVal(inner)
      }

    def isStableValue(t: Term): Boolean = unwrap(t) match
      case Literal(_) => true
      case _: This    => true
      case id: Ident =>
        val sy = id.symbol
        sy.exists && sy.isValDef && !sy.flags.is(Flags.Mutable) && !sy.flags.is(Flags.Lazy)
          && !boxedVars.contains(sy) && !isByNameParam(sy) && !isModelMember(sy)
          && !(sy.owner.exists && sy.owner.flags.is(Flags.Module))
      case _ => false
    def byNameIsFunction(p: Option[Symbol]): Boolean = p.exists { ps =>
      try ps.tree match
        case vd: ValDef => vd.tpt.tpe match
          case ByNameType(u) => isFunctionType(u)
          case _             => false
        case _ => true
      catch case _: Throwable => true
    }

    def clauseParamsOf(ap: Apply): List[Symbol] =
      def depth(f: Term): Int = stripTypeApply(f) match
        case Apply(g, _) => 1 + depth(g)
        case _           => 0
      val sy = innermostFnSym(ap.fun)
      if !sy.exists || !sy.isDefDef then Nil
      else sy.paramSymss.filter(isTermClause).lift(depth(ap.fun)).getOrElse(Nil)

    // ── `this`, outer instances and captures of local classes ────────────────
    def thisRef(target: Symbol): String =
      if !currentClass.exists || !target.exists then "self"
      else
        var c = currentClass
        var ref = "self"
        var guard = 0
        while c.exists && !(c == target || c.typeRef.baseClasses.contains(target)) && anonOuter.contains(c) && guard < 16 do
          ref = ref + ".__outer"
          c = anonOuter(c)
          guard += 1
        ref
    def anonCaptureRef(sy: Symbol): Option[String] =
      var c = currentClass
      var ref = "self"
      var guard = 0
      var found: Option[String] = None
      while found.isEmpty && c.exists && guard < 16 do
        if anonCaptures.get(c).exists(_.contains(sy)) then found = Some(s"$ref.__fv_${brSafe(sy.name)}")
        else anonOuter.get(c) match
          case Some(o) =>
            ref = ref + ".__outer"
            c = o
          case None => c = Symbol.noSymbol
        guard += 1
      found
    def captureValue(sy: Symbol): String = anonCaptureRef(sy).getOrElse(brSafe(sy.name))
    def isOwnedWithin(sy: Symbol, cls: Symbol): Boolean =
      try
        var o = sy.owner
        var g = 0
        var hit = false
        while !hit && o.exists && !o.isPackageDef && g < 64 do
          if o == cls then hit = true
          o = o.owner
          g += 1
        hit
      catch case _: Throwable => false

    // ── shapes ───────────────────────────────────────────────────────────────
    /** A call into something the emitter does not write (a facade, a runtime
     *  function): its hoisted default-arg temps are stripped. */
    def dropsDefaults(e: Term): Boolean =
      def innermost(t: Term): Term = unwrap(t) match
        case Apply(f, _)     => innermost(f)
        case TypeApply(f, _) => innermost(f)
        case other           => other
      unwrap(e) match
        case ap: Apply =>
          innermost(ap) match
            case Select(New(tpt), _) =>
              val ts = tpt.tpe.typeSymbol
              !(userTypeDefs.contains(ts) || isUserClassSym(ts))
            case f => !userEmittedCallee(f.symbol)
        case _ => false
    def isLocalClassInstantiation(cd: ClassDef, e: Term): Boolean =
      def peel(t: Term): Term = t match
        case Apply(f, _)     => peel(f)
        case TypeApply(f, _) => peel(f)
        case other           => other
      unwrap(e) match
        case ap: Apply =>
          peel(ap) match
            case Select(New(tpt), _) => tpt.tpe.typeSymbol == cd.symbol
            case _                   => false
        case _ => false
    /** Side-effect free and cheap — safe to evaluate unconditionally, as
     *  `_iif(c, a, b)` does for both arms. A CALL never is: reactify's
     *  `if useA() then a() else b()` must not register `b` as a dependency. */
    def pureValue(t: Term): Boolean = unwrap(t) match
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

    // ── stdlib recognisers ───────────────────────────────────────────────────
    def ownerFqn(sy: Symbol): String =
      if sy.exists && sy.owner.exists then sy.owner.fullName.stripSuffix("$") else ""
    def ownerIs(fn: Term, fqn: String): Boolean = ownerFqn(fn.symbol) == fqn
    def fnIs(fn: Term, owner: String, name: String): Boolean =
      fn.symbol.exists && fn.symbol.name == name && ownerIs(fn, owner)
    def fqnOf(tp: TypeRepr): String =
      val sy = tp.widen.dealias.typeSymbol
      if sy.exists then sy.fullName else ""
    def isSetTpe(tp: TypeRepr): Boolean =
      val f = fqnOf(tp)
      f.startsWith("scala.collection.immutable.Set") || f == "scala.collection.Set"
        || f.startsWith("scala.collection.immutable.HashSet") || f.startsWith("scala.collection.immutable.ListSet")
    def isMapTpe(tp: TypeRepr): Boolean =
      val f = fqnOf(tp)
      f.startsWith("scala.collection.immutable.Map") || f == "scala.collection.Map"
        || f.startsWith("scala.collection.immutable.HashMap") || f.startsWith("scala.collection.immutable.ListMap")
    def isListTpe(tp: TypeRepr): Boolean =
      val f = fqnOf(tp)
      f == "scala.collection.immutable.List" || f == "scala.collection.immutable.Seq"
        || f == "scala.collection.immutable.Vector" || f == "scala.collection.Seq"
    def elemIsUserType(recv: Term): Boolean =
      recv.tpe.widen.dealias.typeArgs.headOption.exists { ta =>
        val sy = ta.dealias.typeSymbol
        userTypeDefs.contains(sy) || isUserClassSym(sy)
      }
    def isRichNumWrapper(wrap: Term): Boolean =
      val sy = stripTypeApply(wrap).symbol
      sy.exists && Set("doubleWrapper", "intWrapper", "longWrapper", "floatWrapper", "shortWrapper",
        "byteWrapper", "charWrapper").contains(sy.name)
    def isArrowAssoc(arrow: Term): Boolean =
      val sy = stripTypeApply(arrow).symbol
      sy.exists && sy.name == "ArrowAssoc"
    /** The factory an `implicit class` generates (`ListVar(xs)`), if `sy` is one. */
    def implicitClassTarget(sy: Symbol): Symbol =
      if !(sy.exists && sy.isDefDef && sy.flags.is(Flags.Implicit)) then Symbol.noSymbol
      else
        val rt =
          try sy.tree match
            case dd: DefDef => dd.returnTpt.tpe.typeSymbol
            case _          => Symbol.noSymbol
          catch case _: Throwable => Symbol.noSymbol
        if rt.exists && rt.name == sy.name && (userTypeDefs.contains(rt) || isUserClassSym(rt)) then rt
        else Symbol.noSymbol
    /** `c.copy(apply = …)`: shallow-copy the receiver, override the fields
     *  actually given (omitted ones arrive as `copy$default$N` getters). */
    def emitCaseCopy(recv: Term, args: List[Term]): String =
      val cls = recv.tpe.widen.dealias.typeSymbol
      val overrides = cls.caseFields.zip(flattenVarargs(args)).collect {
        case (f, arg) if !isDefaultArg(arg) =>
          val v = arg match { case NamedArg(_, x) => x; case o => o }
          s"${fieldKey(f)}: ${emitArgVal(v)}"
      }
      s"_caseCopy(${emitTerm(recv)}, { ${overrides.mkString(", ")} })"

    // `f(a)(b)` on a method with several term clauses is ONE call in the
    // emitted function (termParams flattens the clauses), so the Apply chain
    // flattens too. A call whose result is itself a function is left alone.
    def innermostFnSym(t: Term): Symbol = stripTypeApply(t) match
      case Apply(f, _) => innermostFnSym(f)
      case other       => other.symbol
    def isMultiClause(sy: Symbol): Boolean =
      sy.exists && sy.isDefDef && sy.paramSymss.count(c => c.headOption.exists(_.isTerm)) > 1
    def uncurry(fn: Term, args: List[Term]): (Term, List[Term]) =
      stripTypeApply(fn) match
        case Apply(inner, innerArgs) if isMultiClause(innermostFnSym(inner)) => uncurry(inner, innerArgs ++ args)
        case _ => (fn, args)

    // `super.m(…)` resolves statically to the implementation TASTy names.
    def superSel(f: Term): Option[Select] = f match
      case sel @ Select(spr, _) if isSuper(spr) => Some(sel)
      case TypeApply(inner, _)                  => superSel(inner)
      case _                                    => None

    // Default arguments. A getter on an INSTANCE (`fire$default$3` reads
    // `this.reactions()`) is emitted as a method and called; one on a companion
    // object (constructor defaults) has no `this`, so its body is inlined.
    def defaultGetterRhs(sy: Symbol): Option[Term] =
      try sy.tree match
        case dd: DefDef if dd.termParamss.forall(_.params.isEmpty) => dd.rhs
        case _                                                     => None
      catch case _: Throwable => None
    def emitDefaultArg(t: Term): String =
      val inner = unwrap(t) match { case NamedArg(_, v) => v; case x => x }
      val sy = inner.symbol
      if sy.exists && sy.owner.exists && sy.owner.flags.is(Flags.Module) then
        defaultGetterRhs(sy).map(emitArgVal).getOrElse(emitTerm(inner))
      else emitTerm(inner)

    // Scala `==` is structural for case classes and collections, not identity;
    // BrightScript `=` on two objects is a runtime type error. Primitives,
    // strings and facade values keep `=`; anything else goes through `_eq`.
    def isPrimLike(t: Term): Boolean =
      val sy = t.tpe.widen.dealias.typeSymbol
      sy.exists && (primitiveFqns.contains(sy.fullName) || hasNative(sy))

    // ── expression emission ─────────────────────────────────────────────────
    def emitRef(sym: Symbol, name: String): String =
      if name == "Nil" then "[]"
      else if name == "None" && sym.exists && sym.fullName.endsWith(".None") then "invalid"
      else if isFacade(sym) then name
      else if modelModuleOf(sym).isDefined then moduleAccessor(modelModuleOf(sym).get)
      // A symbol with no denotation (some local/synthetic Idents) has no owner —
      // `sym.owner` would throw NoDenotation.owner — so emit the bare safe name.
      else if !sym.exists || !sym.owner.exists then brSafe(name)
      else
        val boxed = boxedVars.contains(sym)
        val byName = isByNameParam(sym)
        val local = anonCaptureRef(sym).orElse(if boxed || byName then Some(brSafe(name)) else None)
        local match
          // a captured box reads its cell; a by-name parameter is forced on
          // EVERY use (Scala re-evaluates it — reactify's dependency tracking
          // depends on exactly that)
          case Some(ref) =>
            if boxed then ref + ".v" else if byName then s"RtForce($ref)" else ref
          case None =>
            if inParentArgs && sym.flags.is(Flags.ParamAccessor) && currentClass.exists && sym.owner == currentClass then brSafe(name)
            else if isModelMember(sym) && inCurrentClass(sym.owner) then emitModelCall(None, sym, Nil)
            else if inCurrentClass(sym.owner) then s"self.${fieldKey(sym)}"
            else moduleSyms.get(sym.owner) match
              case Some(prefix) => s"${prefix}_${memberName(sym, name)}()"
              case None         => brSafe(name)

    def rep(t: Term): List[Term] = unwrap(t) match
      case Repeated(es, _) => es
      case _               => Nil

    def emitStringInterp(outer: Term): Option[String] = unwrap(outer) match
      case Apply(Select(inner, "s"), List(argsArg)) =>
        unwrap(inner) match
          case Apply(Select(sc, "apply"), List(partsArg)) if sc.symbol.fullName.contains("StringContext") =>
            val parts = rep(partsArg).collect { case Literal(StringConstant(s)) => s }
            val args  = rep(argsArg)
            val pieces = mutable.ListBuffer[String]()
            for i <- args.indices do
              if i < parts.length && parts(i).nonEmpty then pieces += brStr(parts(i))
              pieces += s"_str(${emitTerm(args(i))})"
            if parts.length > args.length && parts.last.nonEmpty then pieces += brStr(parts.last)
            Some(if pieces.isEmpty then "\"\"" else pieces.mkString(" + "))
          case _ => None
      case _ => None

    def someArg(t: Term): Option[Term] = t match
      case Apply(fn, List(x)) =>
        stripTypeApply(fn) match
          case Select(q, "apply") if q.symbol.exists && q.symbol.fullName.endsWith(".Some") => Some(x)
          case _ => None
      case _ => None

    def mathNames: Set[String] =
      Set("sqrt", "pow", "sin", "cos", "tan", "atan", "exp", "log", "abs", "min", "max", "floor", "ceil", "round")
    /** `scala.math` onto BrightScript's numeric builtins. */
    def emitMath(name: String, args: List[Term]): String =
      val a = flattenVarargs(args).map(emitArgVal)
      name match
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
        case _       => s"Int(${a(0)} + 0.5)"

    /** Milliseconds per unit of a `scala.concurrent.duration` conversion (`5.seconds`). */
    def durationFactor(conv: Term, unit: String): Option[String] =
      val sy = stripTypeApply(conv).symbol
      if !(sy.exists && Set("DurationInt", "DurationLong", "DurationDouble").contains(sy.name)) then None
      else unit match
        case "nanoseconds" | "nanos" | "nanosecond" | "nano"      => Some("0.000001")
        case "microseconds" | "micros" | "microsecond" | "micro"  => Some("0.001")
        case "milliseconds" | "millis" | "millisecond" | "milli"  => Some("1")
        case "seconds" | "second"                                 => Some("1000")
        case "minutes" | "minute"                                 => Some("60000")
        case "hours" | "hour"                                     => Some("3600000")
        case "days" | "day"                                       => Some("86400000")
        case _                                                    => None

    /** Scala compiles `super.m(...)` inside a trait into a call to a synthetic accessor `pkg$T$$super$m`, which
      * only exists after mixin. Resolve it statically to the nearest user implementation in T's base classes. */
    def isSuperAccessor(sy: Symbol): Boolean = sy.exists && sy.name.contains("$$super$")
    def superAccessorImpl(sy: Symbol): Option[Symbol] =
      if !isSuperAccessor(sy) || !sy.owner.exists then None
      else
        val m = sy.name.substring(sy.name.lastIndexOf("$$super$") + "$$super$".length)
        val arity = arityOf(sy)
        sy.owner.typeRef.baseClasses.drop(1).iterator.filter(userTypeDefs.contains).flatMap { b =>
          userTypeDefs(b).body.collectFirst {
            case dd: DefDef if dd.name == m && emittableMethod(dd) && arityOf(dd.symbol) == arity => dd.symbol
          }
        }.nextOption()

    /** The constructor a `new C(...)` / parent `C(...)` call invokes. */
    def parentCtor(p: Tree): Symbol =
      def peel(t: Term): Symbol = t match
        case Apply(f, _)             => peel(f)
        case TypeApply(f, _)         => peel(f)
        case sel @ Select(New(_), _) => sel.symbol
        case Block(_, e)             => peel(e)
        case _                       => Symbol.noSymbol
      p match
        case t: Term => peel(t)
        case _       => Symbol.noSymbol
    def secondaryCtors(cls: Symbol): List[DefDef] =
      userTypeDefs.get(cls).map(_.body.collect {
        case dd: DefDef if dd.name == "<init>" && dd.symbol != cls.primaryConstructor && dd.rhs.isDefined => dd
      }).getOrElse(Nil)
    /** `C__init` for the primary constructor, `C__init__k` for the k-th secondary one. */
    def initName(cls: Symbol, ctor: Symbol): String =
      val k = if !ctor.exists || ctor == cls.primaryConstructor then 0 else secondaryCtors(cls).map(_.symbol).indexOf(ctor) + 1
      if k <= 0 then s"${typeName(cls)}__init" else s"${typeName(cls)}__init__$k"

    /** The singleton instance a model module's term or class refers to, if any. */
    def modelModuleOf(sy: Symbol): Option[Symbol] =
      if !sy.exists then None
      else if sy.isClassDef then Some(sy).filter(modelModules.contains)
      else if sy.flags.is(Flags.Module) then
        val mc = try sy.moduleClass catch case _: Throwable => Symbol.noSymbol
        Some(mc).filter(m => m.exists && modelModules.contains(m))
      else None
    /** A case object is initialised once at startup (rtMain) and then read straight from the global AA; any
      * other object (or code running inside a module's own initialisation) goes through its lazy accessor. */
    def moduleAccessor(mc: Symbol): String =
      if mc.flags.is(Flags.Case) && !(currentClass.exists && modelModules.contains(currentClass)) then
        s"ga.__mod_${typeName(mc)}"
      else s"${typeName(mc)}__module()"

    def emitApply(t: Term): String =
      someArg(t) match
        case Some(x) => return emitArgVal(x)
        case None    =>
      emitStringInterp(t) match
        case Some(s) => return s
        case None    =>
      // `opt.fold(ifEmpty)(f)` → runtime invalid-check helper.
      optionFold(t) match
        case Some((recv, ifEmpty, f)) =>
          return s"_optFold(${emitTerm(recv)}, ${emitArgVal(ifEmpty)}, ${emitArgVal(f)})"
        case None =>
      t match
        case Apply(fn00, args00) =>
          val (fn0, args) = uncurry(fn00, args00)
          val fn = stripTypeApply(fn0)
          if isListApply(fn) then
            return "[" + flattenVarargs(args).map(emitArgVal).mkString(", ") + "]"
          caseApply(t.tpe) match
            case Some(_) if isCaseCtor(fn, t.tpe) && isModel(t.tpe.typeSymbol) =>
              return s"${typeName(t.tpe.typeSymbol)}_new(${emitArgs(args, fn.symbol)})"
            case Some(fields) if isCaseCtor(fn, t.tpe) =>
              val flat = flattenVarargs(args).filterNot(isDefaultArg)
              val fs = fields.zip(flat).map((f, a) => s"$f: ${emitArgVal(a)}")
              val fieldList = fields.take(flat.size).map(f => brStr(f)).mkString(", ")
              return "{ " + (s"_cls: ${brStr(t.tpe.typeSymbol.name)}" :: s"_fields: [$fieldList]" :: fs).mkString(", ") + " }"
            case _ =>
          fn match
            // ── stdlib the emitter models directly (runtime shims) ──
            case _ if fnIs(fn, "scala.Option", "apply") && args.size == 1 => emitArgVal(args.head)
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
            case Select(Apply(arrow, List(k)), "->" | "$minus$greater") if isArrowAssoc(arrow) && args.size == 1 =>
              s"Tuple2(${emitTerm(k)}, ${emitArgVal(args.head)})"
            case _ if fn.symbol.exists && fn.symbol.name == "assertFailed" =>
              s"RtThrow(AssertionError_new(${if args.isEmpty then "invalid" else emitArgVal(args.head)}))"
            case _ if fnIs(fn, "scala.Predef", "assert") =>
              s"RtAssert(${emitArgVal(args.head)}${if args.size > 1 then ", " + emitThunk(args(1)) else ""})"
            case _ if fn.symbol.exists && fn.symbol.name == "synchronized" && args.size == 1 =>
              s"RtSynchronized(${emitThunk(args.head)})"
            case Select(recv, "getOrElse") if isOptionType(recv) =>
              val d = args.head match { case NamedArg(_, x) => x; case o => o }
              if pureValue(d) then s"_getOrElse(${emitTerm(recv)}, ${emitArgVal(d)})"
              else s"_getOrElseThunk(${emitTerm(recv)}, ${emitThunk(d)})"
            case Select(recv, "orElse") if isOptionType(recv) =>
              s"_optOrElse(${emitTerm(recv)}, ${emitThunk(args.head)})"
            case Select(recv, "contains") if isOptionType(recv) =>
              if isModuleOperand(args.head) then s"RtIdentity(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              else s"_optContains(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(q, "apply") if isSetTpe(t.tpe) && q.symbol.exists && q.symbol.flags.is(Flags.Module) =>
              s"_distinct([${flattenVarargs(args).map(emitArgVal).mkString(", ")}])"
            case Select(q, "apply") if isMapTpe(t.tpe) && q.tpe.widen.typeSymbol.flags.is(Flags.Module) =>
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
              s"(Instr(1, ${emitTerm(recv)}, ${emitArgVal(args.head)}) - 1)"
            case Select(recv, "+" | "incl") if isSetTpe(recv.tpe) && args.size == 1 =>
              s"_setAdd(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "-" | "excl") if isSetTpe(recv.tpe) && args.size == 1 =>
              s"_setRemove(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "++" | "union" | "concat") if isSetTpe(recv.tpe) =>
              s"_setUnion(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "diff" | "--") if isSetTpe(recv.tpe) =>
              s"_setDiff(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "contains") if isSetTpe(recv.tpe) =>
              s"_setHas(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "get" | "apply") if isMapTpe(recv.tpe) && args.size == 1 =>
              s"_mapGet(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "contains") if isMapTpe(recv.tpe) =>
              s"_mapHas(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "+" | "updated") if isMapTpe(recv.tpe) =>
              if args.size == 1 then s"_mapPutT(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              else s"_mapPut(${emitTerm(recv)}, ${emitArgVal(args(0))}, ${emitArgVal(args(1))})"
            case Select(recv, "-" | "removed") if isMapTpe(recv.tpe) && args.size == 1 =>
              s"_mapRemove(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "sorted") if isCollType(recv) =>
              if elemIsUserType(recv) then s"_sortedOrdered(${emitTerm(recv)})" else s"_sortedPrim(${emitTerm(recv)})"
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
              superAccessorImpl(fn.symbol) match
                case Some(impl) => s"${modelImplName(impl)}(self${emitArgList(args, impl).map(", " + _).mkString})"
                case None       => "invalid"
            // Object-model dispatch (see "object model" above).
            case Select(recv, _) if isModelMember(fn.symbol) && !isFacade(fn.symbol) && !isSuper(recv) =>
              emitModelCall(Some(recv), fn.symbol, args)
            case Ident(_) if isModelMember(fn.symbol) && !isFacade(fn.symbol) =>
              emitModelCall(None, fn.symbol, args)
            // `x == null` / `x ne null` on an object: a direct test, not a call.
            case Select(recv, op @ ("==" | "!=" | "eq" | "ne")) if args.length == 1 && nullCompared(recv, args.head).isDefined =>
              val cmp = if op == "==" || op == "eq" then "=" else "<>"
              s"(${emitTerm(nullCompared(recv, args.head).get)} $cmp invalid)"
            case Select(recv, op @ ("==" | "!=")) if args.length == 1 && (isModuleOperand(recv) || isModuleOperand(args.head)) =>
              val e = s"RtIdentity(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              if op == "==" then e else s"(not $e)"
            // Scala equality / identity on non-primitive operands.
            case Select(recv, op @ ("==" | "!=")) if args.length == 1 && !(isPrimLike(recv) && isPrimLike(args.head)) =>
              val e = s"_eq(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              if op == "==" then e else s"(not $e)"
            case Select(recv, op @ ("eq" | "ne")) if args.length == 1 =>
              val e = s"RtIdentity(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              if op == "eq" then e else s"(not $e)"
            case Select(recv, "apply") if args.length == 1 && isCollType(recv) =>
              s"${emitTerm(recv)}[${emitTerm(args.head)}]"
            // Invoking a function VALUE (`f(x)` where f: T => R desugars to
            // f.apply(x)). A function value is a defunctionalised {fn, env}
            // closure, so it must be called via RtInvoke, not `f(x)`.
            case Select(recv, "apply") if isFunctionType(recv.tpe) =>
              val r = emitTerm(recv)
              val a = emitArgs(args, fn.symbol)
              // A closure held in a local or a field chain is called in place — one call fewer than RtInvoke.
              if r.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*") && r != "invalid" then s"$r.fn($r.env, [$a])"
              else s"RtInvoke($r, [$a])"
            // A method DECLARED on a user class dispatches to its own emitted
            // function — it must win over the collection/string/Option builtins
            // below, which match on NAME alone. `Var.map` is not `List.map`,
            // and a UI layer is full of `map`/`filter`/`find`/`contains`.
            case Select(recv, name)
                if fn.symbol.exists && fn.symbol.isDefDef && fn.symbol.flags.is(Flags.Method)
                   && isUserClassSym(fn.symbol.owner) && !isFacade(fn.symbol) =>
              val a = emitArgs(args, fn.symbol)
              val tail = if a.isEmpty then "" else ", " + a
              if isThis(recv) && inCurrentClass(fn.symbol.owner) then
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
            case Select(recv, "$plus$colon") =>  // x +: xs (prepend)
              s"_prepend(${emitTerm(args.head)}, ${emitTerm(recv)})"
            case Select(recv, "::") =>  // x :: xs (cons/prepend); recv is xs
              s"_prepend(${emitTerm(args.head)}, ${emitTerm(recv)})"
            // `xs ::: ys` is RIGHT-associative: it desugars to `ys.:::(xs)`, so
            // the RECEIVER is the suffix. Concatenating receiver-first turned
            // every `list ::: List(x)` append into a prepend.
            case Select(recv, ":::") =>
              s"_concat(${emitTerm(args.head)}, ${emitTerm(recv)})"
            case Select(recv, "++") =>  // xs ++ ys (left-associative)
              s"_concat(${emitTerm(recv)}, ${emitTerm(args.head)})"
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
            case Select(recv, "replace") if args.length == 2 =>
              s"_replace(${emitTerm(recv)}, ${emitTerm(args(0))}, ${emitTerm(args(1))})"
            case Select(recv, op) if binOps.contains(op) && args.length == 1 =>
              s"(${emitTerm(recv)} ${binOps(op)} ${emitTerm(args.head)})"
            // ── Option ops → invalid-checks (Option lowers to a nullable). Must
            // precede the collection ops, since some names (map/filter/…) overlap. ──
            case Select(recv, "map" | "flatMap") if isOptionType(recv) =>
              s"_optMap(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "foreach") if isOptionType(recv) =>
              s"_optForeach(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "getOrElse") if isOptionType(recv) =>
              s"_getOrElse(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "filter") if isOptionType(recv) =>
              s"_optFilter(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "exists") if isOptionType(recv) =>
              s"_optExists(${emitTerm(recv)}, ${emitArgVal(args.head)})"
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
            case Select(recv, "contains") =>
              s"_contains(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "mkString") if args.length == 1 =>
              s"_mkString(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "take") =>
              s"_take(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "drop") =>
              s"_drop(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "sortBy") =>
              s"_sortBy(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "getOrElse") =>
              s"_getOrElse(${emitTerm(recv)}, ${emitArgVal(args.head)})"
            case Select(recv, "until") =>
              s"_until(${emitTerm(recv)}, ${emitTerm(args.head)})"
            case Select(recv, "to") if args.length == 1 =>
              s"_toIncl(${emitTerm(recv)}, ${emitTerm(args.head)})"
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
              s"${typeName(currentClass)}_${memberName(fn.symbol, name)}(self${if a.isEmpty then "" else ", " + a})"
            // A self method referenced as a BARE Ident (`load()` inside another
            // method) — Scala drops the `this.`; emit Class_method(self, …), not
            // `self.load()` (a field-call passes no `self` → "wrong # of params").
            case _ if fn.symbol.exists && fn.symbol.isDefDef && fn.symbol.flags.is(Flags.Method)
                && inCurrentClass(fn.symbol.owner) && !isFacade(fn.symbol) =>
              val a = emitArgs(args, fn.symbol)
              s"${typeName(currentClass)}_${memberName(fn.symbol, fn.symbol.name)}(self${if a.isEmpty then "" else ", " + a})"
            // Cross-object user-method call: `recv.m(args)` where recv is another
            // user object. RokuEmitter methods take `self` explicitly, so emit
            // Owner_method(recv, args), NOT recv.m(args) — a field-call passes no
            // self, yielding "wrong # of params" at runtime.
            case Select(recv, name)
                if fn.symbol.exists && fn.symbol.isDefDef && fn.symbol.flags.is(Flags.Method)
                   && isUserClassSym(fn.symbol.owner) && !isFacade(fn.symbol) =>
              val a = emitArgs(args, fn.symbol)
              s"${typeName(fn.symbol.owner)}_${memberName(fn.symbol, name)}(${emitTerm(recv)}${if a.isEmpty then "" else ", " + a})"
            case _ =>
              val fe = emitTerm(fn)
              // `throw e` is a keyword in BrightScript, not a call → runtime helper.
              if fe == "throw" then s"RtThrow(${emitArgs(args, fn.symbol)})"
              else s"$fe(${emitArgs(args, fn.symbol)})"
        case _ => emitTerm(t)

    def isThis(t: Term): Boolean = t match
      case th: This => thisRef(th.symbol) == "self"
      case _        => false

    def emitTerm(t0: Term): String = t0 match
      // An inlined call's bindings (`assert`'s message proxy …) are real vals.
      case Inlined(_, bs, e) if bs.nonEmpty && pendingStack.nonEmpty =>
        bs.foreach { bd => val st = emitStat(bd); if st.trim.nonEmpty then pendingStack.top += st }
        emitTerm(e)
      case _ => emitTermU(t0)

    def emitTermU(t0: Term): String =
      unwrap(t0) match
        case Apply(Select(New(tpt), _), args) if hasNative(tpt.tpe.typeSymbol) =>
          s"${tpt.tpe.typeSymbol.name}(${emitFacadeCtorArgs(tpt.tpe.typeSymbol.primaryConstructor, args)})"
        case Apply(TypeApply(Select(New(tpt), _), _), args) if hasNative(tpt.tpe.typeSymbol) =>
          s"${tpt.tpe.typeSymbol.name}(${emitFacadeCtorArgs(tpt.tpe.typeSymbol.primaryConstructor, args)})"
        case Apply(ctor @ Select(New(tpt), _), args) if isUserClassSym(tpt.tpe.typeSymbol) =>
          s"${typeName(tpt.tpe.typeSymbol)}_new(${emitArgs(args, ctor.symbol)})"
        case Apply(TypeApply(ctor @ Select(New(tpt), _), _), args) if isUserClassSym(tpt.tpe.typeSymbol) =>
          s"${typeName(tpt.tpe.typeSymbol)}_new(${emitArgs(args, ctor.symbol)})"
        // super.m(...) — a user base resolves statically to the implementation
        // TASTy names; into a @native base (State.initState etc.) it is a no-op.
        case Apply(f0, sargs) if superSel(f0).isDefined =>
          val ss = superSel(f0).get.symbol
          if ss.exists && userTypeDefs.contains(ss.owner) then
            s"${modelImplName(ss)}(self${emitArgList(sargs).map(", " + _).mkString})"
          else "invalid"
        case sel @ Select(spr, _) if isSuper(spr) =>
          val ss = sel.symbol
          if ss.exists && userTypeDefs.contains(ss.owner) && (ss.isDefDef || ss.flags.is(Flags.Lazy)) then
            s"${modelImplName(ss)}(self)"
          else "invalid"
        case Literal(c)  => emitConst(c)
        case th: This    => thisRef(th.symbol)
        case id @ Ident(n) => emitRef(id.symbol, n)
        case Select(q, "toString")             => s"_str(${emitTerm(q)})"
        case Apply(Select(q, "toString"), Nil) => s"_str(${emitTerm(q)})"
        case sel @ Select(_, n @ ("MinValue" | "MaxValue")) if ownerFqn(sel.symbol) == "scala.Double" =>
          if n == "MaxValue" then "RtDoubleMax()" else "RtDoubleMin()"
        case sel @ Select(_, n @ ("MinValue" | "MaxValue")) if ownerFqn(sel.symbol) == "scala.Int" =>
          if n == "MaxValue" then "2147483647" else "(-2147483647 - 1)"
        case sel @ Select(_, n @ ("Pi" | "E")) if ownerFqn(sel.symbol) == "scala.math.package" =>
          if n == "Pi" then "3.141592653589793" else "2.718281828459045"
        case Select(Apply(conv, List(n)), unit) if durationFactor(conv, unit).isDefined =>
          s"RtDuration((${emitTerm(n)}) * ${durationFactor(conv, unit).get})"
        case Select(q, "toMillis") if fqnOf(q.tpe).startsWith("scala.concurrent.duration") =>
          s"RtDurationMs(${emitTerm(q)})"
        case Select(q, "toSeconds") if fqnOf(q.tpe).startsWith("scala.concurrent.duration") =>
          s"Int(RtDurationMs(${emitTerm(q)}) / 1000)"
        case Select(Apply(f, List(str)), m @ ("nonEmpty" | "isEmpty"))
            if stripTypeApply(f).symbol.exists && stripTypeApply(f).symbol.name == "augmentString" =>
          if m == "nonEmpty" then s"(Len(${emitTerm(str)}) > 0)" else s"(Len(${emitTerm(str)}) = 0)"
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
        case Select(q, "toOption") if isTryType(q) || isEitherType(q) => s"_tryToOption(${emitTerm(q)})"
        case Select(q, "toList" | "toSeq" | "toArray" | "toVector" | "toIndexedSeq") if isCollType(q) => emitTerm(q)
        // Paren-less collection getters → runtime helpers.
        case Select(q, "isEmpty") if isCollType(q)   => s"(${emitTerm(q)}.count() = 0)"
        case Select(q, "nonEmpty") if isCollType(q)  => s"(${emitTerm(q)}.count() > 0)"
        case Select(q, "head") if isCollType(q)      => s"${emitTerm(q)}[0]"
        case Select(q, "last") if isCollType(q)      => s"_last(${emitTerm(q)})"
        case Select(q, "headOption") if isCollType(q) => s"_headOption(${emitTerm(q)})"
        case Select(q, "lastOption") if isCollType(q) => s"_lastOption(${emitTerm(q)})"
        case Select(q, "reverse") if isCollType(q)   => s"_reverse(${emitTerm(q)})"
        case Select(q, "distinct") if isCollType(q)  => s"_distinct(${emitTerm(q)})"
        case Select(q, "sum") if isCollType(q)       => s"_sum(${emitTerm(q)})"
        case Select(q, "min") if isCollType(q)       => s"_minList(${emitTerm(q)})"
        case Select(q, "max") if isCollType(q)       => s"_maxList(${emitTerm(q)})"
        case Select(q, "zipWithIndex") if isCollType(q) => s"_zipWithIndex(${emitTerm(q)})"
        case Select(q, "toDouble" | "toInt" | "toLong" | "toFloat") => emitTerm(q)
        case Select(q, "tail") if isCollType(q) => s"_drop(${emitTerm(q)}, 1)"
        case TypeApply(Select(q, "asInstanceOf"), _) => emitTerm(q)
        case sel @ Select(_, _) if modelModuleOf(sel.symbol).isDefined => moduleAccessor(modelModuleOf(sel.symbol).get)
        case sel @ Select(q, n) if inParentArgs && isThis(q) && sel.symbol.flags.is(Flags.ParamAccessor) && sel.symbol.owner == currentClass =>
          if isByNameParam(sel.symbol) then s"RtForce(${brSafe(n)})" else brSafe(n)
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
          if qs.exists && qs.flags.is(Flags.Module)
             && (hasNative(qs) || hasNative(qs.companionClass)) then
            s"ga.${qs.name.stripSuffix("$")}.${brSafe(name)}"
          else
            // paren-less USER getter → Class_getter(self); a member of a @native
            // base class is a plain field → self.field.
            val parenlessSelf =
              s.exists && s.isDefDef && s.flags.is(Flags.Method) && !s.flags.is(Flags.FieldAccessor)
                && s.paramSymss.forall(c => c.nonEmpty && c.forall(_.isType)) && isUserClassSym(s.owner)
            // `inCurrentClass` is ownership-only, so it is ALSO true for another
            // instance of the same class (`child.node` inside a Component
            // method). Only a real `this.` receiver resolves against `self`.
            if inCurrentClass(s.owner) && isThis(q) then
              if parenlessSelf then s"${typeName(currentClass)}_${memberName(s, name)}(self)" else s"self.${brSafe(name)}"
            else moduleSyms.get(s.owner) match
              case Some(prefix) => s"${prefix}_${memberName(s, name)}()"
              // A paren-less getter on ANOTHER user instance is a method, not a
              // field — `self.getter` only holds the function ref, so call it.
              case None if parenlessSelf => s"${typeName(s.owner)}_${memberName(s, name)}(${emitTerm(q)})"
              case None                  => s"${emitTerm(q)}.${brSafe(name)}"
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
        case Block(stats, e) if allValDefs(stats) && dropsDefaults(e) =>
          emitTempValBlock(stats.collect { case v: ValDef => v }, e)
        case bl: Block => hoistBlock(bl)
        case asg: Assign =>
          if pendingStack.nonEmpty then
            val st = emitStat(asg)
            if st.trim.nonEmpty then pendingStack.top += st
            "invalid"
          else todo(asg)
        case other => todo(other)

    // ── statements / bodies ─────────────────────────────────────────────────
    // A statement-position term whose value is a no-op (a `super.x()` call, an
    // elided Unit) emits the bare literal `invalid`, which isn't a valid
    // BrightScript statement — drop those.
    def stmtTerm(e: String): String =
      val t = e.trim
      // A bare literal / no-op isn't a valid BrightScript statement — only
      // calls and assignments are. Drop `invalid`, "", string/number literals.
      if t.isEmpty || t == "invalid" then ""
      else if t.matches("\"(?:[^\"]|\"\")*\"") then ""
      else if t.matches("-?[0-9][0-9.]*") then ""
      // a discarded VALUE (`(a - b)`, `x.field`) is not a BrightScript statement; keep its evaluation
      else if t.startsWith("(") || !t.endsWith(")") then "  __discard = " + e
      else "  " + e

    // ── pattern matching ─────────────────────────────────────────────────────
    // BrightScript has no expression-level match/IIFE, so `x match { … }` is
    // emitted as an if/elseif STATEMENT chain, with each branch's value routed
    // through a `sink` (return / assignment). Case classes carry a `_cls` tag.
    def patternClass(p: Tree): Symbol = p match
      case Unapply(fun, _, _) =>
        val s = fun.symbol
        if s.exists && s.owner.exists && s.owner.companionClass.exists then s.owner.companionClass
        else p match { case tp: Term => tp.tpe.typeSymbol; case _ => Symbol.noSymbol }
      case TypedOrTest(_, tpt) => tpt.tpe.typeSymbol
      case Typed(_, tpt)       => tpt.tpe.typeSymbol
      case _                   => Symbol.noSymbol

    // (testExpr, bindingStatements) for matching `subj` against pattern `p`.
    def patternTest(p: Tree, subj: String): (String, List[String]) = p match
      case Wildcard()        => ("true", Nil)
      case Ident("_")        => ("true", Nil)
      case Bind(name, inner) =>
        val (t, b) = patternTest(inner, subj)
        (t, s"${brSafe(name)} = $subj" :: b)
      case Literal(c)        => (s"($subj = ${emitConst(c)})", Nil)
      case Typed(inner, _)   => patternTest(inner, subj)
      case TypedOrTest(inner, tpt) =>
        val ts = tpt.tpe.typeSymbol
        val innerTests = inner match
          case u: Unapply => { val c = patternClass(u); c.exists && c == ts }
          case _          => false
        val tag =
          if !ts.exists || innerTests then "true"
          else if ts.fullName == "scala.Some" || ts.fullName == "scala.Option" then s"($subj <> invalid)"
          else if ts.flags.is(Flags.Case) && !ts.fullName.matches("scala\\.Tuple\\d+") then
            s"($subj <> invalid and $subj._cls = ${brStr(ts.name)})"
          else "true"
        val (it, ib) = patternTest(inner, subj)
        val test = if it == "true" then tag else if tag == "true" then it else s"$tag and $it"
        (test, ib)
      // Option lowers to a nullable: `Some(x)` is any non-invalid value, bound as-is.
      case u @ Unapply(fun, _, subs)
          if subs.size == 1 && { val c = patternClass(u); c.exists && c.fullName == "scala.Some" } =>
        val (it, ib) = patternTest(subs.head, subj)
        (if it == "true" then s"($subj <> invalid)" else s"($subj <> invalid) and $it", ib)
      case u @ Unapply(_, _, subs) =>
        val cls = patternClass(u)
        val fields = if cls.exists then cls.caseFields.map(_.name) else Nil
        val tag =
          if cls.exists && !cls.fullName.matches("scala\\.Tuple\\d+") then s"($subj <> invalid and $subj._cls = ${brStr(cls.name)})"
          else if cls.exists then s"($subj <> invalid)"
          else "true"
        val subT = subs.zipWithIndex.map { case (sp, i) =>
          patternTest(sp, s"$subj.${fields.lift(i).map(brSafe).getOrElse("_" + (i + 1))}") }
        val test = (tag :: subT.map(_._1)).filter(_ != "true").mkString(" and ")
        ((if test.isEmpty then "true" else test), subT.flatMap(_._2))
      // A stable identifier (a case object, `Nil`, `None`, a val) matches by
      // equality. Without this it fell through to `true` and ALWAYS matched.
      case t: Term if (t match { case Ident(_) | Select(_, _) => true; case _ => false })
                      && t.symbol.exists && (t.symbol.flags.is(Flags.Module) || t.symbol.isValDef) =>
        val v = emitTerm(t)
        // An `object` is a single instance: matching `case Conclusion.Continue =>` is an identity test.
        if v == "invalid" then (s"($subj = invalid)", Nil)
        else if t.symbol.exists && t.symbol.flags.is(Flags.Module) then (s"RtIdentity($subj, $v)", Nil)
        else (s"_eq($subj, $v)", Nil)
      case _ => ("true", Nil)

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
      if sk == "" then stmtTerm(v)
      else if sk == skReturn then s"  return $v"
      else s"  $sk = $v"
    def sinkUnit(sk: String): String =
      if sk == "" then "" else if sk == skReturn then "  return invalid" else s"  $sk = invalid"

    /** Emit ONE statement: whatever its expressions hoist is written first. */
    def withStatement(f: => String): String =
      pendingStack.push(mutable.ListBuffer[String]())
      val line = f
      val pre = pendingStack.pop().toList
      (pre :+ line).filter(_.trim.nonEmpty).mkString("\n")

    def hoist(t: Term): String =
      if pendingStack.isEmpty then todo(t)
      else
        anfCounter += 1
        val tmp = s"_v$anfCounter"
        pendingStack.top += emitInto(t, tmp)
        tmp

    def hoistBlock(bl: Block): String =
      if pendingStack.isEmpty then todo(bl)
      else
        bl.statements.foreach { st =>
          val e = emitStat(st)
          if e.trim.nonEmpty then pendingStack.top += e
        }
        emitTerm(bl.expr)

    def emitInto(t: Term, sk: String): String = t match
      case Inlined(_, bindings, expansion) if bindings.nonEmpty =>
        (bindings.map(emitStat) :+ emitInto(expansion, sk)).filter(_.trim.nonEmpty).mkString("\n")
      case _ => emitIntoU(unwrap(t), sk)

    def emitIntoU(t: Term, sk: String): String = t match
      case Block(List(cd: ClassDef), e) if isLocalClassInstantiation(cd, e) =>
        withStatement(sinkLine(sk, emitAnonInstance(cd, e)))
      case Block(List(dd: DefDef), _: Closure) => withStatement(sinkLine(sk, emitClosure(dd)))
      case Block(Nil, e) => emitInto(e, sk)
      // Single-threaded target: `synchronized { body }` in statement or return
      // position IS the body — no thunk to allocate and force. (In expression
      // position it stays `RtSynchronized(thunk)`, preserving evaluation order.)
      case Apply(fn, List(body)) if fn.symbol.exists && fn.symbol.name == "synchronized" =>
        emitInto(body, sk)
      case Block(stats, e) if allValDefs(stats) && dropsDefaults(e) =>
        withStatement(sinkLine(sk, emitTempValBlock(stats.collect { case v: ValDef => v }, e)))
      case Block(stats, e) =>
        (stats.map(emitStat) :+ emitInto(e, sk)).filter(_.trim.nonEmpty).mkString("\n")
      case If(c, a, b) =>
        val head = withStatement(s"  if ${emitTerm(c)} then")
        val thenPart = emitInto(a, sk)
        if sk == "" && isUnit(b) then s"$head\n$thenPart\n  end if"
        else s"$head\n$thenPart\n  else\n${emitInto(b, sk)}\n  end if"
      case Match(scrut, cases) => emitMatchStat(scrut, cases, sk)
      case Try(body, cases, fin) => emitTryStat(body, cases, fin, sk)
      case While(cond, body) =>
        val head = withStatement(s"  while ${emitTerm(cond)}")
        List(head, emitInto(body, ""), "  end while", sinkUnit(sk)).filter(_.trim.nonEmpty).mkString("\n")
      case asg: Assign =>
        List(emitStat(asg), sinkUnit(sk)).filter(_.trim.nonEmpty).mkString("\n")
      case e if isUnit(e) => sinkUnit(sk)
      case e => withStatement(sinkLine(sk, emitTerm(e)))

    def isModuleVar(s: Symbol): Boolean =
      s.exists && s.isValDef && s.flags.is(Flags.Mutable) && s.owner.exists && s.owner.flags.is(Flags.Module)

    /** The non-null operand of `x == null` (either side) when a direct
      * `= invalid` test is safe: an object of a concrete class. Never a string,
      * number, boolean or function (BrightScript may refuse the comparison),
      * nor a type that could hold one (Any, AnyRef, a type parameter, java.*). */
    /** A reference to an `object` (a case object such as
      * `ReactionStatus.Continue`, `None`, `Nil`): one instance, so `==` against
      * it is identity — `_eq`'s structural walk reaches the same answer slowly. */
    def isModuleOperand(t: Term): Boolean =
      val u = unwrap(t)
      (u.symbol.exists && u.symbol.flags.is(Flags.Module))
        || { val ts = u.tpe.widen.typeSymbol; ts.exists && ts.flags.is(Flags.Module) }

    def nullCompared(a: Term, b: Term): Option[Term] =
      def isNull(t: Term): Boolean = unwrap(t) match
        case Literal(NullConstant()) => true
        case _                       => false
      val other = if isNull(b) then Some(a) else if isNull(a) then Some(b) else None
      other.filter { o =>
        val tp = o.tpe.widen
        val ts = tp.typeSymbol
        ts.exists && ts.isClassDef && !isPrimLike(o) && !isFunctionType(tp)
          && !ts.fullName.startsWith("java.")
          && !(tp =:= TypeRepr.of[Any]) && !(tp =:= TypeRepr.of[AnyRef]) && !(tp =:= TypeRepr.of[Matchable])
          && !(tp <:< TypeRepr.of[String]) && !(tp <:< TypeRepr.of[AnyVal])
      }

    def emitStat(st: Statement): String = st match
      case vd @ ValDef(name, _, Some(rhs)) =>
        val lhs = brSafe(name)
        if boxedVars.contains(vd.symbol) then
          List(emitInto(rhs, lhs), s"  $lhs = { v: $lhs }").filter(_.trim.nonEmpty).mkString("\n")
        else emitInto(rhs, lhs)
      case ValDef(_, _, None) => ""
      case Assign(lhs, rhs) if isModuleVar(lhs.symbol) =>
        val k = s"${modulePrefix(lhs.symbol.owner)}_${brSafe(lhs.symbol.name)}"
        val tmp = s"__mvset_$k"
        List(emitInto(rhs, tmp), s"  ${k}__set($tmp)").filter(_.trim.nonEmpty).mkString("\n")
      case Assign(lhs, rhs) => emitInto(rhs, emitTerm(lhs))
      case _: DefDef | _: ClassDef | _: TypeDef => ""
      case term: Term => emitInto(term, "")
      case _ => ""

    def emitBody(t: Term, wantRet: Boolean): String = emitInto(t, if wantRet then skReturn else "")
    def emitTail(expr: Term, wantRet: Boolean): String = emitBody(expr, wantRet)

    def iifSafe(a: Term, b: Term): Boolean = pureValue(a) && pureValue(b)

    def allValDefs(stats: List[Statement]): Boolean =
      stats.nonEmpty && stats.forall { case _: ValDef => true; case _ => false }

    def isStmtExpr(t: Term): Boolean = unwrap(t) match
      case _: Assign => true
      case _: If     => true
      case _: Match  => true
      case _: Try    => true
      case _         => false

    def substToken(in: String, name: String, value: String): String =
      val re = "(?<![A-Za-z0-9_$.])" + java.util.regex.Pattern.quote(name) + "(?![A-Za-z0-9_$])"
      in.replaceAll(re, java.util.regex.Matcher.quoteReplacement(value))

    // The if/elseif chain matching `subj` against `cases`. `noMatch` is the
    // statement for when nothing matches: a MatchError for `match`, a rethrow
    // for `catch`.
    def emitCasesOn(subj: String, cases: List[CaseDef], sk: String, noMatch: String): List[String] =
      val sb = mutable.ListBuffer[String]()
      var exhaustive = false
      var first = true
      cases.foreach { cd =>
        if !exhaustive then
          val (test, binds) = patternTest(cd.pattern, subj)
          val guard = cd.guard.map { g =>
            pendingStack.push(mutable.ListBuffer[String]())
            var ge = emitTerm(g)
            if pendingStack.pop().nonEmpty then todos += "unsupported: match guard needing statements"
            // A guard reads the pattern's bindings, which are only assigned
            // inside the branch: substitute each bound name by its path.
            binds.foreach { bd =>
              val eq = bd.indexOf(" = ")
              if eq > 0 then ge = substToken(ge, bd.substring(0, eq).trim, bd.substring(eq + 3).trim)
            }
            s" and ($ge)"
          }.getOrElse("")
          sb += s"  ${if first then "if" else "else if"} $test$guard"
          first = false
          binds.foreach(bd => sb += s"    $bd")
          val body = emitInto(cd.rhs, sk)
          if body.trim.nonEmpty then sb += body
          if test == "true" && guard.isEmpty then exhaustive = true
      }
      if !exhaustive && noMatch.nonEmpty then
        sb += "  else"
        sb += noMatch
      sb += "  end if"
      sb.toList

    def emitMatchStat(scrut: Term, cases: List[CaseDef], sk: String): String =
      val mv = s"_mt${matchCounter}"; matchCounter += 1
      val head = withStatement(s"  $mv = ${emitTerm(scrut)}")
      (head :: emitCasesOn(mv, cases, sk, s"    RtThrow(MatchError_new($mv))")).mkString("\n")

    // `try body catch { cases } finally fin`. The body and handlers assign a
    // result temp rather than returning from inside `try` (which would skip
    // `finally`); an unmatched exception is rethrown; with a finally block, an
    // exception from the body OR a handler is held, `finally` runs, then it is
    // rethrown.
    def emitTryStat(body: Term, cases: List[CaseDef], fin: Option[Term], sk: String): String =
      val n = matchCounter; matchCounter += 1
      val ex = s"_ex$n"
      val wantsValue = sk != "" && !(body.tpe.widen =:= TypeRepr.of[Unit])
      val res = s"_tr$n"
      val inner = if wantsValue then res else ""
      val sb = mutable.ListBuffer[String]()
      fin match
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
          if cases.isEmpty then sb += s"    $err = $ex"
          else
            sb += "    try"
            sb ++= emitCasesOn(ex, cases, inner, s"    throw $ex")
            sb += s"    catch ${ex}h"
            sb += s"      $err = ${ex}h"
            sb += "    end try"
          sb += "  end try"
          sb += emitInto(f, "")
          sb += s"  if $err <> invalid then throw $err"
      if wantsValue then sb += sinkLine(sk, res)
      else if sk == skReturn then sb += "  return invalid"
      sb.filter(_.trim.nonEmpty).mkString("\n")

    // Scala hoists a facade call's named/default args into temp vals:
    //   { val title$1 = …; val key$2 = Facade.$default$1; …; Facade(title$1, key$2, …) }
    // Inline the REAL ones back into the call by token substitution and STRIP
    // the omitted-default ones (the platform runtime supplies defaults).
    def emitTempValBlock(vals: List[ValDef], expr: Term): String =
      def sub(in: String, name: String, value: String): String =
        val re = "(?<![A-Za-z0-9_$])" + java.util.regex.Pattern.quote(name) + "(?![A-Za-z0-9_$])"
        in.replaceAll(re, java.util.regex.Matcher.quoteReplacement(value))
      val marker = "__RT_DEF__"
      def innermost(t: Term): Term = unwrap(t) match
        case Apply(f, _)     => innermost(f)
        case TypeApply(f, _) => innermost(f)
        case other           => other
      // Only a call into something the emitter does NOT write (a facade,
      // a runtime stub) has defaults to strip; a user function's defaults are
      // real arguments (see argParts).
      val (defaultVals, realVals) =
        if dropsDefaults(expr) then vals.partition(_.rhs.exists(isDefaultArg)) else (Nil, vals)
      var res = emitTerm(expr)
      // Refs to these temp vals were emitted through brSafe (`title$1` →
      // `title_1`), so substitute the brSafe'd name, not the raw Scala one.
      for vd <- defaultVals do res = sub(res, brSafe(vd.name), marker)
      // No extra parens: emitTerm already wraps binary ops, and an inlined temp
      // sits in a delimited slot (assoc `key: value` / arg). Wrapping a receiver
      // as `(self.player).setSource(…)` is a BrightScript syntax error (a
      // statement can't start with `(`, and `(expr).method()` isn't allowed).
      for vd <- realVals.reverse do
        val rhs = vd.rhs.get
        res = sub(res, brSafe(vd.name), if isDefaultArg(rhs) then emitDefaultArg(rhs) else emitTerm(rhs))
      val id = "[A-Za-z_$][A-Za-z0-9_$]*"
      res
        .replaceAll(id + ": " + marker + ", ", "")
        .replaceAll(", " + id + ": " + marker, "")
        .replaceAll(marker + ", ", "")
        .replaceAll(", " + marker, "")
        .replaceAll("\\(" + marker + "\\)", "()")
        .replaceAll(id + ": " + marker, "")
        .replaceAll(marker, "invalid")

    // ── object → top-level Obj_member functions ──────────────────────────────
    def emitModule(cd: ClassDef): Unit =
      val base = modulePrefix(cd.symbol)
      cd.body.foreach {
        case dd @ DefDef(name, pcs, _, Some(rhs))
            if userMember(dd.symbol) && name != "<init>" && !name.endsWith("_=") =>
          val ps = termParams(pcs)
          out.append(s"function ${base}_${memberName(dd.symbol, name)}(${ps.map(p => s"$p as dynamic").mkString(", ")}) as dynamic\n")
          out.append(emitBody(rhs, true)).append("\n")
          out.append("end function\n\n")
        case vd @ ValDef(name, _, Some(rhs)) if userMember(vd.symbol) && modelModuleOf(vd.symbol).isEmpty =>
          val mutable = vd.symbol.flags.is(Flags.Mutable)
          out.append(s"function ${base}_${brSafe(name)}() as dynamic\n")
          unwrap(rhs) match
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
          out.append("end function\n\n")
          // An object `var` also gets a setter. It marks the var initialised so
          // a first READ after a write doesn't re-run the initialiser over it.
          if mutable then
            val k = s"${base}_${brSafe(name)}"
            out.append(s"function ${k}__set(v as dynamic) as dynamic\n")
            out.append("  ga = GetGlobalAA()\n")
            out.append(s"  ga[${brStr("__mvs_" + k)}] = true\n")
            out.append(s"  ga[${brStr("__mv_" + k)}] = v\n")
            out.append("  return invalid\nend function\n\n")
        case _ => ()
      }

    // ── class → constructor fn (assoc + method refs) + method fns ────────────
    def emitClass(cd: ClassDef): Unit =
      val sym = cd.symbol
      val cn  = sym.name
      currentClass = sym
      val ctorParams = termParams(cd.constructor.paramss)

      // constructor
      out.append(s"function ${cn}_new(${ctorParams.map(p => s"$p as dynamic").mkString(", ")}) as object\n")
      out.append("  self = {}\n")
      out.append(s"  self._cls = ${brStr(cn)}\n")
      cd.body.foreach {
        case vd: ValDef if userMember(vd.symbol) && vd.symbol.flags.is(Flags.ParamAccessor) =>
          out.append(s"  self.${brSafe(vd.name)} = ${brSafe(vd.name)}\n")
        case vd @ ValDef(name, _, Some(rhs)) if userMember(vd.symbol) =>
          out.append(emitInto(rhs, s"self.${brSafe(name)}")).append("\n")
        // A constructor STATEMENT (`register(this)` in a class body) runs at
        // construction; it used to be silently dropped.
        case st: Term =>
          val e = emitStat(st)
          if e.nonEmpty then out.append(e).append("\n")
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
          val ps = "self as object" :: termParams(pcs).map(p => s"$p as dynamic")
          out.append(s"function ${cn}_${memberName(dd.symbol, name)}(${ps.mkString(", ")}) as dynamic\n")
          val wantRet = !(ret.tpe =:= TypeRepr.of[Unit])
          out.append(emitBody(rhs, wantRet)).append("\n")
          out.append("  return invalid\n")
          out.append("end function\n\n")
        case _ => ()
      }
      currentClass = Symbol.noSymbol

    // ── object-model type → methods + init chain + (concrete) constructor ──
    def parentInfo(p: Tree): (Symbol, List[Term]) =
      def peel(t: Term, acc: List[Term]): (Symbol, List[Term]) = t match
        case Apply(f, as)        => peel(f, as ++ acc)
        case TypeApply(f, _)     => peel(f, acc)
        case Select(New(tpt), _) => (tpt.tpe.typeSymbol, acc)
        case New(tpt)            => (tpt.tpe.typeSymbol, acc)
        case Block(_, e)         => peel(e, acc)
        case other               => (other.tpe.typeSymbol, acc)
      p match
        case t: Term      => peel(t, Nil)
        case tt: TypeTree => (tt.tpe.typeSymbol, Nil)
        case _            => (Symbol.noSymbol, Nil)

    def isWildcardInit(t: Term): Boolean = unwrap(t) match
      case Ident("_") => true
      case other      => other.symbol.exists && other.symbol.fullName.endsWith("uninitialized")

    def vtableFor(sym: Symbol): List[(String, String)] =
      val slots = mutable.LinkedHashMap[String, String]()
      sym.typeRef.baseClasses.filter(userTypeDefs.contains).foreach { b =>
        userTypeDefs(b).body.foreach {
          case dd: DefDef if emittableMethod(dd) && !dd.symbol.flags.is(Flags.Private) =>
            val sl = slotName(dd.symbol)
            if !slots.contains(sl) then slots(sl) = modelImplName(dd.symbol)
          case vd: ValDef if vd.symbol.flags.is(Flags.Lazy) && vd.rhs.isDefined && !vd.symbol.flags.is(Flags.Private) =>
            val sl = slotName(vd.symbol)
            if !slots.contains(sl) then slots(sl) = modelImplName(vd.symbol)
          case vd: ValDef if isVirtualField(vd.symbol) =>
            val sl = slotName(vd.symbol)
            if !slots.contains(sl) then slots(sl) = modelImplName(vd.symbol)
          case _ => ()
        }
      }
      slots.toList.reverse

    def emitModelType(cd: ClassDef, sb: StringBuilder): Unit =
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
          val ps = "self as object" :: termParams(dd.paramss).map(p => s"$p as dynamic")
          sb.append(s"function ${modelImplName(dd.symbol)}(${ps.mkString(", ")}) as dynamic\n")
          val wantRet = !(dd.returnTpt.tpe =:= TypeRepr.of[Unit])
          sb.append(emitBody(dd.rhs.get, wantRet)).append("\n")
          sb.append("  return invalid\nend function\n\n")
        case vd: ValDef if vd.symbol.flags.is(Flags.Lazy) && vd.rhs.isDefined =>
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
      sb.append(s"function ${cn}__init(self as object${ctorParams.map(p => s", $p as dynamic").mkString}) as dynamic\n")
      sb.append(s"  if self.DoesExist($guard) then return invalid\n")
      sb.append(s"  self[$guard] = true\n")
      cd.parents.foreach { p =>
        val (ps, pargs) = parentInfo(p)
        if ps.exists && ps != sym && userTypeDefs.contains(ps) then
          val ctor = parentCtor(p)
          inParentArgs = true
          val call = withStatement(s"  ${initName(ps, ctor)}(self${emitArgList(pargs, ctor).map(", " + _).mkString})")
          inParentArgs = false
          sb.append(call).append("\n")
        else if ps.exists && shimParents.contains(ps.fullName) then
          sb.append(s"  ${shimParents(ps.fullName)}_init(self)\n")
      }
      cd.body.foreach {
        case vd: ValDef if vd.symbol.flags.is(Flags.ParamAccessor) =>
          sb.append(s"  self.${fieldKey(vd.symbol)} = ${brSafe(vd.name)}\n")
        case vd @ ValDef(_, _, rhsOpt) if !vd.symbol.flags.is(Flags.Lazy) && userMember(vd.symbol) =>
          rhsOpt match
            case Some(r) if !isWildcardInit(r) => sb.append(emitInto(r, s"self.${fieldKey(vd.symbol)}")).append("\n")
            case _                             => sb.append(s"  self.${fieldKey(vd.symbol)} = invalid\n")
        case _: DefDef | _: ValDef | _: TypeDef | _: ClassDef => ()
        case st: Term =>
          val e = emitStat(st)
          if e.trim.nonEmpty then sb.append(e).append("\n")
        case _ => ()
      }
      sb.append("  return invalid\nend function\n\n")

      // secondary constructors: `def this(...) = { this(...); stats }`
      secondaryCtors(sym).zipWithIndex.foreach { case (dd, k) =>
        val ps = termParams(dd.paramss)
        sb.append(s"function ${cn}__init__${k + 1}(self as object${ps.map(p => s", $p as dynamic").mkString}) as dynamic\n")
        val stats = unwrap(dd.rhs.get) match
          case Block(ss, e) => ss :+ e
          case other        => List(other)
        stats.foreach {
          case Apply(f, as) if f.symbol.exists && f.symbol.isClassConstructor =>
            sb.append(withStatement(s"  ${initName(sym, f.symbol)}(self${emitArgList(as, f.symbol).map(", " + _).mkString})")).append("\n")
          case st =>
            val e = emitStat(st)
            if e.trim.nonEmpty then sb.append(e).append("\n")
        }
        sb.append("  return invalid\nend function\n\n")
      }

      // a module: one instance, stored BEFORE its init runs (as the JVM sets MODULE$), so init can refer to it
      if sym.flags.is(Flags.Module) then
        val key = brStr("__mod_" + cn)
        sb.append(s"function ${cn}__module() as object\n")
        sb.append("  ga = GetGlobalAA()\n")
        sb.append(s"  self = ga[$key]\n")
        sb.append("  if self <> invalid then return self\n")
        sb.append("  self = {}\n")
        sb.append(s"  self._cls = ${brStr(cn)}\n")
        sym.typeRef.baseClasses.reverse.foreach { bc =>
          shimParents.get(bc.fullName).foreach(shim => sb.append(s"  ${shim}__install(self)\n"))
        }
        vtableFor(sym).foreach { case (slot, impl) => sb.append(s"  self.$slot = $impl\n") }
        sb.append(s"  ga[$key] = self\n")
        sb.append(s"  ${cn}__init(self)\n")
        sb.append("  return self\nend function\n\n")
      // constructor: allocate, captures, runtime-parent slots, vtable, init
      else if !isTrait && !sym.flags.is(Flags.Abstract) then
        sb.append(s"function ${cn}_new(${(extraParams ++ ctorParams).map(p => s"$p as dynamic").mkString(", ")}) as object\n")
        sb.append("  self = {}\n")
        sb.append(s"  self._cls = ${brStr(cn)}\n")
        extraParams.foreach(ep => sb.append(s"  self.$ep = $ep\n"))
        if sym.flags.is(Flags.Case) then
          sb.append(s"  self._fields = [${sym.caseFields.map(f => brStr(fieldKey(f))).mkString(", ")}]\n")
        sym.typeRef.baseClasses.reverse.foreach { bc =>
          shimParents.get(bc.fullName).foreach(shim => sb.append(s"  ${shim}__install(self)\n"))
        }
        vtableFor(sym).foreach { case (slot, impl) => sb.append(s"  self.$slot = $impl\n") }
        sb.append(s"  ${cn}__init(self${ctorParams.map(p => ", " + p).mkString})\n")
        sb.append("  return self\nend function\n\n")
      currentClass = savedClass

    /** `new T { … }` — an anonymous (or other local) class instance. The class
     *  is emitted out of line under a generated name, capturing the enclosing
     *  instance as `__outer` and each enclosing local it reads as `__fv_<x>`. */
    def emitAnonInstance(cd: ClassDef, e: Term): String =
      val sym = cd.symbol
      if !typeNameOverride.contains(sym) then
        anonCounter += 1
        val encl = if currentClass.exists then typeName(currentClass) else "Anon"
        typeNameOverride(sym) = s"${encl}_anon$anonCounter"
        if currentClass.exists then anonOuter(sym) = currentClass
        val caps = mutable.LinkedHashSet[Symbol]()
        val trav = new TreeTraverser:
          override def traverseTree(t: Tree)(owner: Symbol): Unit =
            t match
              case id: Ident =>
                val sy = id.symbol
                if sy.exists && sy.isTerm && !sy.isDefDef && !isFacade(sy) && !moduleSyms.contains(sy.owner)
                   && sy.owner.exists && !sy.owner.isClassDef && (sy.owner.flags.is(Flags.Method) || sy.owner.isValDef)
                   && !isOwnedWithin(sy, sym)
                then caps += sy
              case _ =>
            super.traverseTree(t)(owner)
        cd.body.foreach(bd => trav.traverseTree(bd)(sym))
        anonCaptures(sym) = caps.toList
        userTypeDefs(sym) = cd
        objectModelTypes += sym
        cd.body.foreach { case dd: DefDef => userMethodSyms += dd.symbol; case _ => () }
        val sb = new StringBuilder
        emitModelType(cd, sb)
        closures.append(sb.toString)
      val outerArg = if currentClass.exists then thisRef(currentClass) else "invalid"
      val capArgs = anonCaptures(sym).map(captureValue)
      s"${typeNameOverride(sym)}_new(${(outerArg :: capArgs).mkString(", ")})"

    /** Local `var`s read or written inside a closure, a by-name argument or a
     *  local class are boxed, so both scopes share one cell. */
    def scanBoxedVars(root: Tree): Unit =
      val candidates = mutable.Set[Symbol]()
      var nested = 0
      val trav = new TreeTraverser:
        override def traverseTree(t: Tree)(owner: Symbol): Unit = t match
          case vd: ValDef =>
            val sy = vd.symbol
            if sy.flags.is(Flags.Mutable) && sy.owner.exists && sy.owner.flags.is(Flags.Method) then candidates += sy
            super.traverseTree(t)(owner)
          case id: Ident =>
            if nested > 0 && candidates.contains(id.symbol) then boxedVars += id.symbol
          case Block(List(_: DefDef), _: Closure) =>
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
              if bn then nested += 1
              traverseTree(arg)(owner)
              if bn then nested -= 1
            }
          case _ => super.traverseTree(t)(owner)
      trav.traverseTree(root)(Symbol.noSymbol)

    // ── walk ─────────────────────────────────────────────────────────────
    def eachClassDef(tree: Tree)(f: ClassDef => Unit): Unit = tree match
      case pc: PackageClause => pc.stats.foreach(s => eachClassDef(s)(f))
      case cd: ClassDef      => f(cd); cd.body.foreach(s => eachClassDef(s)(f))
      case _                 => ()

    val mains = mutable.ListBuffer[DefDef]()
    def collectMains(tree: Tree): Unit = tree match
      case pc: PackageClause => pc.stats.foreach(collectMains)
      case cd: ClassDef      => cd.body.foreach(collectMains)
      case dd: DefDef if isMain(dd.symbol) => mains += dd
      case _                 => ()

    for tasty <- tastys do collectMains(tasty.ast)
    val mainNames = mains.map(_.name).toSet
    for tasty <- tastys do
      eachClassDef(tasty.ast) { cd =>
        if isUserTypeSym(cd.symbol) && !mainNames.contains(cd.symbol.name) then userTypeDefs(cd.symbol) = cd
      }
    // Every user TYPE name (traits and case classes too) claims its namespace,
    // so a companion object's members take the `Obj` infix wherever they would
    // collide — `Reaction.apply` (trait) vs `Reaction.apply` (object).
    for tasty <- tastys do
      eachClassDef(tasty.ast) { cd =>
        val sy = cd.symbol
        if sy.flags.is(Flags.Module) && !hasNative(sy) && !isBuiltin(sy) && !isEnumLike(sy)
           && !sy.name.stripSuffix("$").endsWith("$package") && !mainNames.contains(sy.name)
           && sy.typeRef.baseClasses.exists(b => b != sy && userTypeDefs.contains(b)) then
          modelModules += sy
          userTypeDefs(sy) = cd
      }
    for mc <- modelModules do
      val base = brIdent(mc.name.stripSuffix("$"))
      val owner = mc.owner
      val qualified =
        if owner.exists && owner.flags.is(Flags.Module) then brIdent(owner.name.stripSuffix("$")) + "_" + base else base
      typeNameOverride(mc) =
        if mc.companionClass.exists && userTypeDefs.contains(mc.companionClass) then qualified + "Obj" else qualified
    userClassNames ++= userTypeDefs.keys.filterNot(modelModules.contains).map(_.name)
    for (_, cd) <- userTypeDefs do
      cd.body.foreach { case dd: DefDef => userMethodSyms += dd.symbol; case _ => () }
    locally {
      val all = userTypeDefs.keys.toList
      for sy <- all do
        val bases    = sy.typeRef.baseClasses.filter(b => b != sy && userTypeDefs.contains(b))
        val extended = all.exists(o => o != sy && o.typeRef.baseClasses.contains(sy))
        val caseWithMethods = sy.flags.is(Flags.Case) &&
          userTypeDefs(sy).body.exists { case dd: DefDef => emittableMethod(dd) && !dd.name.contains("$default$"); case _ => false }
        // A `lazy val` needs the object model's lazy getter; the flat class path would evaluate it eagerly.
        val hasLazy = userTypeDefs(sy).body.exists { case vd: ValDef => vd.symbol.flags.is(Flags.Lazy); case _ => false }
        if sy.flags.is(Flags.Trait) || bases.nonEmpty || extended || caseWithMethods || hasLazy then objectModelTypes += sy
    }
    for tasty <- tastys do
      eachClassDef(tasty.ast) { cd => if isUserModule(cd) && !modelModules.contains(cd.symbol) then moduleSyms.getOrElseUpdate(cd.symbol, modulePrefix(cd.symbol)) }
    for tasty <- tastys do scanBoxedVars(tasty.ast)
    for tasty <- tastys do
      eachClassDef(tasty.ast) { cd =>
        if isModel(cd.symbol) then emitModelType(cd, out)
        else if isUserClass(cd) && !mainNames.contains(cd.symbol.name) then emitClass(cd)
        else if isUserModule(cd) then emitModule(cd)
      }
    // @main → rtMain(): the entry the scene calls.
    out.append("sub rtMain()\n")
    for mc <- modelModules.toList.filter(_.flags.is(Flags.Case)).sortBy(typeName) do
      out.append(s"  ${typeName(mc)}__module()\n")
    for dd <- mains; body <- dd.rhs do
      out.append(emitBody(body, false)).append("\n")
    out.append("end sub\n")

  // ── output ─────────────────────────────────────────────────────────────
  def writeOutput(): Unit =
    val title = if projectName.trim.nonEmpty then projectName else "App"
    write("manifest", manifest(title))
    write("source/Main.brs", mainBrs)
    write("components/AppScene.xml", sceneXml)
    write("components/AppScene.brs", sceneBrs)
    val appCode = out.toString + "\n' ── defunctionalised closures ──\n" + closures.toString
    write("components/App.brs", appBrsHeader + injectGa(appCode))
    write("components/ScalaRuntime.brs", runtimeBrs + "\n" + shimsBrs)

  // Facade access is emitted as `ga.<Module>.<member>` (see the Select case).
  // `ga` is a local alias for GetGlobalAA() — needed because a BrightScript
  // statement can't start with a call (`GetGlobalAA().Nav.push(…)` is a syntax
  // error) but CAN start with an identifier (`ga.Nav.push(…)`). So bind
  // `ga = GetGlobalAA()` at the top of every emitted function that references it.
  private def injectGa(code: String): String =
    val lines = code.split("\n", -1)
    val sb = new StringBuilder
    var i = 0
    while i < lines.length do
      val line = lines(i)
      sb.append(line).append("\n")
      val t = line.trim
      if t.startsWith("function ") || t.startsWith("sub ") then
        var j = i + 1
        val body = new StringBuilder
        while j < lines.length && !{ val e = lines(j).trim; e.startsWith("end function") || e.startsWith("end sub") } do
          body.append(lines(j)).append("\n"); j += 1
        val b = body.toString
        if b.contains("ga.") && !b.contains("ga = GetGlobalAA()") then
          sb.append("  ga = GetGlobalAA()\n")
      i += 1
    if sb.nonEmpty && sb.last == '\n' then sb.deleteCharAt(sb.length - 1)
    sb.toString

  private def write(rel: String, content: String): Unit =
    val p = outDir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.write(p, content.getBytes(StandardCharsets.UTF_8))

  // ── channel templates ─────────────────────────────────────────────────────
  private def manifest(title: String): String =
    s"""# Generated by universal-ui's Roku compiler
       |title=$title
       |major_version=1
       |minor_version=0
       |build_version=1
       |ui_resolutions=fhd
       |rsg_version=1.3
       |splash_color=#FFFFFF
       |splash_min_time=0
       |""".stripMargin

  private val mainBrs: String =
    """' Generated by universal-ui's Roku compiler — channel entry point.
      |sub Main()
      |    screen = CreateObject("roSGScreen")
      |    port = CreateObject("roMessagePort")
      |    screen.setMessagePort(port)
      |    scene = screen.CreateScene("AppScene")
      |    screen.show()
      |    while true
      |        msg = wait(0, port)
      |        if type(msg) = "roSGScreenEvent"
      |            if msg.isScreenClosed() then return
      |        end if
      |    end while
      |end sub
      |""".stripMargin

  private val sceneXml: String =
    """<?xml version="1.0" encoding="utf-8" ?>
      |<!-- Generated by universal-ui's Roku compiler — root scene. -->
      |<component name="AppScene" extends="Scene">
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
    """' Generated by universal-ui's Roku compiler.
      |sub init()
      |    ga = GetGlobalAA()
      |    ga.appScene = m.top
      |    ga.appRoot = m.top.findNode("root")
      |    m.top.setFocus(true)
      |    rtMain()
      |end sub
      |""".stripMargin

  private val appBrsHeader: String =
    "' Generated by universal-ui's Roku compiler — the translated example app.\n\n"

  /** The core of the Scala runtime: string conversion, collection, String,
   *  Option and Try/Either operations, closure invocation (`RtInvoke`) and the
   *  `RtTodo` placeholder. The object model and stdlib shims are `shimsBrs`. */
  private val runtimeBrs: String =
    """' universal-ui Roku runtime (generated by the Roku compiler).
      |
      |' ── misc helpers ──
      |function _str(v as dynamic) as string
      |    if v = invalid then return ""
      |    if type(v) = "roString" or type(v) = "String" then return v
      |    k = _rtKind(v)
      |    if k = "aa" then return _rtObjStr(v)
      |    if k = "arr" then return _rtArrStr(v)
      |    if k = "fn" then return "<function>"
      |    return v.toStr()
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
      |function _arrMap(xs as object, cl as dynamic) as object
      |    r = []
      |    if xs <> invalid
      |        for each e in xs
      |            r.push(RtInvoke(cl, [e]))
      |        end for
      |    end if
      |    return r
      |end function
      |function _arrFlatMap(xs as object, cl as dynamic) as object
      |    r = []
      |    if xs <> invalid
      |        for each e in xs
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
      |        for each e in xs
      |            if RtInvoke(cl, [e]) then r.push(e)
      |        end for
      |    end if
      |    return r
      |end function
      |function _arrFilterNot(xs as object, cl as dynamic) as object
      |    r = []
      |    if xs <> invalid
      |        for each e in xs
      |            if not RtInvoke(cl, [e]) then r.push(e)
      |        end for
      |    end if
      |    return r
      |end function
      |sub _arrForeach(xs as object, cl as dynamic)
      |    if xs <> invalid
      |        for each e in xs
      |            RtInvoke(cl, [e])
      |        end for
      |    end if
      |end sub
      |function _arrExists(xs as object, cl as dynamic) as boolean
      |    if xs <> invalid
      |        for each e in xs
      |            if RtInvoke(cl, [e]) then return true
      |        end for
      |    end if
      |    return false
      |end function
      |function _arrForall(xs as object, cl as dynamic) as boolean
      |    if xs <> invalid
      |        for each e in xs
      |            if not RtInvoke(cl, [e]) then return false
      |        end for
      |    end if
      |    return true
      |end function
      |function _arrFind(xs as object, cl as dynamic) as dynamic
      |    if xs <> invalid
      |        for each e in xs
      |            if RtInvoke(cl, [e]) then return e
      |        end for
      |    end if
      |    return invalid
      |end function
      |function _contains(xs as object, x as dynamic) as boolean
      |    if xs <> invalid
      |        for each e in xs
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
      |function _take(xs as object, n as integer) as object
      |    r = [] : i = 0
      |    if xs <> invalid
      |        for each e in xs
      |            if i < n then r.push(e)
      |            i = i + 1
      |        end for
      |    end if
      |    return r
      |end function
      |function _drop(xs as object, n as integer) as object
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
      |        for each e in xs
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
      |function _split(s as dynamic, sep as dynamic) as object
      |    t = _str(s)
      |    if _str(sep) = "" then return t.Split("")
      |    return t.Tokenize(_str(sep))
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
      |        arg = t.error : if arg = invalid then arg = t.value
      |        return RtInvoke(fa, [arg])
      |    end if
      |    v = invalid : if t <> invalid then v = t.value
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
      |
      |' ── Scala stdlib collections + synthetics (best-effort; keep the channel loading) ──
      |function Map(o = invalid as dynamic) as object : return _iif(o = invalid, {}, o) : end function
      |function Set(o = invalid as dynamic) as object : return _iif(o = invalid, [], o) : end function
      |function Tuple2(a = invalid as dynamic, b = invalid as dynamic) as object : return { _1: a, _2: b } : end function
      |function wrapRefArray(a = invalid as dynamic) as dynamic : return a : end function
      |function intWrapper(x = invalid as dynamic) as dynamic : return x : end function
      |' -- Scala stdlib shims added for universal-ui's component model --
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
      |
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
      |function ThreadLocal_initialValue(self as object) as dynamic
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
      |    for each cb in p.callbacks
      |        RtInvoke(cb, [v])
      |    end for
      |    p.callbacks = []
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
      |""".stripMargin