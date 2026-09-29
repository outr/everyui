package everyui.sbt

import org.objectweb.asm.{ClassReader, ClassWriter, Label, Opcodes, Type}
import org.objectweb.asm.tree.*
import sbt.*
import sbt.io.IO

import scala.jdk.CollectionConverters.*

/** The classpath an APK is dexed from, with every Scala 3 lazy val made one Android can run before API 33.
  *
  * Scala 3 (its standard library, and any library it compiled without `-Ylegacy-lazy-vals`) keeps a lazy val's cell
  * behind a `java.lang.invoke.VarHandle`, found in the class initialiser (`MethodHandles.lookup().findVarHandle(C,
  * "x$lzy1", Object)`) and used through `compareAndSet(Object, Object, Object)`. Android has no VarHandle before API 33:
  * on an Android 11 television the verifier throws out `scala.package$`, and with it the app, before the first frame.
  *
  * So each class holding one is rewritten: the handle becomes a `java.util.concurrent.atomic.AtomicReferenceFieldUpdater`
  * on the same volatile cell (`newUpdater(C, Object, "x$lzy1")`, made by the class itself, which owns the private cell),
  * whose `compareAndSet` takes the same arguments and answers the same. `VarHandle.releaseFence()`
  * (`scala.runtime.Statics.releaseFence`, which immutable collections call once built) becomes `Unsafe.storeFence()`,
  * the fence it stands for (a volatile write before Android 8), through `scala.runtime.Statics$AndroidFence`, a class
  * added to the library.
  *
  * Nothing is swapped for 2.13's, so nothing a later Scala adds is lost: swapping the whole library lost
  * `scala.Double$.compare` under 3.10, and swapping only the classes holding a VarHandle lost the fields of
  * `RedBlackTree$Tree` that Scala 3's `TreeMap` reads. A VarHandle used in any other way fails the build, rather than
  * an APK that cannot start below Android 13 going out. */
object AndroidRuntime {
  private val varHandle = "java/lang/invoke/VarHandle"
  private val updater = "java/util/concurrent/atomic/AtomicReferenceFieldUpdater"
  private val fence = "scala/runtime/Statics$AndroidFence"
  private val unsafe = "sun/misc/Unsafe"
  private val cas = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"

  /** `entries` with each jar or class directory holding a VarHandle replaced by a rewritten jar under `work`. The
    * platform's android.jar is compiled against, never dexed, and is passed through. */
  def classpath(entries: Seq[File], minSdk: String, work: File, log: String => Unit): Seq[File] =
    if (minSdk.toInt >= 33) entries
    else {
      IO.delete(work)
      val out = entries.zipWithIndex.map { case (entry, index) =>
        val classes = read(entry)
        val holding = classes.keySet.filter(n => dexed(n) && contains(classes(n), varHandle))
        if (holding.isEmpty) entry
        else {
          val rewritten = classes ++ holding.map(n => n -> rewrite(classes(n)))
          val fenced = holding.exists(n => contains(rewritten(n), fence))
          val jar = work / s"$index-${entry.getName.stripSuffix(".jar")}.jar"
          write(if (fenced) rewritten + (fence + ".class" -> fenceClass) else rewritten, jar)
          log(s"AndroidApk: ${entry.getName}: the VarHandles of ${holding.size} classes rewritten for API $minSdk")
          jar
        }
      }
      val left = out.flatMap(e => read(e).collect { case (n, b) if dexed(n) && contains(b, varHandle) => s"$n (${e.getName})" })
      if (left.nonEmpty) sys.error(s"AndroidApk: ${left.size} classes still use java.lang.invoke.VarHandle, which Android " +
        s"lacks before API 33 (minSdk $minSdk), in a way AndroidRuntime cannot rewrite:\n  " + left.sorted.mkString("\n  "))
      out
    }

  // what the dexer reads: a class, but not one for a later Java in a multi-release jar (META-INF/versions)
  private def dexed(name: String): Boolean = name.endsWith(".class") && !name.startsWith("META-INF/")

  /** One class with its VarHandles made updaters; a use it does not recognise is left for the check to find. */
  private def rewrite(bytes: Array[Byte]): Array[Byte] = {
    val node = new ClassNode()
    new ClassReader(bytes).accept(node, 0)
    def swap(desc: String) = desc.replace(s"L$varHandle;", s"L$updater;")
    node.fields.asScala.foreach(f => f.desc = swap(f.desc))
    node.methods.asScala.foreach { m =>
      val code = m.instructions
      code.toArray.foreach {
        // lookup(), ldc C, ldc "x$lzy1", ldc V, findVarHandle  becomes  ldc C, ldc V, ldc "x$lzy1", newUpdater
        case i: MethodInsnNode if i.owner == "java/lang/invoke/MethodHandles$Lookup" && i.name == "findVarHandle" =>
          val v = i.getPrevious
          val name = if (v == null) null else v.getPrevious
          val c = if (name == null) null else name.getPrevious
          val lookup = if (c == null) null else c.getPrevious
          (lookup, c, name, v) match {
            case (l: MethodInsnNode, c: LdcInsnNode, n: LdcInsnNode, v: LdcInsnNode) if l.owner == "java/lang/invoke/MethodHandles" &&
              l.name == "lookup" && c.cst.isInstanceOf[Type] && n.cst.isInstanceOf[String] && v.cst.isInstanceOf[Type] =>
              code.remove(l)
              code.remove(n)
              code.insert(v, n)
              code.set(i, new MethodInsnNode(Opcodes.INVOKESTATIC, updater, "newUpdater",
                s"(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;)L$updater;", false))
            case _ =>
          }
        case i: MethodInsnNode if i.owner == varHandle && i.name == "compareAndSet" && i.desc == cas => i.owner = updater
        case i: MethodInsnNode if i.owner == varHandle && i.name == "releaseFence" && i.desc == "()V" => i.owner = fence
        case i: FieldInsnNode => i.desc = swap(i.desc)
        case f: FrameNode =>
          Seq(f.local, f.stack).filter(_ != null).foreach(_.replaceAll(t => if (t == varHandle) updater else t))
        case _ =>
      }
      if (m.localVariables != null) m.localVariables.asScala.foreach(l => l.desc = swap(l.desc))
    }
    val writer = new ClassWriter(0)
    node.accept(writer)
    writer.toByteArray
  }

