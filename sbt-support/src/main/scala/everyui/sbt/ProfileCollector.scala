package everyui.sbt

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import scala.jdk.CollectionConverters.*

/** Baseline profile rules collected from an app run on a device or an emulator (Android 14 or later, whose ART dumps
  * the profile it gathered as rules: `pm dump-profiles --dump-classes-and-methods`), as Macrobenchmark's
  * BaselineProfileRule collects them: the APK installed afresh without the profile it carries (so the rules are what
  * this run ran, not what the last collection gave it), started cold once for each launch given (its extras,
  * `--es demo chat`) and left to run, ART told to save what it ran (SIGUSR1, where adb can be root, as on an
  * emulator's Google APIs image; otherwise a minute is waited for ART to save it of its own accord), then the methods
  * and classes dumped and given their source names again through the APK's own R8 mapping (ProfileRetrace), which
  * `AndroidApk.build` keeps beside it (`mappings/<map id>-v<versionCode>.txt.gz`). */
object ProfileCollector {
  /** The rules the APK `apk` ran, retraced and sorted, those `keep` keeps (by class, `everyui/render/Host`).
    * `adb` is platform-tools' adb, `buildTools` a build-tools directory (aapt2 reads the package and its activity out
    * of the APK, zipalign and apksigner make the copy without a profile, signed with the SDK's debug key),
    * `serial` the device (None: the only one), `seconds` how long each launch runs before the next. */
  def collect(log: String => Unit, adb: File, buildTools: File, serial: Option[String], apk: File,
              launches: Seq[Seq[String]], seconds: Int, keep: String => Boolean = _ => true): Seq[String] = {
    def run(args: String*): String = {
      val out = new StringBuilder
      val cmd = Seq(adb.toString) ++ serial.toSeq.flatMap(s => Seq("-s", s)) ++ args
      val rc = sys.process.Process(cmd).!(sys.process.ProcessLogger(l => out.append(l).append('\n'), l => out.append(l).append('\n')))
      if (rc != 0) sys.error(s"ProfileCollector: ${args.mkString(" ")} exited $rc: ${out.toString.trim}")
      out.toString
    }
    val badging = sys.process.Process(Seq((new File(buildTools, "aapt2")).toString, "dump", "badging", apk.toString)).!!
    val pkg = """package: name='([^']+)'""".r.findFirstMatchIn(badging).map(_.group(1))
      .getOrElse(sys.error(s"ProfileCollector: no package in $apk"))
    val activity = """launchable-activity: name='([^']+)'""".r.findFirstMatchIn(badging).map(_.group(1))
      .getOrElse(sys.error(s"ProfileCollector: no launcher activity in $apk"))
    val sdk = run("shell", "getprop", "ro.build.version.sdk").trim.toIntOption.getOrElse(0)
    if (sdk < 34) sys.error(s"ProfileCollector: the device runs API $sdk; collecting needs Android 14 (API 34) or later")
    val retrace = ProfileRetrace(mapping(apk))
    // root, where adbd may be (an emulator's Google APIs image), to tell ART to save what the app ran
    val root = scala.util.Try(run("root")).toOption.exists(!_.contains("cannot")) && {
      run("wait-for-device")
      run("shell", "id", "-u").trim == "0"
    }

    val bare = withoutProfile(apk, buildTools, new File(apk.getParentFile, "profile-collect.apk"))
    log(s"ProfileCollector: installing $apk without its profile on ${serial.getOrElse("the device")} (API $sdk)")
    scala.util.Try(run("uninstall", pkg))
    run("install", "--no-incremental", bare.toString)
    run("shell", "pm", "art", "clear-app-profiles", pkg)
    launches.foreach { extras =>
      log(s"ProfileCollector: $pkg started cold${if (extras.isEmpty) "" else " with " + extras.mkString(" ")}, for $seconds s")
      run(Seq("shell", "am", "start", "-W", "-S", "-n", s"$pkg/$activity") ++ extras*)
      Thread.sleep(seconds * 1000L)
      if (root) {
        run("shell", "kill", "-s", "SIGUSR1", run("shell", "pidof", pkg).trim)
        Thread.sleep(3000L)
      }
      else {
        log("ProfileCollector: adb cannot be root here, so ART is left a minute to save what the app ran")
        Thread.sleep(60000L)
      }
    }
    run("shell", "pm", "dump-profiles", "--dump-classes-and-methods", pkg)
    val dumped = run("shell", "cat", s"/data/misc/profman/$pkg-primary.prof.txt").linesIterator.toSeq
    run("shell", "am", "force-stop", pkg)
    bare.delete()
    val rules = retrace.all(dumped).filter(r => keep(owner(r))).sorted
    log(s"ProfileCollector: ${dumped.size} rules dumped, ${rules.size} kept")
    rules
  }

