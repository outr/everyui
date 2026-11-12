package everyui.sbt

import org.objectweb.asm.{ClassReader, Handle, Opcodes, Type}
import org.objectweb.asm.tree.*
import sbt.*
import sbt.io.IO

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** Every reference compiled classes make to an Android class, method or field newer than the oldest Android they are
  * to run on, where nothing keeps it from running there: the NoClassDefFoundError, NoSuchMethodError or VerifyError a
  * device of that age meets. On 2026-11-12 an unguarded `WindowInsets.Type.ime()` (Android 11) in the activity's insets
  * listener crashed every EveryUI app at launch on Android 7 to 10; the example, built for 11 and up, never ran there.
  *
  * The levels are the SDK's own (`platforms/android-N/data/api-versions.xml`: each class, method and field with the
  * level it came in at, a member found through the classes it is inherited from). A reference is guarded where the code
  * only reaches it after `Build.VERSION.SDK_INT` was compared with a constant (`>= N`, `> N`, or the other way round
  * with the reference on the far side), followed through the method's branches, its `try` blocks included, and a local
  * holding SDK_INT stands for it. One that is not guarded in its own method is still guarded where every call of that
  * method in the classes checked is, or is in a method that is: a class of its own touched only behind a guard (how
  * the code keeps a newer class from being loaded at all), a lambda made behind one. A method of a class whose own
  * parents are newer runs only once that class has loaded, so on a device that has them; one overriding a framework
  * method is reached by the framework too, only where that method is, and through the objects of its class, so where
  * those are made. A guard this cannot follow (a Boolean kept from the comparison, a helper answering it) is named in
  * `allowed`, by the method holding the reference (`everyui.render.Host$.safeFrom`), with why it is safe beside it. */
object AndroidApiCheck {
  /** A reference that can run below its level: where it is (`class.method(line)`), what it names, and its level. */
  final case class Finding(method: String, line: Int, reference: String, since: Int) {
    override def toString: String = s"$method (line $line): $reference needs API $since"
  }

  /** The API levels of the SDK's classes, read from `apiVersions`. */
  final class Levels(val classes: Map[String, Int], val parents: Map[String, Seq[String]],
                     val members: Map[String, Map[String, Int]])

  def levels(apiVersions: File): Levels = {
    val classes = mutable.Map.empty[String, Int]
    val parents = mutable.Map.empty[String, Seq[String]]
    val members = mutable.Map.empty[String, Map[String, Int]]
    val in = javax.xml.stream.XMLInputFactory.newInstance()
    val reader = in.createXMLStreamReader(new java.io.FileInputStream(apiVersions))
    var current: String = null
    var since = 1
    var supers = List.empty[String]
    var own = Map.empty[String, Int]
    def level(attr: String, default: Int): Int = Option(reader.getAttributeValue(null, attr))
      .map(_.takeWhile(_.isDigit)).filter(_.nonEmpty).map(_.toInt).getOrElse(default)
    try while (reader.hasNext) {
      reader.next() match {
        case javax.xml.stream.XMLStreamConstants.START_ELEMENT => reader.getLocalName match {
          case "class" =>
            current = reader.getAttributeValue(null, "name")
            since = level("since", 1)
            supers = Nil
            own = Map.empty
          case "extends" | "implements" if current != null => supers ::= reader.getAttributeValue(null, "name")
          case "method" | "field" if current != null =>
            own += reader.getAttributeValue(null, "name") -> level("since", since)
          case _ => ()
        }
        case javax.xml.stream.XMLStreamConstants.END_ELEMENT if reader.getLocalName == "class" =>
          classes(current) = since
          parents(current) = supers.reverse
          members(current) = own
          current = null
        case _ => ()
      }
    } finally reader.close()
    new Levels(classes.toMap, parents.toMap, members.toMap)
  }

  /** What `checked` (class directories or jars) reaches past `minSdk` unguarded, less what `allowed` names. The rest of
    * the classpath, `others`, is read for the classes' parents only. */
  def check(levels: Levels, minSdk: Int, checked: Seq[File], others: Seq[File],
            allowed: Set[String] = Set.empty): Seq[Finding] =
    new Run(levels, minSdk, checked.flatMap(classNodes(_, ClassReader.SKIP_FRAMES)).map(c => c.name -> c).toMap,
      others.flatMap(classNodes(_, ClassReader.SKIP_CODE)).map(c => c.name -> c).toMap).findings
      .filterNot(f => allowed.contains(f.method.takeWhile(_ != '(')))

