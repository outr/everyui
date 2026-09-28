import sbt.*

/** An Android app as a signed APK, from a Scala program's classpath: R8 over the program (shrinking from the
  * classes the rules keep, then dexing), aapt2 over the resources and manifest, the dex added, aligned and signed with
  * the given key (`Signing.debug` for iteration, a release key to ship). No Gradle and no Android plugin: the SDK's
  * own tools.
  *
  * With `bundletool`, an app bundle (`.aab`) instead: the same dex, resources and manifest, linked in the protocol
  * buffer form a bundle wants and signed as a jar, for a store that builds the per-device APKs itself (Play).
  *
  * EveryUI's `android/packageApk` calls it, and so does an app's build that includes this file in its meta-build. */
object AndroidApk {
  /** A keystore to sign with, and the words to open it. `debug` is the SDK's own throwaway key — fine to test with,
    * but a store update must be signed with the same release key every time, so a shipping build passes its own. */
  final case class Signing(keystore: File, storePass: String, keyPass: String, alias: String)
  def debug: Signing = Signing(file(sys.props("user.home")) / ".android" / "debug.keystore", "android", "android", "androiddebugkey")

  /** Core library desugaring: Java library classes newer than the device (`java.time`, streams, `Optional` — Android 7
    * and 8 additions) rewritten onto Google's back-port, which is dexed into the APK beside the program. `config` is
    * the `desugar.json` from `desugar_jdk_libs_configuration`, `library` the `desugar_jdk_libs` jar of the same version. */
  final case class Desugar(config: File, library: File)

  /** A copy of R8's mapping kept for good, gzipped, under `mappings/<map id>-v<versionCode>.txt.gz`: a crash from the
    * field names its build only by the map id in its frames (`r8-map-id-...`), and the build's own mapping.txt is
    * overwritten by the next, which left every report from an older build unreadable. Retrace with the file whose
    * name starts with the id the report carries. */
  def keepMapping(mapping: File, into: File, versionCode: Int, log: String => Unit): Unit = if (mapping.exists) {
    // the id is in the header: read no further than it
    val source = scala.io.Source.fromFile(mapping, "UTF-8")
    val id = try source.getLines().take(20).find(_.startsWith("# pg_map_id:")).map(_.stripPrefix("# pg_map_id:").trim)
      .getOrElse("unknown") finally source.close()
    IO.createDirectory(into)
    val kept = into / s"$id-v$versionCode.txt.gz"
    IO.gzip(mapping, kept)
    log(s"AndroidApk: mapping kept as ${kept.getName} (${kept.length / 1048576} MB)")
  }

