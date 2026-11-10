package everyui.sbt

import sbt.*
import sbt.io.IO

/** Android archives, opened.
  *
  * An `.aar` is a zip: the classes are a jar inside it, with the manifest, any resources, and the shrinker rules the
  * library wants kept. Nothing in the toolchain here reads one — a compiler wants a jar on its classpath and R8 wants
  * jars to dex — so each is opened and its parts handed over separately.
  *
  * Each archive is opened once for the machine (AarCache): an entry already there is read as it is, never opened
  * again, so asking twice (the classes and the rules are separate questions, and sbt may ask them at the same moment,
  * as may another build) costs no more than finding it. */
object Aars {
  /** Where opened archives are kept: one place for the machine, rather than one inside each build that wants them
    * (`EVERYUI_AAR_CACHE` names another, a test's, say).
    *
    * They cannot live under a build's own target. sbt 2 shares its cache between builds, and a classpath assembled
    * out of these jars hashes to the same key wherever it is assembled, because the jars are byte for byte the same
    * in every build. What comes back out of that cache is the path the first build wrote, relative to ITS out
    * directory — so the second build is handed `${OUT}/jvm/…/other-android/aar/media3-common-1.9.2.jar`, resolves
    * it against its own out directory, finds nothing, and cannot see a library it declared perfectly correctly.
    * The symptom is `value media3 is not a member of androidx` in whichever build compiled second, and no amount of
    * cleaning that build fixes it. An absolute path outside every build means the same thing in all of them.
    *
    * Builds opening at once are safe (AarCache): an archive's entry appears whole by a rename, and is never written
    * again, so no build can find a jar it has on its classpath gone or half-written. */
  val room: File = sys.env.get("EVERYUI_AAR_CACHE").filter(_.nonEmpty).map(file)
    .getOrElse(file(sys.props("user.home")) / ".cache" / "everyui" / "aar")

  /** The classes jar of each archive, and the shrinker rules any of them ship, opened under `into`.
    *
    * Resources are opened with them but not returned here: `resources` returns those of the archives whose classes
    * read them, which `AndroidApk.build` links (`libraries`). An archive that has resources is named in the log as it
    * is opened. */
  def open(log: String => Unit, archives: Seq[File], into: File): (Seq[File], Seq[File]) = {
    IO.createDirectory(into)
    AarCache.clean(into)
    val opened = archives.count { aar =>
      val fresh = AarCache.open(aar, into)
      if (fresh && AarCache.resources(aar, into).isDirectory)
        log(s"Aars: ${AarCache.nameOf(aar)} carries resources (linked only where its classes read its R class: " +
          "Aars.resources)")
      fresh
    }
    val classes = archives.map(AarCache.classes(_, into)).filter(_.isFile)
    val rules = archives.map(AarCache.rules(_, into)).filter(_.isFile)
    if (opened > 0)
      log(s"Aars: $opened opened of ${archives.size}; ${classes.size} with classes, ${rules.size} with rules")
    (classes, rules)
  }

  /** The resources of each archive whose classes read them through their own R class (AndroidLibraries), opened under
    * `into`, for `AndroidApk.build` to link and write that R class for (`libraries`). Archives whose classes read no
    * R class of their own are left out, their resources with them. Opened once for the machine, as `open` opens. */
  def resources(log: String => Unit, archives: Seq[File], into: File): Seq[AarResources] = {
    IO.createDirectory(into)
    AarCache.clean(into)
    val found = archives.distinct.flatMap { aar =>
      AndroidLibraries.needsResources(aar).map { pkg =>
        AarCache.open(aar, into)
        AarResources(AarCache.nameOf(aar), pkg, AarCache.resources(aar, into))
      }
    }
    // one for each package: the first, where two versions of an archive are on the classpath
    val kept = found.groupBy(_.packageName).values.map(_.head).toSeq.sortBy(_.name)
    if (kept.nonEmpty)
      log(s"Aars: resources linked for ${kept.map(r => r.name + " (" + r.packageName + ")").mkString(", ")}")
    kept
  }
}
