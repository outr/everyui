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
    * Resources are NOT merged, and an archive that has any is named in the log rather than passed over in silence.
    * What that costs is whatever code reads them: the classes are still there, and asking one of them for a string
    * it cannot find is a crash at the moment of asking. It is only safe while the resources belong to a corner of
    * the library nothing here enters — Media3's are the nine strings its download-notification service shows, and
    * the shrinker removes that service along with them. Anything else wants `aapt2` adding here first. */
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
          log(s"Aars: $name carries resources, which are not merged — whatever reads them must be shrunk away")
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
}