  /** `releaseFence()`: `sun.misc.Unsafe.storeFence()`, where Scala 2.13's `Statics` falls back to, or before Android 8,
    * which has no `storeFence`, a write to a static volatile. A version 50 class, so its one branch needs no frames.
    * {{{
    * static Unsafe unsafe;  static volatile int written;
    * static { try { Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
    *   Unsafe.class.getMethod("storeFence"); unsafe = (Unsafe) f.get(null); } catch (Throwable t) {} }
    * public static void releaseFence() { Unsafe u = unsafe; if (u != null) u.storeFence(); else written = 0; }
    * }}} */
  private lazy val fenceClass: Array[Byte] = {
    import Opcodes.*
    val w = new ClassWriter(ClassWriter.COMPUTE_MAXS)
    w.visit(V1_6, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, fence, null, "java/lang/Object", null)
    w.visitField(ACC_PRIVATE | ACC_STATIC, "unsafe", s"L$unsafe;", null, null).visitEnd()
    w.visitField(ACC_PRIVATE | ACC_STATIC | ACC_VOLATILE, "written", "I", null, null).visitEnd()
    val init = w.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null)
    val (start, end, handler, done) = (new Label, new Label, new Label, new Label)
    init.visitCode()
    init.visitTryCatchBlock(start, end, handler, "java/lang/Throwable")
    init.visitLabel(start)
    init.visitLdcInsn(Type.getObjectType(unsafe))
    init.visitLdcInsn("theUnsafe")
    init.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false)
    init.visitInsn(DUP)
    init.visitInsn(ICONST_1)
    init.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false)
    init.visitLdcInsn(Type.getObjectType(unsafe))
    init.visitLdcInsn("storeFence")
    init.visitInsn(ICONST_0)
    init.visitTypeInsn(ANEWARRAY, "java/lang/Class")
    init.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false)
    init.visitInsn(POP)
    init.visitInsn(ACONST_NULL)
    init.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false)
    init.visitTypeInsn(CHECKCAST, unsafe)
    init.visitFieldInsn(PUTSTATIC, fence, "unsafe", s"L$unsafe;")
    init.visitLabel(end)
    init.visitJumpInsn(GOTO, done)
    init.visitLabel(handler)
    init.visitInsn(POP)
    init.visitLabel(done)
    init.visitInsn(RETURN)
    init.visitMaxs(0, 0)
    init.visitEnd()
    val release = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "releaseFence", "()V", null, null)
    val volatile = new Label
    release.visitCode()
    release.visitFieldInsn(GETSTATIC, fence, "unsafe", s"L$unsafe;")
    release.visitInsn(DUP)
    release.visitJumpInsn(IFNULL, volatile)
    release.visitMethodInsn(INVOKEVIRTUAL, unsafe, "storeFence", "()V", false)
    release.visitInsn(RETURN)
    release.visitLabel(volatile)
    release.visitInsn(POP)
    release.visitInsn(ICONST_0)
    release.visitFieldInsn(PUTSTATIC, fence, "written", "I")
    release.visitInsn(RETURN)
    release.visitMaxs(0, 0)
    release.visitEnd()
    w.visitEnd()
    w.toByteArray
  }

  // a jar's entries, or a class directory's files, by their path within it
  private def read(entry: File): Map[String, Array[Byte]] =
    if (entry.isDirectory) (entry ** "*").get().filter(_.isFile).map(f => IO.relativize(entry, f).get -> IO.readBytes(f)).toMap
    else if (!entry.getName.endsWith(".jar") || entry.getName == "android.jar") Map.empty
    else {
      val zip = new java.util.zip.ZipFile(entry)
      try zip.entries().asScala.filterNot(_.isDirectory).map(e => e.getName -> zip.getInputStream(e).readAllBytes()).toMap
      finally zip.close()
    }

  private def write(entries: Map[String, Array[Byte]], jar: File): Unit = {
    IO.createDirectory(jar.getParentFile)
    val out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(jar))
    try entries.toSeq.sortBy(_._1).foreach { case (name, bytes) =>
      out.putNextEntry(new java.util.zip.ZipEntry(name))
      out.write(bytes)
      out.closeEntry()
    }
    finally out.close()
  }

  private def contains(bytes: Array[Byte], text: String): Boolean = {
    val what = text.getBytes("UTF-8")
    var i = 0
    var found = false
    while (!found && i <= bytes.length - what.length) {
      var j = 0
      while (j < what.length && bytes(i + j) == what(j)) j += 1
      found = j == what.length
      i += 1
    }
    found
  }
}
