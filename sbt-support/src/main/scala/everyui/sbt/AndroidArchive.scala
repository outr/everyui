package everyui.sbt

import java.io.{File, FileInputStream}
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*

/** An Android archive (.aar) among an app's dependencies, opened for its build (AarCache): the module it is
  * (`androidx.media3:media3-common`), its name (`media3-common-1.9.2`), its classes jar, the shrinker rules it ships,
  * and its resources where its classes read them through their own R class (AndroidLibraries), which
  * `AndroidApk.build` links and writes that R class for. Each is `AndroidApk.build`'s to dex, apply and link
  * (`libraries`); the plugin's `everyuiAndroidLibraries` is every archive a project depends on, each once. */
case class AndroidArchive(module: String, name: String, classes: Option[File], rules: Option[File],
                          resources: Option[AarResources])

object AndroidArchive {
  private val needs = new ConcurrentHashMap[(String, Long, Long), Option[String]]()

  /** The archive to take of each module among `found` (its group, name, version and file): one a module, however many
    * ways it was reached, the latest version where more than one is left (the one a resolution keeps), in the order
    * of the modules' names. */
  def chosen(found: Seq[(String, String, String, File)]): Seq[(String, File)] =
    found.groupBy { case (group, name, _, _) => s"$group:$name" }.toSeq.sortBy(_._1).map { case (module, all) =>
      module -> all.reduce((a, b) => if (newer(b._3, a._3)) b else a)._4
    }

  /** Whether version `a` is later than `b`: their numbers compared one by one (1.10.0 after 1.9.2), then the rest as
    * text, a release after its own pre-release (1.0.0 after 1.0.0-rc1). */
  def newer(a: String, b: String): Boolean = {
    def parts(v: String) = v.split("[.-]").toSeq
    val (pa, pb) = (parts(a), parts(b))
    val first = pa.zipAll(pb, "", "").find(p => p._1 != p._2)
    first.exists {
      case (x, y) if x.nonEmpty && y.nonEmpty && x.forall(_.isDigit) && y.forall(_.isDigit) => BigInt(x) > BigInt(y)
      case ("", y) => !y.forall(_.isDigit)   // 1.0.0 against 1.0.0-rc1
      case (x, "") => x.forall(_.isDigit)    // 1.0.0.1 against 1.0.0
      case (x, y) if x.forall(_.isDigit) != y.forall(_.isDigit) => x.forall(_.isDigit)
      case (x, y) => x > y
    }
  }

  /** The archive `aar` of `module`, opened under `room` (AarCache) unless it is there already. Whether its classes
    * read their resources is read once a process while the file is unchanged. */
  def open(module: String, aar: File, room: File): AndroidArchive = {
    AarCache.open(aar, room)
    val key = (aar.getAbsolutePath, aar.length(), aar.lastModified())
    val pkg = needs.computeIfAbsent(key, _ => AndroidLibraries.needsResources(aar))
    AndroidArchive(module, AarCache.nameOf(aar), Some(AarCache.classes(aar, room)).filter(_.isFile),
      Some(AarCache.rules(aar, room)).filter(_.isFile),
      pkg.map(p => AarResources(AarCache.nameOf(aar), p, AarCache.resources(aar, room))))
  }

  /** What R8 dexes: the jars and directories of `program`, a jar of the same bytes as one before it left out, then
    * each archive's classes jar that the program does not hold already (the plugin puts every one on the classpath
    * the program is taken from, and an app may add its own copy). An archive whose classes are all among the program
    * or an earlier archive, byte for byte, is held already and left out; one where only some are, or some differ (a
    * copy of another version, say), stops the build naming the archive and the jar that holds them, where R8 would
    * stop on the first class "defined multiple times" and name neither. */
  def dexed(program: Seq[File], archives: Seq[AndroidArchive]): Seq[File] = {
    val jars = program.filter(f => f.isFile && f.getName.endsWith(".jar"))
    val seen = scala.collection.mutable.Set.empty[String]
    val kept = program.filter(f => !jars.contains(f) || seen.add(digest(f)))
    // each class held so far: where it is, and its CRC
    val held = scala.collection.mutable.Map.empty[String, (String, Long)]
    kept.filter(jars.contains).foreach { j =>
      classes(j).foreach { case (c, crc) => held.getOrElseUpdate(c, (j.getName, crc)) }
    }
    val added = archives.flatMap(a => a.classes.map(a -> _)).flatMap { case (archive, jar) =>
      val own = classes(jar)
      val shared = own.filter { case (c, _) => held.contains(c) }
      if (shared.isEmpty) {
        own.foreach { case (c, crc) => held(c) = (s"${archive.module} (${archive.name}.aar)", crc) }
        Some(jar)
      }
      else if (shared.size == own.size && shared.forall { case (c, crc) => held(c)._2 == crc }) None
      else {
        val holders = shared.keys.map(c => held(c)._1).toSeq.distinct.sorted
        throw new IllegalStateException(s"AndroidApk: ${holders.mkString(", ")} ${if (holders.size == 1) "holds" else
          "hold"} ${shared.size} of the ${own.size} classes of the Android archive ${archive.module} " +
          s"(${archive.name}.aar)${if (shared.size == own.size) ", not byte for byte" else ""}. sbt-everyui opens " +
          "every archive among the dependencies itself (everyuiAndroidLibraries) and puts its classes on the " +
          "classpath: leave out any copy of an archive's classes the build opens or adds of its own (an archive " +
          "opened by hand, or a jar of another version), and name the version wanted in libraryDependencies")
      }
    }
    kept ++ added
  }

  /** The rules R8 is given, the build's `own` and then the archives': each file once, a file of the same text as one
    * before it left out. */
  def rules(own: Seq[File], archives: Seq[AndroidArchive]): Seq[File] = {
    val seen = scala.collection.mutable.Set.empty[String]
    (own ++ archives.flatMap(_.rules)).filter(f => seen.add(digest(f)))
  }

  // the classes in a jar (not a later Java's, under META-INF/versions), with the CRC its zip records for each
  private def classes(jar: File): Map[String, Long] = {
    val zip = new ZipFile(jar)
    try zip.entries().asScala.filter(e => e.getName.endsWith(".class") && !e.getName.startsWith("META-INF/"))
      .map(e => e.getName -> e.getCrc).toMap
    finally zip.close()
  }

  private def digest(f: File): String = {
    val sha = MessageDigest.getInstance("SHA-256")
    val in = new FileInputStream(f)
    try {
      val buffer = new Array[Byte](65536)
      var n = in.read(buffer)
      while (n >= 0) {
        sha.update(buffer, 0, n)
        n = in.read(buffer)
      }
    }
    finally in.close()
    sha.digest().map(b => f"${b & 0xff}%02x").mkString
  }
}
