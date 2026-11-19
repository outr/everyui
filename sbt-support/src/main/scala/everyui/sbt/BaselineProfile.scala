package everyui.sbt

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.zip.{CRC32, ZipEntry, ZipFile, ZipOutputStream}

/** An APK's baseline profile: the methods and classes its start and its busiest paths run, compiled ahead of time
  * where the APK is installed, so a new install does not start in the interpreter. Its rules are human-readable
  * (`baseline-prof.txt`, ART's own form: `HSPLeveryui/render/Host;->measure(…)V`, `Leveryui/render/Host;`), from
  * EveryUI (in the everyui-android jar, `everyui/android/baseline-prof.txt`), the app, and the Android archives it
  * depends on; `AndroidApk.build` merges them, has R8 rename them as it renames the classes, compiles them against
  * the dex (Profgen) and stores the result in the APK where ART and androidx.profileinstaller look for it. */
object BaselineProfile {
  /** Where an APK holds the profile and its metadata for androidx.profileinstaller, which writes them where ART reads
    * them on the first launch (where the install did not compile with one), as the Gradle plugin puts them:
    * uncompressed, since profileinstaller opens them as file descriptors (an asset compressed cannot be). */
  val prof: String = "assets/dexopt/baseline.prof"
  val profm: String = "assets/dexopt/baseline.profm"

  /** Where ART itself reads an APK's profile as it installs it, on Android 14 and later with a recent ART module
    * (artd's embedded profile, version 0.1.5; 35.08 reads it, 34.09 does not): `adb install` and a sideload compile
    * the app with it at once. Not in the Gradle plugin's APKs, whose profile reaches ART there through Play or a dex
    * metadata file. */
  val art: String = "assets/art-profile/baseline.prof"

  /** The entries an APK holds for the profile `prof`, its metadata `profm` and the profile `art`. */
  def entries(prof: File, profm: File, art: File): Seq[(String, File)] =
    Seq(BaselineProfile.prof -> prof, BaselineProfile.profm -> profm, BaselineProfile.art -> art)

  /** `file` written to `out` as the entry `name`, stored (zipalign then puts its bytes on a 4-byte boundary). */
  def store(out: ZipOutputStream, name: String, file: File): Unit = {
    val bytes = Files.readAllBytes(file.toPath)
    val crc = new CRC32()
    crc.update(bytes)
    val entry = new ZipEntry(name)
    entry.setMethod(ZipEntry.STORED)
    entry.setSize(bytes.length.toLong)
    entry.setCompressedSize(bytes.length.toLong)
    entry.setCrc(crc.getValue)
    out.putNextEntry(entry)
    out.write(bytes)
    out.closeEntry()
  }

  /** Where a jar holds the rules its classes want: everyui-android's are at `everyui/android/baseline-prof.txt`. */
  val inJar: String = "everyui/android/baseline-prof.txt"

  /** The rules among `program` (each jar or class directory's `inJar`), copied out under `into`, in order. */
  def onClasspath(program: Seq[File], into: File): Seq[File] = program.zipWithIndex.flatMap { case (f, i) =>
    val out = new File(into, s"$i-${f.getName}.txt")
    if (f.isDirectory) Some(new File(f, inJar)).filter(_.isFile)
    else if (f.isFile && f.getName.endsWith(".jar")) {
      val zip = new ZipFile(f)
      try Option(zip.getEntry(inJar)).map { e =>
        Files.createDirectories(into.toPath)
        Files.write(out.toPath, zip.getInputStream(e).readAllBytes())
        out
      } finally zip.close()
    }
    else None
  }

  /** The rules of `sources` that exist, as one file `out`: each rule once, in the order first given, comments and blank
    * lines left out; the number of rules. */
  def merge(sources: Seq[File], out: File): Int = {
    val rules = sources.filter(_.isFile).flatMap(f => new String(Files.readAllBytes(f.toPath), UTF_8).linesIterator)
      .map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#")).distinct
    Files.createDirectories(out.getParentFile.toPath)
    Files.write(out.toPath, rules.mkString("", "\n", "\n").getBytes(UTF_8))
    rules.size
  }

  /** What is wrong with the profile in `apk`, or None: each entry there, stored rather than compressed, its data on a
    * 4-byte boundary (as zipalign leaves it), and each profile of the version its reader takes. */
  def problem(apk: File): Option[String] = {
    val heads = Map(prof -> "pro\u0000010\u0000", profm -> "prm\u0000002\u0000", art -> "pro\u0000015\u0000")
    val names = Seq(prof, profm, art)
    val zip = new ZipFile(apk)
    val found = try names.map(name => name -> Option(zip.getEntry(name)).map(e =>
      (e.getMethod, new String(zip.getInputStream(e).readNBytes(8), UTF_8)))) finally zip.close()
    lazy val offsets = dataOffsets(Files.readAllBytes(apk.toPath))
    found.collectFirst { case (name, None) => s"$apk has no $name" }
      .orElse(found.collectFirst { case (name, Some((method, _))) if method != ZipEntry.STORED => s"$name is compressed in $apk" })
      .orElse(found.collectFirst { case (name, Some((_, head))) if head != heads(name) =>
        s"$name in $apk is not of version ${heads(name).substring(4, 7)}" })
      .orElse(names.find(offsets(_) % 4 != 0).map(name => s"$name is not on a 4-byte boundary in $apk"))
  }

  // where each entry's bytes start in a zip: its local header (from the central directory), past its name and extra field
  private def dataOffsets(bytes: Array[Byte]): Map[String, Long] = {
    val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    def u16(at: Int) = b.getShort(at) & 0xffff
    val end = (bytes.length - 22 to 0 by -1).find(i => b.getInt(i) == 0x06054b50).getOrElse(sys.error("not a zip"))
    var at = b.getInt(end + 16)
    (0 until u16(end + 10)).map { _ =>
      val name = new String(bytes, at + 46, u16(at + 28), UTF_8)
      val local = b.getInt(at + 42)
      at += 46 + u16(at + 28) + u16(at + 30) + u16(at + 32)
      name -> (local + 30L + u16(local + 26) + u16(local + 28))
    }.toMap
  }

  /** An APK's dex files in the order ART numbers them: `classes.dex`, `classes2.dex`, … */
  def dexOrder(dexes: Seq[File]): Seq[File] =
    dexes.sortBy(f => f.getName.stripPrefix("classes").stripSuffix(".dex").toIntOption.getOrElse(1))
}
