package everyui.sbt

import java.io.{File, FileOutputStream}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, StandardCopyOption}
import java.security.MessageDigest

/** Google's own compiler of ART profiles (profgen): the human-readable rules (`baseline-prof.txt`) matched against an
  * app's dex files and written as the binary profiles ART reads: version 0.1.0 (`baseline.prof`, as from Android 9)
  * with its metadata (`baseline.profm`, version 0.0.2), which androidx.profileinstaller turns into the form of the
  * Android it runs on (7 to 8.1 among them), and version 0.1.5, the form Android 12 and later take at install (the
  * Gradle plugin's dex metadata for them; ART reads it out of an APK on Android 14 and later,
  * `assets/art-profile/baseline.prof`). It is what the Android Gradle plugin compiles them with
  * (CompileArtProfileTask), and is published only inside that plugin's jar, so the jar is fetched once for the
  * machine with the Kotlin library it runs on, each checked against its SHA-256, and loaded apart from the build's
  * own classes. */
object Profgen {
  /** Android Gradle plugin 9.5.1, the latest stable, whose jar holds profgen (Apache 2.0), and the Kotlin standard
    * library it runs with (2.2.10 or later). */
  val jars: Seq[(String, String)] = Seq(
    "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.5.1/gradle-9.5.1.jar" ->
      "9f503b4fdb4e2234d183e56c25a96599af1d9df47f2171aa61cc59550df354ff",
    "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.21/kotlin-stdlib-2.4.21.jar" ->
      "bd8250210584cb659847dce9cda660f8e9906e5def7fbebcea93dc6dcb6a88a3")

  /** Where the jars are kept: one place for the machine (`EVERYUI_PROFGEN` names another). */
  val room: File = sys.env.get("EVERYUI_PROFGEN").filter(_.nonEmpty).map(new File(_))
    .getOrElse(new File(sys.props("user.home"), ".cache/everyui/profgen"))

  /** The jars, fetched into `room` unless they are there already. */
  def classpath(log: String => Unit): Seq[File] = synchronized {
    jars.map { case (url, sha) =>
      val jar = new File(room, url.substring(url.lastIndexOf('/') + 1))
      if (!jar.isFile || digest(jar) != sha) {
        Files.createDirectories(room.toPath)
        log(s"Profgen: fetching $url")
        val part = File.createTempFile(jar.getName, ".part", room)
        try {
          val response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
            .send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofFile(part.toPath))
          if (response.statusCode != 200) sys.error(s"Profgen: $url answered ${response.statusCode}")
          val got = digest(part)
          if (got != sha) sys.error(s"Profgen: $url has SHA-256 $got, not $sha")
          Files.move(part.toPath, jar.toPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally part.delete()
      }
      jar
    }
  }

  private var loaded: Option[(Seq[File], ClassLoader)] = None

  // one loader a process for the same jars: the plugin's classes are large, and loading them again for every build
  // costs a second or more
  private def loader(classpath: Seq[File]): ClassLoader = synchronized {
    loaded.filter(_._1 == classpath).map(_._2).getOrElse {
      val made = new java.net.URLClassLoader(classpath.map(_.toURI.toURL).toArray, ClassLoader.getPlatformClassLoader)
      loaded = Some(classpath -> made)
      made
    }
  }

  /** The rules in `rules` (named as in `dexes`, the shrinker's names) compiled against the dex files `dexes`, in the
    * order they are in the APK (`classes.dex`, `classes2.dex`, …), into `prof` (0.1.0), `profm` and `art` (0.1.5);
    * the problems profgen found in the rules, each a line it could not read. */
  def compile(classpath: Seq[File], rules: File, dexes: Seq[File], prof: File, profm: File, art: File): Seq[String] = {
    val cl = loader(classpath)
    def cls(name: String) = Class.forName("com.android.tools.profgen." + name, true, cl)
    val problems = scala.collection.mutable.ArrayBuffer.empty[String]
    val diagnostics = java.lang.reflect.Proxy.newProxyInstance(cl, Array(cls("Diagnostics")), (_, method, args) => {
      if (method.getName == "onError") problems += String.valueOf(args(0))
      null
    })
    val hrp = cls("HumanReadableProfileKt").getMethod("HumanReadableProfile", classOf[File], cls("Diagnostics"))
      .invoke(null, rules, diagnostics)
    if (hrp == null) sys.error(s"Profgen: $rules could not be read: ${problems.mkString("; ")}")
    val obfuscation = cls("ObfuscationMap").getField("Companion").get(null)
    val empty = obfuscation.getClass.getMethod("getEmpty").invoke(obfuscation)
    val parse = cls("DexFileParserKt").getMethod("parseDexFiles", classOf[Array[Byte]], classOf[Int])
    val parsed = new java.util.ArrayList[AnyRef]()
    dexes.foreach(d => parsed.addAll(parse.invoke(null, Files.readAllBytes(d.toPath), Int.box(parsed.size))
      .asInstanceOf[java.util.List[AnyRef]]))
    // the APK's name left to its default, as the Gradle plugin leaves it
    val profile = cls("ArtProfileKt").getMethod("ArtProfile$default", cls("HumanReadableProfile"), cls("ObfuscationMap"),
      classOf[java.util.List[?]], classOf[String], classOf[Int], classOf[Object])
      .invoke(null, hrp, empty, parsed, null, Int.box(8), null)
    val save = profile.getClass.getMethod("save", classOf[java.io.OutputStream], cls("ArtProfileSerializer"))
    Seq(prof -> "V0_1_0_P", profm -> "METADATA_0_0_2", art -> "V0_1_5_S").foreach { case (file, version) =>
      Files.createDirectories(file.getParentFile.toPath)
      val out = new FileOutputStream(file)
      try save.invoke(profile, out, cls("ArtProfileSerializer").getField(version).get(null)) finally out.close()
    }
    problems.toSeq
  }

  private def digest(f: File): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath)).map(b => f"${b & 0xff}%02x").mkString
}
