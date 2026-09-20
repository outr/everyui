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
        val room = into / s"$name.opening"
        IO.delete(room)
        IO.unzip(aar, room)
        val resources = room / "res"
        if (resources.isDirectory && resources.listFiles().nonEmpty)
          log(s"Aars: $name carries resources, which are not merged — whatever reads them must be shrunk away")
        val classes = room / "classes.jar"
        if (classes.isFile) IO.copyFile(classes, jar)
        val shipped = room / "proguard.txt"
        if (shipped.isFile) IO.copyFile(shipped, rules) else IO.delete(rules)
        IO.delete(room)
      }
      (jar, rules)
    }
    val classes = found.map(_._1).filter(_.isFile)
    val rules = found.map(_._2).filter(_.isFile)
    if (opened > 0) log(s"Aars: $opened opened of ${archives.size}; ${classes.size} with classes, ${rules.size} with rules")
    (classes, rules)
  }
}
