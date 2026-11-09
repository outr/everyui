package everyui.sbt

import sbt.*
import sbt.io.IO

/** Android archives, opened.
  *
  * An `.aar` is a zip: the classes are a jar inside it, with the manifest, any resources, and the shrinker rules the
  * library wants kept. Nothing in the toolchain here reads one — a compiler wants a jar on its classpath and R8 wants
  * jars to dex — so each is opened and its parts handed over separately.
  *
  * Opening is done once and repeats cheaply: asked twice for the same archive (the classes and the rules are
  * separate questions, and sbt may ask them at the same moment) the second answer comes from what the first left
  * behind, rather than the two of them tearing up the same directory. */
object Aars {
  /** Where opened archives are kept: one place for the machine, rather than one inside each build that wants them.
    *
    * They cannot live under a build's own target. sbt 2 shares its cache between builds, and a classpath assembled
    * out of these jars hashes to the same key wherever it is assembled, because the jars are byte for byte the same
    * in every build. What comes back out of that cache is the path the first build wrote, relative to ITS out
    * directory — so the second build is handed `${OUT}/jvm/…/other-android/aar/media3-common-1.9.2.jar`, resolves
    * it against its own out directory, finds nothing, and cannot see a library it declared perfectly correctly.
    * The symptom is `value media3 is not a member of androidx` in whichever build compiled second, and no amount of
    * cleaning that build fixes it. An absolute path outside every build means the same thing in all of them.
    *
    * Two builds opening at once is safe: each unpacks into a room of its own and the finished jar is moved into
    * place, so neither can be read half-written. */
  val room: File = file(sys.props("user.home")) / ".cache" / "everyui" / "aar"

  /** The classes jar of each archive, and the shrinker rules any of them ship, unpacked under `into`.
    *
    * Resources are not opened here: `resources` opens those of the archives whose classes read them, which
    * `AndroidApk.build` links (`libraries`). An archive that has resources is named in the log. */
  def open(log: String => Unit, archives: Seq[File], into: File): (Seq[File], Seq[File]) = {
    IO.createDirectory(into)
    var opened = 0
    val found = archives.map { aar =>
      val name = aar.getName.stripSuffix(".aar")
      val jar = into / s"$name.jar"
      val rules = into / s"$name.pro"
      if (!jar.isFile || jar.lastModified() < aar.lastModified()) {
        opened += 1
        val bench = into / s"$name.opening-${java.util.UUID.randomUUID().toString.take(8)}"
        IO.delete(bench)
        IO.unzip(aar, bench)
        val resources = bench / "res"
        if (resources.isDirectory && resources.listFiles().nonEmpty)
          log(s"Aars: $name carries resources (linked only where its classes read its R class: Aars.resources)")
        val classes = bench / "classes.jar"
        // moved rather than copied: a jar appears at its name whole, never as far as somebody else's read got
        if (classes.isFile) IO.move(classes, jar)
        val shipped = bench / "proguard.txt"
        if (shipped.isFile) IO.move(shipped, rules) else IO.delete(rules)
        IO.delete(bench)
      }
      (jar, rules)
    }
    val classes = found.map(_._1).filter(_.isFile)
    val rules = found.map(_._2).filter(_.isFile)
    if (opened > 0) log(s"Aars: $opened opened of ${archives.size}; ${classes.size} with classes, ${rules.size} with rules")
    (classes, rules)
  }

  /** The resources of each archive whose classes read them through their own R class (AndroidLibraries), opened under
    * `into`, for `AndroidApk.build` to link and write that R class for (`libraries`). Archives whose classes read no
    * R class of their own are left out, their resources with them. Opened once, and again as an archive changes. */
  def resources(log: String => Unit, archives: Seq[File], into: File): Seq[AarResources] = {
    IO.createDirectory(into)
    val found = archives.distinct.flatMap { aar =>
      val name = aar.getName.stripSuffix(".aar")
      AndroidLibraries.needsResources(aar).map { pkg =>
        val dir = into / s"$name.res"
        if (!(dir / "res").isDirectory || dir.lastModified() < aar.lastModified()) {
          val bench = into / s"$name.res-opening-${java.util.UUID.randomUUID().toString.take(8)}"
          IO.delete(bench)
          IO.unzip(aar, bench, (entry: String) => entry.startsWith("res/"))
          IO.delete(dir)
          IO.move(bench, dir)
        }
        AarResources(name, pkg, dir / "res")
      }
    }
    // one for each package: the first, where two versions of an archive are on the classpath
    val kept = found.groupBy(_.packageName).values.map(_.head).toSeq.sortBy(_.name)
    if (kept.nonEmpty)
      log(s"Aars: resources linked for ${kept.map(r => r.name + " (" + r.packageName + ")").mkString(", ")}")
    kept
  }
}