  /** `check`, failing with each finding where there is any. */
  def require(levels: Levels, minSdk: Int, checked: Seq[File], others: Seq[File], allowed: Set[String],
              log: String => Unit): Unit = {
    val found = check(levels, minSdk, checked, others, allowed)
    if (found.nonEmpty) sys.error(s"AndroidApiCheck: ${found.size} references past API $minSdk with no " +
      s"Build.VERSION.SDK_INT guard (guard them, or name the method in the allowed list with why it is safe):\n  " +
      found.map(_.toString).mkString("\n  "))
    log(s"AndroidApiCheck: nothing past API $minSdk unguarded in ${checked.map(_.toString).mkString(", ")}")
  }

  /** The `allowed` list of a file: a method a line, `#` and what follows a comment. */
  def allowedIn(file: File): Set[String] =
    if (!file.exists) Set.empty
    else IO.readLines(file).map(_.takeWhile(_ != '#').trim).filter(_.nonEmpty).toSet

  private def classNodes(entry: File, flags: Int): Seq[ClassNode] = {
    def node(bytes: Array[Byte]): ClassNode = {
      val c = new ClassNode()
      new ClassReader(bytes).accept(c, flags)
      c
    }
    def wanted(name: String) = name.endsWith(".class") && !name.startsWith("META-INF/") && name != "module-info.class"
    if (entry.isDirectory) (entry ** "*.class").get().filter(f => wanted(IO.relativize(entry, f).get))
      .map(f => node(IO.readBytes(f)))
    else if (!entry.getName.endsWith(".jar") || entry.getName == "android.jar" || !entry.exists) Nil
    else {
      val zip = new java.util.zip.ZipFile(entry)
      try zip.entries().asScala.filter(e => !e.isDirectory && wanted(e.getName))
        .map(e => node(zip.getInputStream(e).readAllBytes())).toList
      finally zip.close()
    }
  }

  /** A method of the classes checked, by its class, name and descriptor. */
  private final case class Key(owner: String, name: String, desc: String) {
    override def toString: String = owner.replace('/', '.') + "." + name
  }

