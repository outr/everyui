import sbt.*
import sbt.io.IO

/** The Scala standard library an APK carries: Scala 3's, but for the classes that hold a VarHandle, which come from
  * Scala 2.13's instead.
  *
  * Scala 3's library holds its lazy vals in `java.lang.invoke.VarHandle`s, which Android has no signature-polymorphic
  * form of before API 33: on an Android 11 television the verifier throws out `scala.package$`, and with it the whole
  * app, before the first frame, while an Android 14 device runs it perfectly. 2.13's library is the same sources
  * compiled by Scala 2, whose lazy vals are a bitmap and a lock, and Scala 3's is binary compatible with it.
  *
  * NaboTV's build swaps the whole library for 2.13's (its `androidClasspath`). That loses what later Scala 3 libraries
  * added to classes 2.13 already had: under 3.10, `scala.Double$.compare`, which reactify calls, so the app died at its
  * first reaction. So only what has to go is swapped: each class (with its companion and inner classes, which call one
  * another) holding a VarHandle, taken from 2.13 where 2.13 has it. The app's own lazy vals are the legacy kind
  * (`-Ylegacy-lazy-vals`). */
object AndroidRuntime {
  val classicVersion = "2.13.18"

  /** `entries` with the Scala 3 library replaced as above. Fails rather than build an APK that cannot start below
    * Android 13. */
  def classpath(entries: Seq[File], resolution: librarymanagement.DependencyResolution, work: File, log: String => Unit): Seq[File] = {
    val module = librarymanagement.ModuleID("org.scala-lang", "scala-library", classicVersion)
    resolution.retrieve(resolution.wrapDependencyInModule(module), work / "scala213", sbt.util.Logger.Null) match {
      case Right(files) =>
        val classic = files.find(f => f.getName.startsWith("scala-library") && f.getName.endsWith(".jar"))
        // by the name's start: sbt's launcher calls it scala-library.jar, coursier scala-library-3.x.jar
        val three = entries.find(f => f.getName.startsWith("scala-library") && f.getName.endsWith(".jar"))
        (classic, three) match {
          case (Some(lib213), Some(lib3)) =>
            val out = work / "scala-library-android.jar"
            val (swapped, kept) = merge(lib3, lib213, out)
            log(s"AndroidApk: scala-library with ${swapped} classes from $classicVersion in place of Scala 3's VarHandle lazy vals" +
              (if (kept.isEmpty) "" else s"; ${kept.size} have no 2.13 form and keep theirs (${kept.take(4).mkString(", ")})"))
            entries.filterNot(_ == lib3) :+ out
          case _ => sys.error("AndroidApk: could not find scala-library (found " +
            classic.map(_.getName).getOrElse("no 2.13 jar") + ", " + three.map(_.getName).getOrElse("no Scala 3 library") +
            "); refusing to build an APK that cannot start below Android 13")
        }
      case Left(problem) => sys.error("AndroidApk: could not fetch scala-library " + classicVersion + ": " + problem)
    }
  }

  // the class a class file belongs with: itself, its companion and its inner classes
  private def group(name: String): String = {
    val slash = name.lastIndexOf('/')
    val dollar = name.indexOf('$', slash + 1)
    if (dollar < 0) name.stripSuffix(".class") else name.substring(0, dollar)
  }

  /** Scala 3's `lib3` written to `out`, each group holding a VarHandle taken from `lib213` where it has that group: how
    * many classes came from 2.13, and the classes with a VarHandle left as they were. */
  private def merge(lib3: File, lib213: File, out: File): (Int, List[String]) = {
    val three = read(lib3)
    val classic = read(lib213)
    val marker = "java/lang/invoke/VarHandle".getBytes("UTF-8")
    val holding = three.collect { case (name, bytes) if name.endsWith(".class") && contains(bytes, marker) => name }.toSet
    val groups = holding.map(group)
    val swap = groups.filter(g => classic.keys.exists(n => n.endsWith(".class") && group(n) == g))
    val kept = holding.filterNot(n => swap.contains(group(n))).toList.sorted
    val merged = three.filterNot { case (name, _) => name.endsWith(".class") && swap.contains(group(name)) } ++
      classic.filter { case (name, _) => name.endsWith(".class") && swap.contains(group(name)) }
    IO.createDirectory(out.getParentFile)
    val zout = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(out))
    try merged.foreach { case (name, bytes) =>
      zout.putNextEntry(new java.util.zip.ZipEntry(name))
      zout.write(bytes)
      zout.closeEntry()
    }
    finally zout.close()
    (classic.count { case (name, _) => name.endsWith(".class") && swap.contains(group(name)) }, kept)
  }

  private def read(jar: File): Map[String, Array[Byte]] = {
    val zip = new java.util.zip.ZipFile(jar)
    try {
      val out = Map.newBuilder[String, Array[Byte]]
      val entries = zip.entries()
      while (entries.hasMoreElements) {
        val e = entries.nextElement()
        if (!e.isDirectory) out += e.getName -> zip.getInputStream(e).readAllBytes()
      }
      out.result()
    }
    finally zip.close()
  }

  private def contains(bytes: Array[Byte], what: Array[Byte]): Boolean = {
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