  def build(log: String => Unit, sdk: File, platform: String, buildTools: String, minSdk: String, targetSdk: String,
            program: Seq[File], manifest: File, resources: File, assets: File, rules: Seq[File], work: File, apk: File,
            versionName: String, versionCode: Int, signing: Signing, desugar: Option[Desugar] = None,
            bundletool: Option[Seq[File]] = None): File = {
    val tools = sdk / "build-tools" / buildTools
    val androidJar = sdk / "platforms" / platform / "android.jar"
    IO.delete(work)
    IO.createDirectory(work)
    def exec(cmd: Seq[String], env: (String, String)*): Unit = {
      val rc = sys.process.Process(cmd, None, env*).!
      if (rc != 0) sys.error(s"AndroidApk: ${cmd.head} exited $rc")
    }

    // d8 and R8 read jars, so class directories are zipped first
    val inputs = program.filterNot(_ == androidJar).zipWithIndex.map { case (f, i) =>
      if (f.isDirectory) {
        val jar = work / "classes" / s"$i.jar"
        IO.zip(Path.allSubpaths(f).filterNot(_._1.isDirectory), jar, None)
        jar
      } else f
    }
    val dex = work / "dex"
    IO.createDirectory(dex)
    // what the rules matched, and what R8 removed — the record when a shrunk build misbehaves
    val diagnostics = work / "diagnostics.pro"
    IO.write(diagnostics, s"-printseeds ${work / "seeds.txt"}\n-printusage ${work / "usage.txt"}\n")
    // R8, not D8: the rules keep what reflection reaches, and the shrinking is what keeps the dex under the
    // method limit without a second file.
    //
    // A note for whoever meets an APK that runs on one television and not another: R8 is not usually the
    // culprit. On 2026-09-20 a build that ran perfectly on a Google TV Streamer (API 34) died on an NVIDIA
    // SHIELD (API 30) before its first frame, and R8 was blamed for an afternoon. The real cause was the
    // Scala 3 standard library, whose lazy vals are held in a java.lang.invoke.VarHandle: that method is
    // signature-polymorphic, Android has no such thing before API 33, and the verifier throws out every class
    // holding one. The app's build swaps the 2.13 library in for dexing; see `androidClasspath` there.
    log(s"AndroidApk: R8 over ${inputs.size} inputs")
    // with desugaring, R8 rewrites the program's newer library calls onto the back-port's names, and L8 below dexes
    // the back-port itself. (The command line has no way to hand L8 the list of back-port classes the program
    // reaches, so the back-port goes in whole rather than shrunk to them.)
    val desugarArgs = desugar.toList.flatMap(d => Seq("--desugared-lib", d.config.toString))
    exec(Seq("java", "-cp", (tools / "lib" / "d8.jar").toString, "com.android.tools.r8.R8",
      "--release", "--min-api", minSdk, "--lib", androidJar.toString) ++ desugarArgs ++ rules.flatMap(r => Seq("--pg-conf", r.toString)) ++
      Seq("--pg-map-output", (work / "mapping.txt").toString, "--pg-conf", diagnostics.toString, "--output", dex.toString) ++
      inputs.map(_.toString))
    keepMapping(work / "mapping.txt", apk.getParentFile / "mappings", versionCode, log)
    desugar.foreach { d =>
      val lib = work / "dexlib"
      IO.createDirectory(lib)
      exec(Seq("java", "-cp", (tools / "lib" / "d8.jar").toString, "com.android.tools.r8.L8", "--release",
        "--min-api", minSdk, "--lib", androidJar.toString, "--desugared-lib", d.config.toString,
        "--output", lib.toString, d.library.toString))
      // after the program's own dex files, numbered on from them
      val next = (dex * "*.dex").get().size + 1
      (lib * "*.dex").get().sortBy(_.getName).zipWithIndex.foreach { case (f, i) =>
        IO.move(f, dex / s"classes${next + i}.dex")
      }
      log("AndroidApk: desugared Java library dexed for min-api " + minSdk)
    }
    (dex * "*.dex").get().foreach(d => log(s"AndroidApk: ${d.getName} ${d.length / 1024} KB"))

    val compiled = work / "res.zip"
    exec(Seq((tools / "aapt2").toString, "compile", "--dir", resources.toString, "-o", compiled.toString))
    val linked = work / "linked.apk"
    exec(Seq((tools / "aapt2").toString, "link", "-o", linked.toString, "--manifest", manifest.toString, "-I", androidJar.toString,
      "-A", assets.toString, compiled.toString, "--min-sdk-version", minSdk, "--target-sdk-version", targetSdk,
      "--version-code", versionCode.toString, "--version-name", versionName) ++
      (if (bundletool.nonEmpty) Seq("--proto-format") else Nil))
    if (!signing.keystore.exists) sys.error(s"AndroidApk: no keystore at ${signing.keystore}")
    IO.createDirectory(apk.getParentFile)
    bundletool match {
      case Some(tool) =>
        // a bundle's one module: the manifest under manifest/, the dex under dex/, the resources and assets where
        // they are, and anything else the linked APK carries under root/
        val module = work / "base.zip"
        val in = new java.util.zip.ZipFile(linked)
        val out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(module))
        try {
          val entries = in.entries()
          while (entries.hasMoreElements) {
            val e = entries.nextElement()
            val name = e.getName
            val to =
              if (name == "AndroidManifest.xml") "manifest/AndroidManifest.xml"
              else if (name == "resources.pb" || name.startsWith("res/") || name.startsWith("assets/")) name
              else "root/" + name
            if (!e.isDirectory) {
              out.putNextEntry(new java.util.zip.ZipEntry(to))
              out.write(in.getInputStream(e).readAllBytes())
              out.closeEntry()
            }
          }
          (dex * "*.dex").get().sortBy(_.getName).foreach { d =>
            out.putNextEntry(new java.util.zip.ZipEntry("dex/" + d.getName))
            out.write(IO.readBytes(d))
            out.closeEntry()
          }
        } finally {
          in.close()
          out.close()
        }
        IO.delete(apk)
        exec(Seq("java", "-cp", tool.mkString(java.io.File.pathSeparator), "com.android.tools.build.bundletool.BundleToolMain",
          "build-bundle", "--modules=" + module, "--output=" + apk))
        // a bundle is signed as a jar is, not as an APK: apksigner will not take one
        exec(Seq("jarsigner", "-keystore", signing.keystore.toString, "-storepass:env", "APK_KS_PASS",
          "-keypass:env", "APK_KEY_PASS", "-sigalg", "SHA256withRSA", "-digestalg", "SHA-256", apk.toString, signing.alias),
          "APK_KS_PASS" -> signing.storePass, "APK_KEY_PASS" -> signing.keyPass)
        log(s"AndroidApk: $apk (bundle, v$versionCode $versionName, signed ${signing.keystore.getName} alias ${signing.alias})")
        apk
      case None =>
        // the linked APK, entry for entry (stored entries keep their sizes and CRCs), with the dex files added
        val withDex = work / "with-dex.apk"
        val in = new java.util.zip.ZipFile(linked)
        val out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(withDex))
        try {
          val entries = in.entries()
          while (entries.hasMoreElements) {
            val e = entries.nextElement()
            val copy = new java.util.zip.ZipEntry(e.getName)
            copy.setMethod(e.getMethod)
            if (e.getMethod == java.util.zip.ZipEntry.STORED) {
              copy.setSize(e.getSize)
              copy.setCompressedSize(e.getSize)
              copy.setCrc(e.getCrc)
            }
            out.putNextEntry(copy)
            out.write(in.getInputStream(e).readAllBytes())
            out.closeEntry()
          }
          (dex * "*.dex").get().sortBy(_.getName).foreach { d =>
            out.putNextEntry(new java.util.zip.ZipEntry(d.getName))
            out.write(IO.readBytes(d))
            out.closeEntry()
          }
        } finally {
          in.close()
          out.close()
        }

        val aligned = work / "aligned.apk"
        exec(Seq((tools / "zipalign").toString, "-f", "-p", "4", withDex.toString, aligned.toString))
        // the passwords go through the environment, never the argument list, so they stay out of `ps` and any command echo
        exec(Seq((tools / "apksigner").toString, "sign", "--ks", signing.keystore.toString,
          "--ks-pass", "env:APK_KS_PASS", "--key-pass", "env:APK_KEY_PASS", "--ks-key-alias", signing.alias,
          "--out", apk.toString, aligned.toString),
          "APK_KS_PASS" -> signing.storePass, "APK_KEY_PASS" -> signing.keyPass)
        log(s"AndroidApk: $apk (v$versionCode $versionName, signed ${signing.keystore.getName} alias ${signing.alias})")
        apk
    }
  }
}
