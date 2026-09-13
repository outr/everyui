package universal.compiler

import scala.quoted.*
import scala.tasty.inspector.*
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import scala.collection.mutable

/** universal-ui's compiler, Swift backend — THE SWIFT SPIKE (DESIGN.md, "tvOS").
 *
 *  Derived from [[RokuEmitter]] by tools/make-swift-emitter.py: the same lowering decisions (the object
 *  model, defunctionalised closures, A-normalisation, pattern matching as if-chains, the stdlib shims),
 *  printed as Swift over one dynamic value type `V` (swift/runtime/ScalaRuntime.swift) instead of
 *  BrightScript over assoc arrays. Type-erased on purpose: the spike settles whether the object model, the
 *  suite and reactify's cyclic graph work on Apple's runtime, not how well Scala's types map onto Swift's.
 *
 *  The output is one `App.swift`. Every emitted function is `throws -> V`; instance fields are
 *  `this["name"]`; a method slot is called `this["m"](this, …)`; a lambda is `rtClosure(closureN, env)`.
 */
class SwiftEmitter(
  outDir: Path,
  sourceRoot: Option[Path] = None,
  projectName: String = "app"
) extends Emitter:

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

    // A Swift string literal.
    def swStr(str: String): String =
      val sb = new StringBuilder("\"")
      str.foreach {
        case '\\' => sb.append("\\\\")
        case '"'  => sb.append("\\\"")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c if c < ' ' => sb.append("\\u{" + c.toInt.toHexString + "}")
        case c => sb.append(c)
      }
      sb.append("\"").toString
    /** A Scala string as a `V`. */
    def brStr(str: String): String = "V.vStr(" + swStr(str) + ")"
    val NULL = "V.vNull"
    def vInt(i: Long): String = s"V.vInt($i)"
    def vDbl(d: Double): String =
      if d.isNaN then "V.vDbl(Double.nan)"
      else if d.isPosInfinity then "V.vDbl(Double.infinity)"
      else if d.isNegInfinity then "V.vDbl(-Double.infinity)"
      else s"V.vDbl(${d.toString})"
    def rtArr(items: List[String]): String = "rtArr([" + items.mkString(", ") + "])"
    def rtObj(pairs: List[String]): String = if pairs.isEmpty then "rtObj([:])" else "rtObj([" + pairs.mkString(", ") + "])"
    def field(recv: String, name: String): String = s"$recv[${swStr(name)}]"

    def emitConst(c: Constant): String = c match
      case IntConstant(i)     => vInt(i)
      case LongConstant(l)    => vInt(l)
      case DoubleConstant(d)  => vDbl(d)
      case FloatConstant(f)   => vDbl(f.toDouble)
      case StringConstant(s)  => brStr(s)
      case BooleanConstant(b) => s"V.vBool($b)"
      case CharConstant(ch)   => brStr(ch.toString)
      case NullConstant()     => NULL
      case UnitConstant()     => NULL
      case other              => brStr(other.value.toString)

    def todo(t: Tree): String =
      val label = t.getClass.getSimpleName
      todos += s"unsupported: $label"
      s"RtTodo(${swStr(label)})"

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

    // Swift identifiers can't contain `$`, and its keywords are reserved (a Scala local named `default`
    // or `in`, or scalac's `_$1` params, collide).
    val brReserved = Set(
      "associatedtype", "class", "deinit", "enum", "extension", "fileprivate", "func", "import", "init",
      "inout", "internal", "let", "open", "operator", "private", "precedencegroup", "protocol", "public",
      "rethrows", "static", "struct", "subscript", "typealias", "var", "break", "case", "catch", "continue",
      "default", "defer", "do", "else", "fallthrough", "for", "guard", "if", "in", "repeat", "return",
      "throw", "switch", "where", "while", "Any", "as", "await", "false", "is", "nil", "self", "Self",
      "super", "throws", "true", "try", "async", "actor", "this", "ga", "V", "env", "args"
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
      if brReserved(s) then s + "_" else s


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
      "==" -> "==", "!=" -> "!=", "+" -> "+", "-" -> "-", "*" -> "*",
      "/" -> "/", "%" -> "%", "<" -> "<", ">" -> ">", "<=" -> "<=", ">=" -> ">=",
      "&&" -> "&&", "||" -> "||"
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
        (fvs.map { case (nm, _) => s"  $nm = ${field("env", "fv_" + nm)}" } ++
         params.zipWithIndex.map { case (p, i) => s"  $p = args[${vInt(i)}]" }).mkString("\n")
      // A value-returning lambda must RETURN its expression; a Unit lambda falls
      // through to `return V.vNull`.
      val wantRet = !(dd.returnTpt.tpe =:= TypeRepr.of[Unit])
      val body = dd.rhs.map(b => emitBody(b, wantRet)).getOrElse("")
      closures.append(s"func $name(_ env: V, _ args: V) throws -> V {\n")
      closures.append(s"  let this: V = ${field("env", "self")}\n")
      if binds.nonEmpty then closures.append(binds).append("\n")
      if body.nonEmpty then closures.append(body).append("\n")
      closures.append("  return V.vNull\n")
      closures.append("}\n\n")
      val envSelf = if currentClass.exists then "this" else NULL
      // A captured box / thunk travels as itself (raw name), never unboxed.
      val envFvs = fvs.map { case (nm, _) => s"${swStr("fv_" + nm)}: $nm" }
      s"rtClosure($name, ${rtObj(s"${swStr("self")}: $envSelf" :: envFvs)})"

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
          val binds = fvs.map { case (nm, _) => s"  $nm = ${field("env", "fv_" + nm)}" }.mkString("\n")
          val unitBody = body.tpe.widen =:= TypeRepr.of[Unit]
          val code = emitInto(body, if unitBody then "" else skReturn)
          closures.append(s"func $name(_ env: V, _ args: V) throws -> V {\n")
          closures.append(s"  let this: V = ${field("env", "self")}\n")
          if binds.nonEmpty then closures.append(binds).append("\n")
          if code.trim.nonEmpty then closures.append(code).append("\n")
          closures.append("  return V.vNull\n")
          closures.append("}\n\n")
          val envSelf = if currentClass.exists then "this" else NULL
          val envFvs = fvs.map { case (nm, _) => s"${swStr("fv_" + nm)}: $nm" }
          s"rtClosure($name, ${rtObj(s"${swStr("self")}: $envSelf" :: envFvs)})"

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
            case NamedArg(n, v) => parts += s"${swStr(n)}: ${emitArgVal(v)}"
            case other =>
              params.lift(i) match
                case Some(p) => parts += s"${swStr(p.name)}: ${emitArgVal(other)}"
                case None    => parts += s"${swStr("_" + i)}: ${emitArgVal(other)}"
        i += 1
      }
      rtObj(parts.toList)

    def emitFacadeArgs(args: List[Term]): String =
      val kept = args.filterNot(isDefaultArg)
      val pos   = mutable.ListBuffer[String]()
      val named = mutable.ListBuffer[String]()
      kept.foreach {
        case NamedArg(n, v) => named += s"${swStr(n)}: ${emitArgVal(v)}"
        case other          => pos += emitArgVal(other)
      }
      if named.isEmpty then pos.mkString(", ")
      else if pos.isEmpty then rtObj(named.toList)
      else pos.mkString(", ") + ", " + rtObj(named.toList)

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
      if fieldClash then named0 + "__m" else named0

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
        case None                 => "this"
        case Some(r) if isThis(r) => "this"
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
        if simple || argList.size > 8 then s"${field(recvStr, slotName(sy))}($recvStr$tail)"
        else s"RtVcall${argList.size}($recvStr, ${swStr(slotName(sy))}$tail)"

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
      e.matches("[A-Za-z_][A-Za-z0-9_]*") || e == NULL || e.matches("V\\.v(Int|Dbl|Bool)\\([^()]*\\)")
        || e.matches("V\\.vStr\\(\"(?:[^\"\\\\]|\\\\.)*\"\\)")

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
              pendingStack.top.insert(at, s"  $tmp = try ${outs(j)}")
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
      else rtArr(flattenVarargs(args).map(emitArgVal))

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
      if !currentClass.exists || !target.exists then "this"
      else
        var c = currentClass
        var ref = "this"
        var guard = 0
        while c.exists && !(c == target || c.typeRef.baseClasses.contains(target)) && anonOuter.contains(c) && guard < 16 do
          ref = field(ref, "__outer")
          c = anonOuter(c)
          guard += 1
        ref
    def anonCaptureRef(sy: Symbol): Option[String] =
      var c = currentClass
      var ref = "this"
      var guard = 0
      var found: Option[String] = None
      while found.isEmpty && c.exists && guard < 16 do
        if anonCaptures.get(c).exists(_.contains(sy)) then found = Some(field(ref, "__fv_" + brSafe(sy.name)))
        else anonOuter.get(c) match
          case Some(o) =>
            ref = field(ref, "__outer")
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
          s"${swStr(fieldKey(f))}: ${emitArgVal(v)}"
      }
      s"_caseCopy(${emitTerm(recv)}, ${rtObj(overrides)})"

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
      if name == "Nil" then rtArr(Nil)
      else if name == "None" && sym.exists && sym.fullName.endsWith(".None") then NULL
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
            if boxed then field(ref, "v") else if byName then s"RtForce($ref)" else ref
          case None =>
            if inParentArgs && sym.flags.is(Flags.ParamAccessor) && currentClass.exists && sym.owner == currentClass then brSafe(name)
            else if isModelMember(sym) && inCurrentClass(sym.owner) then emitModelCall(None, sym, Nil)
            else if inCurrentClass(sym.owner) then field("this", fieldKey(sym))
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
            Some(if pieces.isEmpty then brStr("") else pieces.mkString(" + "))
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
        case "sqrt"  => s"rtSqrt(${a(0)})"
        case "pow"   => s"rtPow(${a(0)}, ${a(1)})"
        case "sin"   => s"rtSin(${a(0)})"
        case "cos"   => s"rtCos(${a(0)})"
        case "tan"   => s"rtTan(${a(0)})"
        case "atan"  => s"rtAtan(${a(0)})"
        case "exp"   => s"rtExp(${a(0)})"
        case "log"   => s"rtLog(${a(0)})"
        case "abs"   => s"rtAbs(${a(0)})"
        case "min"   => s"RtMin(${a(0)}, ${a(1)})"
        case "max"   => s"RtMax(${a(0)}, ${a(1)})"
        case "floor" => s"rtFloor(${a(0)})"
        case "ceil"  => s"rtCeil(${a(0)})"
        case _       => s"rtRound(${a(0)})"

    /** Milliseconds per unit of a `scala.concurrent.duration` conversion (`5.seconds`). */
    def durationFactor(conv: Term, unit: String): Option[String] =
      val sy = stripTypeApply(conv).symbol
      if !(sy.exists && Set("DurationInt", "DurationLong", "DurationDouble").contains(sy.name)) then None
      else unit match
        case "nanoseconds" | "nanos" | "nanosecond" | "nano"      => Some(vDbl(0.000001))
        case "microseconds" | "micros" | "microsecond" | "micro"  => Some(vDbl(0.001))
        case "milliseconds" | "millis" | "millisecond" | "milli"  => Some(vInt(1))
        case "seconds" | "second"                                 => Some(vInt(1000))
        case "minutes" | "minute"                                 => Some(vInt(60000))
        case "hours" | "hour"                                     => Some(vInt(3600000))
        case "days" | "day"                                       => Some(vInt(86400000))
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
        field("ga", "__mod_" + typeName(mc))
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
            return rtArr(flattenVarargs(args).map(emitArgVal))
          caseApply(t.tpe) match
            case Some(_) if isCaseCtor(fn, t.tpe) && isModel(t.tpe.typeSymbol) =>
              return s"${typeName(t.tpe.typeSymbol)}_new(${emitArgs(args, fn.symbol)})"
            case Some(fields) if isCaseCtor(fn, t.tpe) =>
              val flat = flattenVarargs(args).filterNot(isDefaultArg)
              val fs = fields.zip(flat).map((f, a) => s"${swStr(f)}: ${emitArgVal(a)}")
              val fieldList = rtArr(fields.take(flat.size).map(f => brStr(f)))
              return rtObj(s"${swStr("_cls")}: ${brStr(t.tpe.typeSymbol.name)}" :: s"${swStr("_fields")}: $fieldList" :: fs)
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
              s"RtThrow(AssertionError_new(${if args.isEmpty then NULL else emitArgVal(args.head)}))"
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
              s"_distinct(${rtArr(flattenVarargs(args).map(emitArgVal))})"
            case Select(q, "apply") if isMapTpe(t.tpe) && q.tpe.widen.typeSymbol.flags.is(Flags.Module) =>
              s"_mapFromPairs(${varargsArray(args)})"
            case _ if ownerIs(fn, "scala.math.package") && mathNames.contains(fn.symbol.name) =>
              emitMath(fn.symbol.name, args)
            case Select(q, "apply") if q.symbol.exists && q.symbol.fullName.startsWith("scala.StringContext") =>
              varargsArray(args)
            case Select(recv, "s") if fqnOf(recv.tpe) == "scala.StringContext" =>
              s"_strInterp(${emitTerm(recv)}, ${varargsArray(args)})"
            case Select(recv, "length") if fqnOf(recv.tpe) == "java.lang.String" && args.isEmpty =>
              s"rtLen(${emitTerm(recv)})"
            case Select(recv, "isEmpty") if fqnOf(recv.tpe) == "java.lang.String" && args.isEmpty =>
              s"(rtLen(${emitTerm(recv)}) == ${vInt(0)})"
            case Select(recv, "indexOf") if fqnOf(recv.tpe) == "java.lang.String" && args.size == 1 =>
              s"rtIndexOf(${emitTerm(recv)}, ${emitArgVal(args.head)})"
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
            // var setter x.p_=(v) → a field write (a statement in Swift, so through the runtime)
            case Select(recv, setter) if setter.endsWith("_=") && args.length == 1 && !userMethodSyms.contains(fn.symbol) =>
              s"rtSet(${emitTerm(recv)}, ${swStr(setter.dropRight(2))}, ${emitTerm(args.head)})"
            case _ if isSuperAccessor(fn.symbol) =>
              superAccessorImpl(fn.symbol) match
                case Some(impl) => s"${modelImplName(impl)}(this${emitArgList(args, impl).map(", " + _).mkString})"
                case None       => NULL
            // Object-model dispatch (see "object model" above).
            case Select(recv, _) if isModelMember(fn.symbol) && !isFacade(fn.symbol) && !isSuper(recv) =>
              emitModelCall(Some(recv), fn.symbol, args)
            case Ident(_) if isModelMember(fn.symbol) && !isFacade(fn.symbol) =>
              emitModelCall(None, fn.symbol, args)
            // `x == null` / `x ne null` on an object: a direct test, not a call.
            case Select(recv, op @ ("==" | "!=" | "eq" | "ne")) if args.length == 1 && nullCompared(recv, args.head).isDefined =>
              val cmp = if op == "==" || op == "eq" then "==" else "!="
              s"(${emitTerm(nullCompared(recv, args.head).get)} $cmp $NULL)"
            case Select(recv, op @ ("==" | "!=")) if args.length == 1 && (isModuleOperand(recv) || isModuleOperand(args.head)) =>
              val e = s"RtIdentity(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              if op == "==" then e else s"(!$e)"
            // Scala equality / identity on non-primitive operands.
            case Select(recv, op @ ("==" | "!=")) if args.length == 1 && !(isPrimLike(recv) && isPrimLike(args.head)) =>
              val e = s"_eq(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              if op == "==" then e else s"(!$e)"
            case Select(recv, op @ ("eq" | "ne")) if args.length == 1 =>
              val e = s"RtIdentity(${emitTerm(recv)}, ${emitArgVal(args.head)})"
              if op == "eq" then e else s"(!$e)"
            case Select(recv, "apply") if args.length == 1 && isCollType(recv) =>
              s"${emitTerm(recv)}[${emitTerm(args.head)}]"
            // Invoking a function VALUE (`f(x)` where f: T => R desugars to
            // f.apply(x)). A function value is a defunctionalised {fn, env}
            // closure, so it must be called via RtInvoke, not `f(x)`.
            case Select(recv, "apply") if isFunctionType(recv.tpe) =>
              val r = emitTerm(recv)
              val a = emitArgs(args, fn.symbol)
              // A closure held in a local is called in place — one call fewer than RtInvoke.
              if r.matches("[A-Za-z_][A-Za-z0-9_]*") then s"${field(r, "fn")}(${field(r, "env")}, ${rtArr(List(a))})"
              else s"RtInvoke($r, ${rtArr(List(a))})"
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
                s"${typeName(currentClass)}_${memberName(fn.symbol, name)}(this$tail)"
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
            case Select(recv, "toLowerCase") => s"rtLower(${emitTerm(recv)})"
            case Select(recv, "toUpperCase") => s"rtUpper(${emitTerm(recv)})"
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
              s"_optContains(${emitTerm(recv)}, ${emitArgVal(args.head)})"
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
            // self method call (private def) → Class_method(this, args)
            case Select(recv, name) if inCurrentClass(fn.symbol.owner) && isThis(recv) =>
              val a = emitArgs(args, fn.symbol)
              s"${typeName(currentClass)}_${memberName(fn.symbol, name)}(this${if a.isEmpty then "" else ", " + a})"
            // A self method referenced as a BARE Ident (`load()` inside another
            // method) — Scala drops the `this.`; emit Class_method(self, …), not
            // `self.load()` (a field-call passes no `self` → "wrong # of params").
            case _ if fn.symbol.exists && fn.symbol.isDefDef && fn.symbol.flags.is(Flags.Method)
                && inCurrentClass(fn.symbol.owner) && !isFacade(fn.symbol) =>
              val a = emitArgs(args, fn.symbol)
              s"${typeName(currentClass)}_${memberName(fn.symbol, fn.symbol.name)}(this${if a.isEmpty then "" else ", " + a})"
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
              // `throw e` is a statement in Swift, not an expression → runtime helper.
              if fe == "throw" || fe == "throw_" then s"RtThrow(${emitArgs(args, fn.symbol)})"
              else s"$fe(${emitArgs(args, fn.symbol)})"
        case _ => emitTerm(t)

    def isThis(t: Term): Boolean = t match
      case th: This => thisRef(th.symbol) == "this"
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
            s"${modelImplName(ss)}(this${emitArgList(sargs).map(", " + _).mkString})"
          else NULL
        case sel @ Select(spr, _) if isSuper(spr) =>
          val ss = sel.symbol
          if ss.exists && userTypeDefs.contains(ss.owner) && (ss.isDefDef || ss.flags.is(Flags.Lazy)) then
            s"${modelImplName(ss)}(this)"
          else NULL
        case Literal(c)  => emitConst(c)
        case th: This    => thisRef(th.symbol)
        case id @ Ident(n) => emitRef(id.symbol, n)
        case Select(q, "toString")             => s"_str(${emitTerm(q)})"
        case Apply(Select(q, "toString"), Nil) => s"_str(${emitTerm(q)})"
        case sel @ Select(_, n @ ("MinValue" | "MaxValue")) if ownerFqn(sel.symbol) == "scala.Double" =>
          if n == "MaxValue" then "RtDoubleMax()" else "RtDoubleMin()"
        case sel @ Select(_, n @ ("MinValue" | "MaxValue")) if ownerFqn(sel.symbol) == "scala.Int" =>
          if n == "MaxValue" then vInt(2147483647L) else vInt(-2147483648L)
        case sel @ Select(_, n @ ("Pi" | "E")) if ownerFqn(sel.symbol) == "scala.math.package" =>
          if n == "Pi" then vDbl(math.Pi) else vDbl(math.E)
        case Select(Apply(conv, List(n)), unit) if durationFactor(conv, unit).isDefined =>
          s"RtDuration((${emitTerm(n)}) * ${durationFactor(conv, unit).get})"
        case Select(q, "toMillis") if fqnOf(q.tpe).startsWith("scala.concurrent.duration") =>
          s"RtDurationMs(${emitTerm(q)})"
        case Select(q, "toSeconds") if fqnOf(q.tpe).startsWith("scala.concurrent.duration") =>
          s"rtFloor(RtDurationMs(${emitTerm(q)}) / ${vInt(1000)})"
        case Select(Apply(f, List(str)), m @ ("nonEmpty" | "isEmpty"))
            if stripTypeApply(f).symbol.exists && stripTypeApply(f).symbol.name == "augmentString" =>
          if m == "nonEmpty" then s"(rtLen(${emitTerm(str)}) > ${vInt(0)})" else s"(rtLen(${emitTerm(str)}) == ${vInt(0)})"
        case Select(q, "toList" | "toSeq" | "toVector") if isMapTpe(q.tpe) => s"_mapToList(${emitTerm(q)})"
        case Select(q, "length") if fqnOf(q.tpe) == "java.lang.String" => s"rtLen(${emitTerm(q)})"
        case ta @ TypeApply(Select(_, "empty"), _) if isMapTpe(ta.tpe) => "_mapEmpty()"
        case ta @ TypeApply(Select(_, "empty"), _) if isSetTpe(ta.tpe) || isListTpe(ta.tpe) => rtArr(Nil)
        case sel @ Select(_, "empty") if isMapTpe(sel.tpe) => "_mapEmpty()"
        case sel @ Select(_, "empty") if isSetTpe(sel.tpe) || isListTpe(sel.tpe) => rtArr(Nil)
        case Select(q, "keys" | "keySet") if isMapTpe(q.tpe) => s"_mapKeys(${emitTerm(q)})"
        case Select(q, "values") if isMapTpe(q.tpe) => s"_mapValues(${emitTerm(q)})"
        case Select(q, "size") if isMapTpe(q.tpe) => s"_mapSize(${emitTerm(q)})"
        case Select(q, "size" | "length") if isCollType(q) => s"rtCount(${emitTerm(q)})"
        // Paren-less Option getters (Option lowers to a plain nullable).
        case Select(q, "isDefined" | "nonEmpty") if isOptionType(q) => s"(${emitTerm(q)} != $NULL)"
        case Select(q, "isEmpty") if isOptionType(q)               => s"(${emitTerm(q)} == $NULL)"
        case Select(q, "get" | "orNull") if isOptionType(q)        => emitTerm(q)
        case Select(q, "toOption") if isTryType(q) || isEitherType(q) => s"_tryToOption(${emitTerm(q)})"
        case Select(q, "toList" | "toSeq" | "toArray" | "toVector" | "toIndexedSeq") if isCollType(q) => emitTerm(q)
        // Paren-less collection getters → runtime helpers.
        case Select(q, "isEmpty") if isCollType(q)   => s"(rtCount(${emitTerm(q)}) == ${vInt(0)})"
        case Select(q, "nonEmpty") if isCollType(q)  => s"(rtCount(${emitTerm(q)}) > ${vInt(0)})"
        case Select(q, "head") if isCollType(q)      => s"${emitTerm(q)}[${vInt(0)}]"
        case Select(q, "last") if isCollType(q)      => s"_last(${emitTerm(q)})"
        case Select(q, "headOption") if isCollType(q) => s"_headOption(${emitTerm(q)})"
        case Select(q, "lastOption") if isCollType(q) => s"_lastOption(${emitTerm(q)})"
        case Select(q, "reverse") if isCollType(q)   => s"_reverse(${emitTerm(q)})"
        case Select(q, "distinct") if isCollType(q)  => s"_distinct(${emitTerm(q)})"
        case Select(q, "sum") if isCollType(q)       => s"_sum(${emitTerm(q)})"
        case Select(q, "min") if isCollType(q)       => s"_minList(${emitTerm(q)})"
        case Select(q, "max") if isCollType(q)       => s"_maxList(${emitTerm(q)})"
        case Select(q, "zipWithIndex") if isCollType(q) => s"_zipWithIndex(${emitTerm(q)})"
        case Select(q, "toDouble" | "toFloat") => s"rtToDbl(${emitTerm(q)})"
        case Select(q, "toInt" | "toLong")     => s"rtToInt(${emitTerm(q)})"
        case Select(q, "tail") if isCollType(q) => s"_drop(${emitTerm(q)}, ${vInt(1)})"
        case TypeApply(Select(q, "asInstanceOf"), _) => emitTerm(q)
        case sel @ Select(_, _) if modelModuleOf(sel.symbol).isDefined => moduleAccessor(modelModuleOf(sel.symbol).get)
        case sel @ Select(q, n) if inParentArgs && isThis(q) && sel.symbol.flags.is(Flags.ParamAccessor) && sel.symbol.owner == currentClass =>
          if isByNameParam(sel.symbol) then s"RtForce(${brSafe(n)})" else brSafe(n)
        case sel @ Select(q, _) if isModelMember(sel.symbol) && !isFacade(sel.symbol) && !isSuper(q) =>
          emitModelCall(Some(q), sel.symbol, Nil)
        case Select(q, "unary_$bang" | "unary_!") => s"(!${emitTerm(q)})"
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
            field(field("ga", qs.name.stripSuffix("$")), brSafe(name))
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
              if parenlessSelf then s"${typeName(currentClass)}_${memberName(s, name)}(this)" else field("this", brSafe(name))
            else moduleSyms.get(s.owner) match
              case Some(prefix) => s"${prefix}_${memberName(s, name)}()"
              // A paren-less getter on ANOTHER user instance is a method, not a
              // field — `this.getter` only holds the function ref, so call it.
              case None if parenlessSelf => s"${typeName(s.owner)}_${memberName(s, name)}(${emitTerm(q)})"
              case None                  => field(emitTerm(q), brSafe(name))
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
            NULL
          else todo(asg)
        case other => todo(other)

    // ── statements / bodies ─────────────────────────────────────────────────
    // A statement-position term whose value is a no-op (a `super.x()` call, an
    // elided Unit) emits the bare literal `invalid`, which isn't a valid
    // BrightScript statement — drop those.
    def stmtTerm(e: String): String =
      val t = e.trim
      // A no-op or a literal in statement position is dropped; anything else is evaluated for its effect.
      if t.isEmpty || t == NULL || isTrivialExpr(t) then ""
      else "  _ = try " + t

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
      case Literal(c)        => (s"($subj == ${emitConst(c)})", Nil)
      case Typed(inner, _)   => patternTest(inner, subj)
      case TypedOrTest(inner, tpt) =>
        val ts = tpt.tpe.typeSymbol
        val innerTests = inner match
          case u: Unapply => { val c = patternClass(u); c.exists && c == ts }
          case _          => false
        val tag =
          if !ts.exists || innerTests then "true"
          else if ts.fullName == "scala.Some" || ts.fullName == "scala.Option" then s"($subj != $NULL)"
          else if ts.flags.is(Flags.Case) && !ts.fullName.matches("scala\\.Tuple\\d+") then
            s"($subj != $NULL && ${field(subj, "_cls")} == ${brStr(ts.name)})"
          else "true"
        val (it, ib) = patternTest(inner, subj)
        val test = if it == "true" then tag else if tag == "true" then it else s"$tag && $it"
        (test, ib)
      // Option lowers to a nullable: `Some(x)` is any non-invalid value, bound as-is.
      case u @ Unapply(fun, _, subs)
          if subs.size == 1 && { val c = patternClass(u); c.exists && c.fullName == "scala.Some" } =>
        val (it, ib) = patternTest(subs.head, subj)
        (if it == "true" then s"($subj != $NULL)" else s"($subj != $NULL) && $it", ib)
      case u @ Unapply(_, _, subs) =>
        val cls = patternClass(u)
        val fields = if cls.exists then cls.caseFields.map(_.name) else Nil
        val tag =
          if cls.exists && !cls.fullName.matches("scala\\.Tuple\\d+") then s"($subj != $NULL && ${field(subj, "_cls")} == ${brStr(cls.name)})"
          else if cls.exists then s"($subj != $NULL)"
          else "true"
        val subT = subs.zipWithIndex.map { case (sp, i) =>
          patternTest(sp, field(subj, fields.lift(i).map(brSafe).getOrElse("_" + (i + 1)))) }
        val test = (tag :: subT.map(_._1)).filter(_ != "true").mkString(" && ")
        ((if test.isEmpty then "true" else test), subT.flatMap(_._2))
      // A stable identifier (a case object, `Nil`, `None`, a val) matches by
      // equality. Without this it fell through to `true` and ALWAYS matched.
      case t: Term if (t match { case Ident(_) | Select(_, _) => true; case _ => false })
                      && t.symbol.exists && (t.symbol.flags.is(Flags.Module) || t.symbol.isValDef) =>
        val v = emitTerm(t)
        // An `object` is a single instance: matching `case Conclusion.Continue =>` is an identity test.
        if v == NULL then (s"($subj == $NULL)", Nil)
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
      else if sk == skReturn then s"  return try $v"
      else s"  $sk = try $v"
    def sinkUnit(sk: String): String =
      if sk == "" then "" else if sk == skReturn then s"  return $NULL" else s"  $sk = $NULL"

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
        val head = withStatement(s"  if (try ${emitTerm(c)}).rtTrue {")
        val thenPart = emitInto(a, sk)
        if sk == "" && isUnit(b) then s"$head\n$thenPart\n  }"
        else s"$head\n$thenPart\n  } else {\n${emitInto(b, sk)}\n  }"
      case Match(scrut, cases) => emitMatchStat(scrut, cases, sk)
      case Try(body, cases, fin) => emitTryStat(body, cases, fin, sk)
      case While(cond, body) =>
        val head = withStatement(s"  while (try ${emitTerm(cond)}).rtTrue {")
        List(head, emitInto(body, ""), "  }", sinkUnit(sk)).filter(_.trim.nonEmpty).mkString("\n")
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
          List(emitInto(rhs, lhs), s"  $lhs = ${rtObj(List(s"${swStr("v")}: $lhs"))}").filter(_.trim.nonEmpty).mkString("\n")
        else emitInto(rhs, lhs)
      case ValDef(_, _, None) => ""
      case Assign(lhs, rhs) if isModuleVar(lhs.symbol) =>
        val k = s"${modulePrefix(lhs.symbol.owner)}_${brSafe(lhs.symbol.name)}"
        val tmp = s"__mvset_$k"
        List(emitInto(rhs, tmp), s"  _ = try ${k}__set($tmp)").filter(_.trim.nonEmpty).mkString("\n")
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
            s" && ($ge)"
          }.getOrElse("")
          val cond = (if test == "true" then "V.vBool(true)" else test) + guard
          sb += (if first then s"  if (try $cond).rtTrue {" else s"  } else if (try $cond).rtTrue {")
          first = false
          binds.foreach(bd => sb += s"    $bd")
          val body = emitInto(cd.rhs, sk)
          if body.trim.nonEmpty then sb += body
          if test == "true" && guard.isEmpty then exhaustive = true
      }
      if first then List(noMatch).filter(_.trim.nonEmpty)
      else
        if !exhaustive && noMatch.nonEmpty then
          sb += "  } else {"
          sb += noMatch
        sb += "  }"
        sb.toList

    def emitMatchStat(scrut: Term, cases: List[CaseDef], sk: String): String =
      val mv = s"_mt${matchCounter}"; matchCounter += 1
      val head = withStatement(s"  $mv = try ${emitTerm(scrut)}")
      (head :: emitCasesOn(mv, cases, sk, s"    _ = try RtThrow(MatchError_new($mv))")).mkString("\n")

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
      val err0 = s"_e$n"
      fin match
        case None =>
          sb += "  do {"
          sb += emitInto(body, inner)
          sb += s"  } catch let $err0 as RtError {"
          sb += s"  $ex = $err0.v"
          sb ++= emitCasesOn(ex, cases, inner, s"    throw $err0")
          sb += "  }"
        case Some(f) =>
          val err = s"_te$n"
          sb += s"  $err = $NULL"
          sb += "  do {"
          sb += emitInto(body, inner)
          sb += s"  } catch let $err0 as RtError {"
          if cases.isEmpty then sb += s"    $err = $err0.v"
          else
            sb += "    do {"
            sb += s"    $ex = $err0.v"
            sb ++= emitCasesOn(ex, cases, inner, s"    throw $err0")
            sb += s"    } catch let ${err0}h as RtError {"
            sb += s"      $err = ${err0}h.v"
            sb += "    }"
          sb += "  }"
          sb += emitInto(f, "")
          sb += s"  if ($err != $NULL).rtTrue { throw RtError(v: $err) }"
      if wantsValue then sb += sinkLine(sk, res)
      else if sk == skReturn then sb += s"  return $NULL"
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
      val id = "\"[A-Za-z_$][A-Za-z0-9_$]*\""
      res
        .replaceAll(id + ": " + marker + ", ", "")
        .replaceAll(", " + id + ": " + marker, "")
        .replaceAll(marker + ", ", "")
        .replaceAll(", " + marker, "")
        .replaceAll("\\(" + marker + "\\)", "()")
        .replaceAll(id + ": " + marker, "")
        .replaceAll(marker, NULL)

    // ── object → top-level Obj_member functions ──────────────────────────────
    def emitModule(cd: ClassDef): Unit =
      val base = modulePrefix(cd.symbol)
      cd.body.foreach {
        case dd @ DefDef(name, pcs, _, Some(rhs))
            if userMember(dd.symbol) && name != "<init>" && !name.endsWith("_=") =>
          val ps = termParams(pcs)
          out.append(s"func ${base}_${memberName(dd.symbol, name)}(${ps.map(p => s"_ $p: V").mkString(", ")}) throws -> V {\n")
          out.append(emitBody(rhs, true)).append("\n")
          out.append(s"  return $NULL\n}\n\n")
        case vd @ ValDef(name, _, Some(rhs)) if userMember(vd.symbol) && modelModuleOf(vd.symbol).isEmpty =>
          val mutable = vd.symbol.flags.is(Flags.Mutable)
          out.append(s"func ${base}_${brSafe(name)}() throws -> V {\n")
          unwrap(rhs) match
            case Literal(_) if !mutable => out.append(emitInto(rhs, skReturn)).append("\n")
            case _ =>
              // Scala evaluates an object's vals once; the accessor caches in
              // the global table. The flag is set BEFORE the value so a val that
              // (indirectly) reads itself during init sees null, as the
              // JVM would, rather than recursing.
              val k = s"${base}_${brSafe(name)}"
              out.append(s"  cached = ${field("ga", "__mv_" + k)}\n")
              out.append(s"  if (cached != $NULL).rtTrue { return cached }\n")
              out.append(s"  if !rtHas(ga, ${swStr("__mvs_" + k)}) {\n")
              out.append(s"    ${field("ga", "__mvs_" + k)} = V.vBool(true)\n")
              out.append(emitInto(rhs, field("ga", "__mv_" + k))).append("\n")
              out.append("  }\n")
              out.append(s"  return ${field("ga", "__mv_" + k)}\n")
          out.append("}\n\n")
          // An object `var` also gets a setter. It marks the var initialised so
          // a first READ after a write doesn't re-run the initialiser over it.
          if mutable then
            val k = s"${base}_${brSafe(name)}"
            out.append(s"func ${k}__set(_ v: V) throws -> V {\n")
            out.append(s"  ${field("ga", "__mvs_" + k)} = V.vBool(true)\n")
            out.append(s"  ${field("ga", "__mv_" + k)} = v\n")
            out.append(s"  return $NULL\n}\n\n")
        case _ => ()
      }

    // ── class → constructor fn (assoc + method refs) + method fns ────────────
    def emitClass(cd: ClassDef): Unit =
      val sym = cd.symbol
      val cn  = sym.name
      currentClass = sym
      val ctorParams = termParams(cd.constructor.paramss)

      // constructor
      out.append(s"func ${cn}_new(${ctorParams.map(p => s"_ $p: V").mkString(", ")}) throws -> V {\n")
      out.append(s"  let this: V = rtNew(${swStr(cn)})\n")
      cd.body.foreach {
        case vd: ValDef if userMember(vd.symbol) && vd.symbol.flags.is(Flags.ParamAccessor) =>
          out.append(s"  ${field("this", brSafe(vd.name))} = ${brSafe(vd.name)}\n")
        case vd @ ValDef(name, _, Some(rhs)) if userMember(vd.symbol) =>
          out.append(emitInto(rhs, field("this", brSafe(name)))).append("\n")
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
          out.append(s"  ${field("this", mn)} = rtFn(${cn}_$mn)\n")
        case _ => ()
      }
      out.append("  return this\n")
      out.append("}\n\n")

      // methods
      cd.body.foreach {
        case dd @ DefDef(name, pcs, ret, Some(rhs)) if emittableMethod(dd) =>
          val ps = "_ this: V" :: termParams(pcs).map(p => s"_ $p: V")
          out.append(s"func ${cn}_${memberName(dd.symbol, name)}(${ps.mkString(", ")}) throws -> V {\n")
          val wantRet = !(ret.tpe =:= TypeRepr.of[Unit])
          out.append(emitBody(rhs, wantRet)).append("\n")
          out.append(s"  return $NULL\n")
          out.append("}\n\n")
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
          val ps = "_ this: V" :: termParams(dd.paramss).map(p => s"_ $p: V")
          sb.append(s"func ${modelImplName(dd.symbol)}(${ps.mkString(", ")}) throws -> V {\n")
          val wantRet = !(dd.returnTpt.tpe =:= TypeRepr.of[Unit])
          sb.append(emitBody(dd.rhs.get, wantRet)).append("\n")
          sb.append(s"  return $NULL\n}\n\n")
        case vd: ValDef if vd.symbol.flags.is(Flags.Lazy) && vd.rhs.isDefined =>
          val k = brSafe(vd.name)
          sb.append(s"func ${modelImplName(vd.symbol)}(_ this: V) throws -> V {\n")
          sb.append(s"  cached = ${field("this", "__lz_" + k)}\n")
          sb.append(s"  if (cached != $NULL).rtTrue { return cached }\n")
          sb.append(s"  if !rtHas(this, ${swStr("__lzs_" + k)}) {\n")
          sb.append(s"    ${field("this", "__lzs_" + k)} = V.vBool(true)\n")
          sb.append(emitInto(vd.rhs.get, field("this", "__lz_" + k))).append("\n")
          sb.append("  }\n")
          sb.append(s"  return ${field("this", "__lz_" + k)}\n}\n\n")
        case vd: ValDef if isVirtualField(vd.symbol) =>
          sb.append(s"func ${modelImplName(vd.symbol)}(_ this: V) throws -> V {\n")
          sb.append(s"  return ${field("this", fieldKey(vd.symbol))}\n}\n\n")
        case _ => ()
      }

      // init: super -> mixins -> own fields and statements, guarded for diamonds
      val guard = swStr("__i_" + cn)
      sb.append(s"func ${cn}__init(_ this: V${ctorParams.map(p => s", _ $p: V").mkString}) throws -> V {\n")
      sb.append(s"  if rtHas(this, $guard) { return $NULL }\n")
      sb.append(s"  this[$guard] = V.vBool(true)\n")
      cd.parents.foreach { p =>
        val (ps, pargs) = parentInfo(p)
        if ps.exists && ps != sym && userTypeDefs.contains(ps) then
          val ctor = parentCtor(p)
          inParentArgs = true
          val call = withStatement(s"  _ = try ${initName(ps, ctor)}(this${emitArgList(pargs, ctor).map(", " + _).mkString})")
          inParentArgs = false
          sb.append(call).append("\n")
        else if ps.exists && shimParents.contains(ps.fullName) then
          sb.append(s"  _ = ${shimParents(ps.fullName)}_init(this)\n")
      }
      cd.body.foreach {
        case vd: ValDef if vd.symbol.flags.is(Flags.ParamAccessor) =>
          sb.append(s"  ${field("this", fieldKey(vd.symbol))} = ${brSafe(vd.name)}\n")
        case vd @ ValDef(_, _, rhsOpt) if !vd.symbol.flags.is(Flags.Lazy) && userMember(vd.symbol) =>
          rhsOpt match
            case Some(r) if !isWildcardInit(r) => sb.append(emitInto(r, field("this", fieldKey(vd.symbol)))).append("\n")
            case _                             => sb.append(s"  ${field("this", fieldKey(vd.symbol))} = $NULL\n")
        case _: DefDef | _: ValDef | _: TypeDef | _: ClassDef => ()
        case st: Term =>
          val e = emitStat(st)
          if e.trim.nonEmpty then sb.append(e).append("\n")
        case _ => ()
      }
      sb.append(s"  return $NULL\n}\n\n")

      // secondary constructors: `def this(...) = { this(...); stats }`
      secondaryCtors(sym).zipWithIndex.foreach { case (dd, k) =>
        val ps = termParams(dd.paramss)
        sb.append(s"func ${cn}__init__${k + 1}(_ this: V${ps.map(p => s", _ $p: V").mkString}) throws -> V {\n")
        val stats = unwrap(dd.rhs.get) match
          case Block(ss, e) => ss :+ e
          case other        => List(other)
        stats.foreach {
          case Apply(f, as) if f.symbol.exists && f.symbol.isClassConstructor =>
            sb.append(withStatement(s"  _ = try ${initName(sym, f.symbol)}(this${emitArgList(as, f.symbol).map(", " + _).mkString})")).append("\n")
          case st =>
            val e = emitStat(st)
            if e.trim.nonEmpty then sb.append(e).append("\n")
        }
        sb.append(s"  return $NULL\n}\n\n")
      }

      // a module: one instance, stored BEFORE its init runs (as the JVM sets MODULE$), so init can refer to it
      if sym.flags.is(Flags.Module) then
        val key = swStr("__mod_" + cn)
        sb.append(s"func ${cn}__module() throws -> V {\n")
        sb.append(s"  existing = ga[$key]\n")
        sb.append(s"  if (existing != $NULL).rtTrue { return existing }\n")
        sb.append(s"  let this: V = rtNew(${swStr(cn)})\n")
        sym.typeRef.baseClasses.reverse.foreach { bc =>
          shimParents.get(bc.fullName).foreach(shim => sb.append(s"  _ = ${shim}__install(this)\n"))
        }
        vtableFor(sym).foreach { case (slot, impl) => sb.append(s"  ${field("this", slot)} = rtFn($impl)\n") }
        sb.append(s"  ga[$key] = this\n")
        sb.append(s"  _ = try ${cn}__init(this)\n")
        sb.append("  return this\n}\n\n")
      // constructor: allocate, captures, runtime-parent slots, vtable, init
      else if !isTrait && !sym.flags.is(Flags.Abstract) then
        sb.append(s"func ${cn}_new(${(extraParams ++ ctorParams).map(p => s"_ $p: V").mkString(", ")}) throws -> V {\n")
        sb.append(s"  let this: V = rtNew(${swStr(cn)})\n")
        extraParams.foreach(ep => sb.append(s"  ${field("this", ep)} = $ep\n"))
        if sym.flags.is(Flags.Case) then
          sb.append(s"  ${field("this", "_fields")} = ${rtArr(sym.caseFields.map(f => brStr(fieldKey(f))))}\n")
        sym.typeRef.baseClasses.reverse.foreach { bc =>
          shimParents.get(bc.fullName).foreach(shim => sb.append(s"  _ = ${shim}__install(this)\n"))
        }
        vtableFor(sym).foreach { case (slot, impl) => sb.append(s"  ${field("this", slot)} = rtFn($impl)\n") }
        sb.append(s"  _ = try ${cn}__init(this${ctorParams.map(p => ", " + p).mkString})\n")
        sb.append("  return this\n}\n\n")
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
      val outerArg = if currentClass.exists then thisRef(currentClass) else NULL
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
    // @main → rtMain(): the entry the harness calls.
    // Case objects are initialised once, up front (the emitted code reads them straight from the global
    // table); a harness that skips `rtMain` calls `rtInitModules` itself.
    out.append("func rtInitModules() throws -> V {\n")
    for mc <- modelModules.toList.filter(_.flags.is(Flags.Case)).sortBy(typeName) do
      out.append(s"  _ = try ${typeName(mc)}__module()\n")
    out.append(s"  return $NULL\n}\n\n")
    out.append("func rtMain() throws -> V {\n")
    out.append("  _ = try rtInitModules()\n")
    for dd <- mains; body <- dd.rhs do
      out.append(emitBody(body, false)).append("\n")
    out.append(s"  return $NULL\n}\n")

  // ── output ─────────────────────────────────────────────────────────────
  def writeOutput(): Unit =
    val appCode = out.toString + "\n// ── defunctionalised closures ──\n" + closures.toString
    write("App.swift", "// Generated by universal-ui's compiler (Swift backend) — the translated program.\n\n" + declareLocals(appCode))

  /** BrightScript creates a local on first assignment; Swift needs a declaration. Every name a function
    * assigns (a Scala local, a hoisted temp, a pattern binding) is declared once at the top of its body. */
  private def declareLocals(code: String): String =
    val lines = code.split("\n", -1)
    val sb = new StringBuilder
    val param = """_ ([A-Za-z_][A-Za-z0-9_]*): V""".r
    val assign = """^\s*([A-Za-z_][A-Za-z0-9_]*) = """.r
    var i = 0
    while i < lines.length do
      val line = lines(i)
      if line.startsWith("func ") then
        val params = param.findAllMatchIn(line).map(_.group(1)).toSet
        var j = i + 1
        val body = mutable.ListBuffer[String]()
        while j < lines.length && lines(j) != "}" do
          body += lines(j)
          j += 1
        val lets = body.collect { case l if l.trim.startsWith("let ") =>
          l.trim.drop(4).takeWhile(c => c.isLetterOrDigit || c == '_') }.toSet
        val assigned = mutable.LinkedHashSet[String]()
        body.foreach { l =>
          assign.findFirstMatchIn(l).foreach { m =>
            val n = m.group(1)
            if !params(n) && !lets(n) && n != "this" && n != "_" then assigned += n
          }
        }
        sb.append(line).append("\n")
        assigned.foreach(n => sb.append(s"  var $n: V = V.vNull\n"))
        body.foreach(l => sb.append(l).append("\n"))
        i = j
      else
        sb.append(line).append("\n")
        i += 1
    sb.toString

  private def write(rel: String, content: String): Unit =
    val p = outDir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.write(p, content.getBytes(StandardCharsets.UTF_8))
