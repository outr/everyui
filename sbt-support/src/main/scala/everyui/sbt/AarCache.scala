package everyui.sbt

import java.io.{File, FileInputStream, IOException}
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*

/** Android archives opened into a cache the whole machine shares (Aars.room), which any number of builds read and
  * fill at the same moment, in this JVM and in others.
  *
  * Each archive has an entry of its own, a directory named for the archive and its content
  * (`media3-exoplayer-1.9.2-<sha-256, 16 hex digits>`), holding its classes jar (`<name>.jar`), its shrinker rules
  * (`<name>.pro`) where it ships any, and its resources (`res`) where it has any. An entry is opened into a directory
  * no other build knows of (its name, the process and a nonce) and renamed into place whole, so it appears complete
  * or not at all; once there it is never written, deleted or replaced, so a build that has its jar on a classpath
  * keeps it. Two builds opening the same archive at once each open their own and the second rename fails (a
  * directory is never renamed over one that has files in it): that build drops its copy and reads the first's, which
  * holds the same bytes. A different archive, or the same name republished with other bytes, has another digest and
  * so an entry of its own. A copy left behind by a build that died part way is removed once it is an hour old, never
  * sooner, so a build still opening one is left to finish. */
object AarCache {
  /** How old a half-opened copy is before it is taken for one a build left behind. */
  val staleAfterMillis: Long = 60L * 60 * 1000

  private val digests = new ConcurrentHashMap[(String, Long, Long), String]()

  /** The archive's name, as its entry and the files in it are named: `media3-exoplayer-1.9.2`. */
  def nameOf(aar: File): String = aar.getName.stripSuffix(".aar")

  /** The entry the archive `aar` has under `room`, opened or not. */
  def entry(aar: File, room: File): File = new File(room, s"${nameOf(aar)}-${digest(aar)}")

  /** The archive's classes jar, its shrinker rules and its resources directory in its entry, each there or not. */
  def classes(aar: File, room: File): File = new File(entry(aar, room), nameOf(aar) + ".jar")
  def rules(aar: File, room: File): File = new File(entry(aar, room), nameOf(aar) + ".pro")
  def resources(aar: File, room: File): File = new File(entry(aar, room), "res")

  /** Opens `aar` into its entry under `room` unless it is there already; whether this call opened it. */
  def open(aar: File, room: File): Boolean = {
    val target = entry(aar, room)
    if (target.isDirectory) false
    else {
      Files.createDirectories(room.toPath)
      val nonce = java.lang.Long.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextLong())
      val bench = new File(room, s".${target.getName}.tmp-${ProcessHandle.current().pid()}-$nonce")
      try {
        unpack(aar, bench, nameOf(aar))
        try {
          Files.move(bench.toPath, target.toPath, StandardCopyOption.ATOMIC_MOVE)
          true
        }
        catch {
          // another build put the same entry in place first: its files are these, so they are read instead
          case _: IOException if target.isDirectory => false
        }
      }
      finally delete(bench.toPath)
    }
  }

  /** Removes what builds that died part way left under `room`: half-opened copies an hour old or more (and the
    * `.opening-` directories earlier releases opened in), never a fresh one, which a build may still be filling. */
  def clean(room: File): Unit = {
    val now = System.currentTimeMillis()
    Option(room.listFiles()).getOrElse(Array.empty[File]).foreach { f =>
      val name = f.getName
      val leftover = (name.startsWith(".") && name.contains(".tmp-")) || name.contains("opening-")
      if (leftover && now - newest(f.toPath) > staleAfterMillis) delete(f.toPath)
    }
  }

  /** The SHA-256 of the archive's bytes, as 16 hex digits; read once a process while it is unchanged. */
  def digest(aar: File): String = {
    val key = (aar.getAbsolutePath, aar.length(), aar.lastModified())
    digests.computeIfAbsent(key, _ => {
      val sha = MessageDigest.getInstance("SHA-256")
      val in = new FileInputStream(aar)
      try {
        val buffer = new Array[Byte](65536)
        var n = in.read(buffer)
        while (n >= 0) {
          sha.update(buffer, 0, n)
          n = in.read(buffer)
        }
      }
      finally in.close()
      sha.digest().take(8).map(b => f"${b & 0xff}%02x").mkString
    })
  }

  // the classes jar, the rules and the resources, under `bench`, which is created here
  private def unpack(aar: File, bench: File, name: String): Unit = {
    Files.createDirectories(bench.toPath)
    val root = bench.toPath.toAbsolutePath.normalize
    val zip = new ZipFile(aar)
    try {
      zip.entries().asScala.filterNot(_.isDirectory).foreach { e =>
        val to = e.getName match {
          case "classes.jar" => Some(root.resolve(name + ".jar"))
          case "proguard.txt" => Some(root.resolve(name + ".pro"))
          case n if n.startsWith("res/") => Some(root.resolve(n).normalize).filter(_.startsWith(root.resolve("res")))
          case _ => None
        }
        to.foreach { path =>
          Files.createDirectories(path.getParent)
          val in = zip.getInputStream(e)
          try Files.copy(in, path) finally in.close()
        }
      }
    }
    finally zip.close()
  }

  // the latest time anything under `path` was written; now where it cannot be read (another build removing it)
  private def newest(path: Path): Long = {
    try {
      val all = Files.walk(path)
      try all.iterator().asScala.map(p => p.toFile.lastModified()).foldLeft(0L)(math.max) finally all.close()
    }
    catch { case _: IOException | _: java.io.UncheckedIOException => System.currentTimeMillis() }
  }

  private def delete(path: Path): Unit = if (Files.exists(path)) {
    try {
      val all = Files.walk(path)
      try all.iterator().asScala.toList.reverse.foreach(p => Files.deleteIfExists(p)) finally all.close()
    }
    catch { case _: IOException | _: java.io.UncheckedIOException => () }
  }
}