  /** One check: the facts about the classes, then each reference followed back to its guards. */
  private final class Run(levels: Levels, minSdk: Int, program: Map[String, ClassNode],
                          library: Map[String, ClassNode]) {
    private def parentsOf(name: String): Seq[String] = program.get(name).orElse(library.get(name)) match {
      case Some(c) => Option(c.superName).toSeq ++ c.interfaces.asScala
      case None => levels.parents.getOrElse(name, Nil)
    }

    private val classLevels = mutable.Map.empty[String, Int]
    /** The level a class needs to load: the SDK's for its own, the newest of its parents' for any other. */
    def classLevel(name: String): Int = classLevels.get(name) match {
      case Some(l) => l
      case None =>
        classLevels(name) = 0   // a cycle reads as nothing needed
        val l = levels.classes.get(name) match {
          case Some(l) => l
          case None => (0 +: parentsOf(name).map(classLevel)).max
        }
        classLevels(name) = l
        l
    }

    /** The SDK's level for a member of `owner` or one of its parents, the oldest where more than one has it. */
    def memberLevel(owner: String, member: String, seen: Set[String] = Set.empty): Option[Int] =
      if (seen.contains(owner)) None
      else if (declares(owner, member)) Some(0)
      else {
        val own = levels.members.get(owner).flatMap(_.get(member))
        val inherited = parentsOf(owner).flatMap(memberLevel(_, member, seen + owner))
        (own.toSeq ++ inherited).minOption
      }

    /** Whether a class of the program or a library has `member` itself, a method by its name and descriptor. */
    private def declares(owner: String, member: String): Boolean = program.get(owner).orElse(library.get(owner))
      .exists(c => c.methods.asScala.exists(m => m.name + m.desc == member) ||
        c.fields.asScala.exists(_.name == member))

    /** What a reference needs: its class loaded, and the member itself where the SDK has it. */
    def need(owner: String, member: Option[String]): Int = {
      val o = if (owner.startsWith("[")) Type.getType(owner).getElementType.getInternalName else owner
      if (o.length == 1) 0 else math.max(classLevel(o), member.flatMap(memberLevel(o, _)).getOrElse(0))
    }

    private val subclasses: Map[String, Seq[String]] = program.values.toSeq
      .flatMap(c => (Option(c.superName).toSeq ++ c.interfaces.asScala).map(_ -> c.name))
      .groupBy(_._1).view.mapValues(_.map(_._2)).toMap
    private def below(name: String): Seq[String] =
      name +: subclasses.getOrElse(name, Nil).flatMap(below)
    private def above(name: String): Seq[String] =
      name +: parentsOf(name).filter(program.contains).flatMap(above)

    private val methods: Map[Key, MethodNode] = program.values.toSeq.flatMap(c =>
      c.methods.asScala.map(m => Key(c.name, m.name, m.desc) -> m)).toMap
    /** The methods of the classes checked a call to `owner.name desc` can land in. */
    private def targets(owner: String, name: String, desc: String): Seq[Key] =
      if (!program.contains(owner)) Nil
      else (above(owner) ++ below(owner)).distinct.map(Key(_, name, desc)).filter(methods.contains)

    // every call into a method of the classes checked, with the level known where it is made
    private val callers = mutable.Map.empty[Key, List[(Key, Int)]]
    // for each override of a framework method, where an object it belongs to is made, with the level known there
    private val makers = mutable.Map.empty[Key, List[(Key, Int)]]
    // every reference that can run below its level in its own method
    private val local = mutable.ListBuffer.empty[(Key, Int, String, Int)]

    /** The level of the framework method `key` overrides, the oldest where more than one has it; none if it does
      * not. */
    private def overriding(key: Key): Option[Int] =
      if (key.name == "<init>" || key.name == "<clinit>") None
      else parentsOf(key.owner).flatMap(p => frameworkMember(p, key.name + key.desc, Set.empty)).minOption
    private def frameworkMember(owner: String, member: String, seen: Set[String]): Option[Int] =
      if (seen.contains(owner)) None
      else if (!program.contains(owner) && !library.contains(owner) && levels.classes.contains(owner))
        memberLevel(owner, member)
      else parentsOf(owner).flatMap(frameworkMember(_, member, seen + owner)).minOption

    // the constructors first: what they leave known holds for every other method of their objects
    private val floors = mutable.Map.empty[String, Int]
    private def constructed(name: String): Int = math.max(math.min(floors.getOrElse(name, 0), 1000),
      program.get(name).flatMap(c => Option(c.superName)).map(constructed).getOrElse(0))
    program.values.foreach { c =>
      c.methods.asScala.foreach(m => if (m.name == "<init>" && m.instructions.size > 0) scan(c, m))
    }
    // not what Scala writes for Java: a lambda's deserializer, which names every lambda of the class, and an object's
    // static forwarders, which Scala calls never
    program.values.foreach { c =>
      c.methods.asScala.foreach(m => if (m.name != "<init>" && m.name != "$deserializeLambda$" && !forwarder(c, m) &&
        m.instructions.size > 0) scan(c, m))
    }

    private def forwarder(c: ClassNode, m: MethodNode): Boolean = (m.access & Opcodes.ACC_STATIC) != 0 &&
      program.contains(c.name + "$") && m.instructions.toArray.exists {
        case f: FieldInsnNode => f.getOpcode == Opcodes.GETSTATIC && f.owner == c.name + "$" && f.name == "MODULE$"
        case _ => false
      }

    private def scan(c: ClassNode, m: MethodNode): Unit = {
      val key = Key(c.name, m.name, m.desc)
      val insns = m.instructions.toArray
      val index = insns.zipWithIndex.toMap
      // the locals that only ever hold SDK_INT
      val stores = insns.toSeq.zipWithIndex.collect {
        case (v: VarInsnNode, i) if v.getOpcode == Opcodes.ISTORE => v -> i
      }
      val sdkLocals = stores.groupBy(_._1.`var`).collect {
        case (local, all) if all.forall { case (_, i) => i > 0 && isSdk(insns(i - 1), Set.empty) } => local
      }.toSet
      // the level known at each instruction: at least the class's own, which has loaded for it to run
      // and in a method of an object, what its constructors leave known as they return
      val instance = (m.access & Opcodes.ACC_STATIC) == 0 && m.name != "<init>"
      val start = math.max(math.max(minSdk, classLevel(c.name)), if (instance) constructed(c.name) else 0)
      val known = Array.fill(insns.length)(Int.MaxValue)
      val work = mutable.Queue.empty[Int]
      def reach(i: Int, level: Int): Unit = if (i < insns.length && level < known(i)) {
        known(i) = level
        work.enqueue(i)
      }
      reach(0, start)
      val handlers = m.tryCatchBlocks.asScala.map(t => (index(t.start), index(t.end), index(t.handler)))
      while (work.nonEmpty) {
        val i = work.dequeue()
        val level = known(i)
        handlers.foreach { case (from, to, handler) => if (i >= from && i < to) reach(handler, level) }
        insns(i) match {
          case j: JumpInsnNode =>
            val target = index(j.label)
            guard(insns, i, sdkLocals) match {
              case Some((taken, fallen)) =>
                reach(target, math.max(level, taken))
                reach(i + 1, math.max(level, fallen))
              case None =>
                reach(target, level)
                if (j.getOpcode != Opcodes.GOTO) reach(i + 1, level)
            }
          case s: TableSwitchInsnNode =>
            (s.dflt +: s.labels.asScala.toSeq).foreach(l => reach(index(l), level))
          case s: LookupSwitchInsnNode =>
            (s.dflt +: s.labels.asScala.toSeq).foreach(l => reach(index(l), level))
          case n if ends(n.getOpcode) => ()
          case _ => reach(i + 1, level)
        }
      }
      if (m.name == "<init>") {
        val returns = insns.indices.filter(i => insns(i).getOpcode == Opcodes.RETURN && known(i) != Int.MaxValue)
        val left = if (returns.isEmpty) Int.MaxValue else returns.map(known).min
        floors(c.name) = math.min(floors.getOrElse(c.name, Int.MaxValue), left)
      }
      var line = 0
      insns.zipWithIndex.foreach { case (n, i) =>
        n match {
          case l: LineNumberNode => line = l.line
          case _ => ()
        }
        val level = known(i)
        if (level != Int.MaxValue) {
          def against(needed: Int, what: => String): Unit =
            if (needed > level) local += ((key, line, what, needed))
          def call(owner: String, name: String, desc: String): Unit =
            targets(owner, name, desc).foreach(t => callers(t) = (key, level) :: callers.getOrElse(t, Nil))
          def touch(owner: String): Unit =
            if (program.contains(owner)) targets(owner, "<clinit>", "()V")
              .foreach(t => callers(t) = (key, level) :: callers.getOrElse(t, Nil))
          n match {
            // an exception made on its way to a throw (a case class's productElement past its last), whose newer
            // constructor (IndexOutOfBoundsException(Int), Android 13's) fails there as NoSuchMethodError instead
            case mi: MethodInsnNode if mi.name == "<init>" && thrown(insns, i) => ()
            case mi: MethodInsnNode =>
              against(need(mi.owner, Some(mi.name + mi.desc)), s"${mi.owner}.${mi.name}${mi.desc}")
              call(mi.owner, mi.name, mi.desc)
              touch(mi.owner)
            case f: FieldInsnNode =>
              against(need(f.owner, Some(f.name)), s"${f.owner}.${f.name}")
              touch(f.owner)
            case t: TypeInsnNode =>
              against(need(t.desc, None), t.desc)
              if (t.getOpcode == Opcodes.NEW) {
                touch(t.desc)
                // the framework reaches the methods of what is made here that override its own
                below(t.desc).flatMap(n => program.get(n).toSeq).foreach(sub => sub.methods.asScala.foreach { om =>
                  val k = Key(sub.name, om.name, om.desc)
                  if (overriding(k).nonEmpty) makers(k) = (key, level) :: makers.getOrElse(k, Nil)
                })
              }
            case l: LdcInsnNode => l.cst match {
              case t: Type if t.getSort == Type.OBJECT || t.getSort == Type.ARRAY =>
                against(need(t.getInternalName, None), t.getInternalName)
              case _ => ()
            }
            case d: InvokeDynamicInsnNode =>
              val made = Type.getReturnType(d.desc)
              if (made.getSort == Type.OBJECT) against(need(made.getInternalName, None), made.getInternalName)
              d.bsmArgs.foreach {
                case h: Handle =>
                  against(need(h.getOwner, Some(h.getName + h.getDesc)), s"${h.getOwner}.${h.getName}${h.getDesc}")
                  call(h.getOwner, h.getName, h.getDesc)
                case _ => ()
              }
            case _ => ()
          }
        }
      }
    }

    /** Whether the instruction after `i` throws what `i` made. */
    private def thrown(insns: Array[AbstractInsnNode], i: Int): Boolean = {
      var k = i + 1
      while (k < insns.length && insns(k).getOpcode < 0) k += 1
      k < insns.length && insns(k).getOpcode == Opcodes.ATHROW
    }

    private def ends(op: Int): Boolean =
      (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) || op == Opcodes.ATHROW

    private def isSdk(n: AbstractInsnNode, sdkLocals: Set[Int]): Boolean = n match {
      case f: FieldInsnNode => f.getOpcode == Opcodes.GETSTATIC && f.owner == "android/os/Build$VERSION" &&
        f.name == "SDK_INT"
      case v: VarInsnNode => v.getOpcode == Opcodes.ILOAD && sdkLocals.contains(v.`var`)
      case _ => false
    }
    private def constant(n: AbstractInsnNode): Option[Int] = n match {
      case i: IntInsnNode if i.getOpcode == Opcodes.BIPUSH || i.getOpcode == Opcodes.SIPUSH => Some(i.operand)
      case i: InsnNode if i.getOpcode >= Opcodes.ICONST_M1 && i.getOpcode <= Opcodes.ICONST_5 =>
        Some(i.getOpcode - Opcodes.ICONST_0)
      case l: LdcInsnNode => l.cst match {
        case i: Integer => Some(i.intValue)
        case _ => None
      }
      case _ => None
    }
    /** The levels known where the jump at `i` is taken and where it falls through, where it compares SDK_INT. */
    private def guard(insns: Array[AbstractInsnNode], i: Int, sdkLocals: Set[Int]): Option[(Int, Int)] = {
      // past the labels and line numbers between them
      def back(from: Int): Int = {
        var k = from
        while (k >= 0 && insns(k).getOpcode < 0) k -= 1
        k
      }
      val b = back(i - 1)
      val a = if (b > 0) back(b - 1) else -1
      if (a < 0) None
      else {
        val op = insns(i).getOpcode
        // SDK_INT against n, or n against SDK_INT, with the comparison turned round
        val (n, flip) =
          if (isSdk(insns(a), sdkLocals)) (constant(insns(b)), false)
          else if (isSdk(insns(b), sdkLocals)) (constant(insns(a)), true)
          else (None, false)
        n.flatMap { n =>
          val o = if (!flip) op else op match {
            case Opcodes.IF_ICMPLT => Opcodes.IF_ICMPGT
            case Opcodes.IF_ICMPGT => Opcodes.IF_ICMPLT
            case Opcodes.IF_ICMPLE => Opcodes.IF_ICMPGE
            case Opcodes.IF_ICMPGE => Opcodes.IF_ICMPLE
            case other => other
          }
          o match {
            case Opcodes.IF_ICMPLT => Some((0, n))       // jumps where SDK_INT < n
            case Opcodes.IF_ICMPGE => Some((n, 0))       // jumps where SDK_INT >= n
            case Opcodes.IF_ICMPLE => Some((0, n + 1))   // jumps where SDK_INT <= n
            case Opcodes.IF_ICMPGT => Some((n + 1, 0))   // jumps where SDK_INT > n
            case Opcodes.IF_ICMPEQ => Some((n, 0))
            case _ => None
          }
        }
      }
    }

    private val safeAt = mutable.Map.empty[(Key, Int), Boolean]
    /** Whether `key` only runs where the device has `level`: called only behind a guard (or from what is), or a
      * framework method's override the framework calls only from there. */
    private def safe(key: Key, level: Int): Boolean = safeAt.get((key, level)) match {
      case Some(s) => s
      case None =>
        safeAt((key, level)) = true   // a cycle of calls: safe where whatever enters it is
        def ok(from: Seq[(Key, Int)]): Boolean = from.forall { case (k, known) => known >= level || safe(k, level) }
        val calls = callers.getOrElse(key, Nil)
        val s = overriding(key) match {
          // the framework calls it only where it has the method, from `level` on
          case Some(framework) if framework >= level => ok(calls)
          // the framework calls it wherever its objects are, so it is safe where they are only made behind a guard
          case Some(_) => makers.contains(key) && ok(calls) && ok(makers(key))
          // with no call from the classes checked, an app calls it, wherever it runs
          case None => calls.nonEmpty && ok(calls)
        }
        safeAt((key, level)) = s
        s
    }

    def findings: Seq[Finding] = local.toSeq.distinct.collect {
      case (key, line, what, level) if !safe(key, level) => Finding(key.toString, line, what.replace('/', '.'), level)
    }.sortBy(f => (f.method, f.line))
  }
}
