import sbt.*

/** An Android app as a debug-signed APK, from a Scala program's classpath: R8 over the program (shrinking from the
  * classes the rules keep, then dexing), aapt2 over the resources and manifest, the dex added, aligned and signed with
  * the debug key. No Gradle and no Android plugin: the SDK's own tools.
  *
  * EveryUI's `android/packageApk` calls it, and so does an app's build that includes this file in its meta-build. */
object AndroidApk {
  def build(log: String => Unit, sdk: File, platform: String, buildTools: String, minSdk: String, targetSdk: String,
            program: Seq[File], manifest: File, resources: File, assets: File, rules: Seq[File], work: File, apk: File,
            versionName: String): File = {
    val tools = sdk / "build-tools" / buildTools
    val androidJar = sdk / "platforms" / platform / "android.jar"
    IO.delete(work)
    IO.createDirectory(work)
    def exec(cmd: Seq[String]): Unit = {
      val rc = sys.process.Process(cmd).!
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
    log(s"AndroidApk: R8 over ${inputs.size} inputs")
    exec(Seq("java", "-cp", (tools / "lib" / "d8.jar").toString, "com.android.tools.r8.R8",
      "--release", "--min-api", minSdk, "--lib", androidJar.toString) ++ rules.flatMap(r => Seq("--pg-conf", r.toString)) ++
      Seq("--pg-map-output", (work / "mapping.txt").toString, "--pg-conf", diagnostics.toString, "--output", dex.toString) ++
      inputs.map(_.toString))
    (dex * "*.dex").get().foreach(d => log(s"AndroidApk: ${d.getName} ${d.length / 1024} KB"))

    val compiled = work / "res.zip"
    exec(Seq((tools / "aapt2").toString, "compile", "--dir", resources.toString, "-o", compiled.toString))
    val linked = work / "linked.apk"
    exec(Seq((tools / "aapt2").toString, "link", "-o", linked.toString, "--manifest", manifest.toString, "-I", androidJar.toString,
      "-A", assets.toString, compiled.toString, "--min-sdk-version", minSdk, "--target-sdk-version", targetSdk,
      "--version-code", "1", "--version-name", versionName))

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
    val keystore = file(sys.props("user.home")) / ".android" / "debug.keystore"
    if (!keystore.exists) sys.error(s"AndroidApk: no debug keystore at $keystore")
    IO.createDirectory(apk.getParentFile)
    exec(Seq((tools / "apksigner").toString, "sign", "--ks", keystore.toString, "--ks-pass", "pass:android",
      "--key-pass", "pass:android", "--ks-key-alias", "androiddebugkey", "--out", apk.toString, aligned.toString))
    log(s"AndroidApk: $apk")
    apk
  }
}