  /** `apk` without its profiles (BaselineProfile's entries), aligned and signed with the SDK's debug key, as `out`. */
  def withoutProfile(apk: File, buildTools: File, out: File): File = {
    val unsigned = new File(out.getPath + ".unsigned")
    val aligned = new File(out.getPath + ".aligned")
    val in = new ZipFile(apk)
    val zip = new ZipOutputStream(new FileOutputStream(unsigned))
    val profiles = Set(BaselineProfile.prof, BaselineProfile.profm, BaselineProfile.art)
    try in.entries().asScala.filterNot(e => profiles(e.getName) || e.getName.startsWith("META-INF/")).foreach { e =>
      val copy = new ZipEntry(e.getName)
      copy.setMethod(e.getMethod)
      if (e.getMethod == ZipEntry.STORED) {
        copy.setSize(e.getSize)
        copy.setCompressedSize(e.getSize)
        copy.setCrc(e.getCrc)
      }
      zip.putNextEntry(copy)
      zip.write(in.getInputStream(e).readAllBytes())
      zip.closeEntry()
    } finally {
      in.close()
      zip.close()
    }
    def exec(cmd: String*): Unit = if (sys.process.Process(cmd).! != 0) sys.error(s"ProfileCollector: ${cmd.head} failed")
    exec(new File(buildTools, "zipalign").toString, "-f", "-p", "4", unsigned.toString, aligned.toString)
    exec(new File(buildTools, "apksigner").toString, "sign", "--ks", sys.props("user.home") + "/.android/debug.keystore",
      "--ks-pass", "pass:android", "--key-pass", "pass:android", "--ks-key-alias", "androiddebugkey",
      "--out", out.toString, aligned.toString)
    unsigned.delete()
    aligned.delete()
    new File(out.getPath + ".idsig").delete()
    out
  }

  /** The class a rule names, as `everyui/render/Host`. */
  def owner(rule: String): String = rule.dropWhile(c => c == 'H' || c == 'S' || c == 'P').drop(1).takeWhile(_ != ';')

  /** The mapping R8 wrote for `apk`: the one whose map id its dex carries, kept beside it (`AndroidApk.keepMapping`). */
  def mapping(apk: File): File = {
    val zip = new ZipFile(apk)
    val id = try {
      val dex = new String(zip.getInputStream(zip.getEntry("classes.dex")).readAllBytes(), UTF_8)
      """"pg-map-id":"([0-9a-f]+)"""".r.findFirstMatchIn(dex).map(_.group(1))
        .getOrElse(sys.error(s"ProfileCollector: $apk names no R8 mapping"))
    } finally zip.close()
    Option(new File(apk.getParentFile, "mappings").listFiles()).getOrElse(Array.empty[File])
      .find(_.getName.startsWith(id + "-")).getOrElse(sys.error(
        s"ProfileCollector: no mapping $id in ${new File(apk.getParentFile, "mappings")}, which AndroidApk.build keeps"))
  }

  /** The rules written to `out` under a note of where they came from. */
  def write(rules: Seq[String], out: File, note: String): File = {
    Files.createDirectories(out.getAbsoluteFile.getParentFile.toPath)
    Files.write(out.toPath, (s"# $note\n" + rules.mkString("", "\n", "\n")).getBytes(UTF_8))
    out
  }
}
