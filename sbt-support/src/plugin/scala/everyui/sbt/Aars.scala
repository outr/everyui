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
    * An app needs neither this nor any opening of its own: the plugin opens every archive among a project's
    * dependencies (`archives`, `everyuiAndroidLibraries`), puts their classes on its classpath and hands them, with
    * their rules and resources, to `AndroidApk.build`. Kept for builds written before it did. */
  def open(log: String => Unit, archives: Seq[File], into: File): (Seq[File], Seq[File]) = {
    IO.createDirectory(into)
    AarCache.clean(into)
    val opened = archives.count { aar =>
      val fresh = AarCache.open(aar, into)
      if (fresh && AarCache.resources(aar, into).isDirectory)
        log(s"Aars: ${AarCache.nameOf(aar)} carries resources (linked only where its classes read its R class: " +
          "AndroidLibraries)")
      fresh
    }
    val classes = archives.distinct.map(AarCache.classes(_, into)).filter(_.isFile)
    val rules = archives.distinct.map(AarCache.rules(_, into)).filter(_.isFile)
    if (opened > 0)
      log(s"Aars: $opened opened of ${archives.size}; ${classes.size} with classes, ${rules.size} with rules")
    (classes, rules)
  }

  /** Every Android archive among a project's resolved dependencies (`update`'s report: those the compiler sees,
    * `provided` among them), each once, opened under `into`: one a module, whichever ways it was reached, at the
    * version the resolution kept (AndroidArchive.chosen). What `everyuiAndroidLibraries` is, whose classes the plugin
    * puts on the classpath and whose classes, rules and resources `AndroidApk.build` takes (`libraries`). Opened once
    * for the machine, as `open` opens. */
  def archives(log: String => Unit, report: UpdateReport, into: File): Seq[AndroidArchive] = {
    val config = report.configuration(ConfigRef("compile-internal")).orElse(report.configuration(ConfigRef("compile")))
    val found = config.toSeq.flatMap(_.modules).filterNot(_.evicted).flatMap { m =>
      m.artifacts.collect { case (a, f) if a.extension == "aar" && f.isFile =>
        (m.module.organization, m.module.name, m.module.revision, f)
      }
    }
    val chosen = AndroidArchive.chosen(found)
    if (chosen.isEmpty) Nil
    else {
      IO.createDirectory(into)
      AarCache.clean(into)
      val opened = chosen.count { case (_, aar) => AarCache.open(aar, into) }
      val all = chosen.map { case (module, aar) => AndroidArchive.open(module, aar, into) }
      if (opened > 0) log(s"Aars: $opened opened of ${all.size} archives; ${all.count(_.rules.nonEmpty)} with rules, " +
        s"resources linked for ${all.flatMap(_.resources).map(r => s"${r.name} (${r.packageName})").mkString(", ")}")
      all
    }
  }
}
