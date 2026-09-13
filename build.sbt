// universal-ui — youi's reactive UI model, one lean UI on every screen.
//
// JVM, Scala.js and Scala Native run the shared layer and reactify natively.
// Roku has no Scala runtime, so `compiler/` — universal-ui's own Scala 3 TASTy →
// BrightScript compiler — translates the same code into a SceneGraph channel.

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "com.outr"

@transient val emitRoku = taskKey[File]("Compile this project, and the libraries it emits with, into a Roku channel")

// --- compiler: Scala 3 TASTy → Roku BrightScript + SceneGraph ----------------
// `emitRoku` runs it as a forked `java` process over the TASTy of the project
// being emitted (kept inspectable by -Yretain-trees).
lazy val compiler = (project in file("compiler"))
  .settings(
    name := "universal-compiler",
    publish / skip := true,
    libraryDependencies += "org.scala-lang" %% "scala3-tasty-inspector" % scalaVersion.value
  )

// --- compiler-api: what user code shares with the compiler --------------------
// `@native` marks a facade over a platform global (roku/.../facades.scala).
// Compiled against, never emitted.
lazy val compilerApi = (project in file("compiler-api"))
  .settings(
    name := "universal-compiler-api",
    publish / skip := true
  )

/** Compile the current project, then run the Roku compiler over its TASTy plus
  * each of `libraries` (classes directories compiled through with it). */
def emitRokuTask(outName: String, title: String, libraries: Def.Initialize[Task[Seq[File]]]) = Def.task {
  val log = streams.value.log
  (Compile / compile).value
  val conv = fileConverter.value
  val sep = java.io.File.pathSeparator
  val classes = (Compile / classDirectory).value
  val cp = (Compile / fullClasspath).value
    .map(e => conv.toPath(e.data).toAbsolutePath.toString).mkString(sep)
  val compilerCp = (compiler / Compile / fullClasspath).value
    .map(e => conv.toPath(e.data).toAbsolutePath.toString).mkString(sep)
  val libArgs = libraries.value.map(f => s"--library=${f.getAbsolutePath}")
  val outDir = baseDirectory.value / outName
  IO.delete(outDir); IO.createDirectory(outDir)
  log.info(s"emitRoku: emitting a Roku channel into $outDir")
  val rc = sys.process.Process(
    Seq("java", "-cp", compilerCp, "universal.compiler.Main") ++ libArgs ++
      Seq(classes.getAbsolutePath, cp, outDir.getAbsolutePath, baseDirectory.value.getAbsolutePath, title)
  ).!
  if (rc != 0) sys.error(s"universal.compiler.Main exited $rc")
  outDir
}

/** The Swift spike: the same program and libraries, emitted as `<out>/App.swift` for
  * `swift/runtime/ScalaRuntime.swift` (see tools/swift-spike.sh). */
@transient val emitSwift = taskKey[File]("Compile this project and emit it, with the libraries it emits with, as Swift")

def emitSwiftTask(outName: String, libraries: Def.Initialize[Task[Seq[File]]]) = Def.task {
  val log = streams.value.log
  (Compile / compile).value
  val conv = fileConverter.value
  val sep = java.io.File.pathSeparator
  val classes = (Compile / classDirectory).value
  val cp = (Compile / fullClasspath).value
    .map(e => conv.toPath(e.data).toAbsolutePath.toString).mkString(sep)
  val compilerCp = (compiler / Compile / fullClasspath).value
    .map(e => conv.toPath(e.data).toAbsolutePath.toString).mkString(sep)
  val libArgs = libraries.value.map(f => s"--library=${f.getAbsolutePath}")
  val outDir = baseDirectory.value / outName
  IO.delete(outDir); IO.createDirectory(outDir)
  log.info(s"emitSwift: emitting Swift into $outDir")
  val rc = sys.process.Process(
    Seq("java", "-cp", compilerCp, "universal.compiler.Main", "--target=swift") ++ libArgs ++
      Seq(classes.getAbsolutePath, cp, outDir.getAbsolutePath, baseDirectory.value.getAbsolutePath, "App")
  ).!
  if (rc != 0) sys.error(s"universal.compiler.Main exited $rc")
  outDir
}

/** reactify's classes, compiled through with every channel that uses it. */
lazy val reactiveLibrary = Def.task {
  (reactive / Compile / compile).value
  Seq((reactive / Compile / classDirectory).value)
}

// --- reactive: reactify's real source, compiled for emission -----------------
// Points at reactify's own source tree (no vendoring) with -Yretain-trees so the
// compiler can read its TASTy. `reactive/emitRoku` emits reactify alone — the
// input to the gap meter (`tools/emit-gaps.py`).
lazy val reactive = (project in file("reactive"))
  .settings(
    name := "universal-reactive",
    Compile / unmanagedSourceDirectories :=
      Seq(file("/home/mhicks/projects/open/reactify/reactify/src/main/scala")),
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    publish / skip := true,
    emitRoku := emitRokuTask("out-roku", "ReactifyProbe", Def.task(Seq.empty[File])).value
  )

// --- reactive-test: reactify's behaviour, on the JVM and on the Roku ---------
// The ACCEPTANCE TEST for running reactify unmodified on the Roku. The same
// program runs natively (`sbt reactiveTest/run`, the expected output) and is
// emitted with reactify compiled through (`sbt reactiveTest/emitRoku`,
// sideloaded and read off the debug console by tools/roku-acceptance.sh). The
// two outputs must be identical, line for line.
lazy val reactiveTest = (project in file("reactive-test"))
  .dependsOn(reactive)
  .settings(
    name := "universal-reactive-test",
    publish / skip := true,
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / run / fork := true,
    emitRoku := emitRokuTask("out-roku", "ReactifyTest", reactiveLibrary).value,
    emitSwift := emitSwiftTask("out-swift", reactiveLibrary).value
  )

// ─── the render-node seam: one shared UI layer, a backend per platform ──────
//
// `shared/` is the portable UI layer (Prop, Component, Label, animation) plus
// the apps. It names `universal.render.RenderNode` but never a DOM or
// SceneGraph type; each backend project supplies its OWN RenderNode source
// root, so the backend is chosen at COMPILE time (the Scala.js / Scala Native
// pattern) rather than by subtype dispatch.
lazy val sharedSources = (file("shared") / "src" / "main" / "scala").getAbsoluteFile

// --- web: the DOM backend ------------------------------------------------
// NOT an sbt project. The web backend is real Scala.js over the live DOM (see
// web/src/.../RenderNode.scala for why), and Scala.js has no sbt 2 plugin —
// only `sbt-scalajs_2.12_1.0` exists, so this build cannot host it. scala-cli
// builds it instead, pulling in the same shared/ sources:
//
//   mkdir -p web/out-js && cp web/index.html web/out-js/ && cp -r assets/images assets/fonts web/out-js/
//   scala-cli --power package web -o web/out-js/main.js -f   (add --js-mode release for Closure)
//   sbt server/run                                           (serves web/out-js)
//
// Unifying this back into one build means either an sbt 2 Scala.js plugin
// appearing, or moving the whole build to scala-cli / sbt 1.

// --- roku: the SceneGraph backend --------------------------------------------
lazy val roku = (project in file("roku"))
  .dependsOn(reactive, compilerApi)
  .settings(
    name := "universal-roku",
    publish / skip := true,
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / unmanagedSourceDirectories += sharedSources,
    emitRoku := {
      val outDir = emitRokuTask("out-roku", "UniversalUI", reactiveLibrary).value
      val rt = baseDirectory.value / "runtime" / "UniversalRuntime.brs"
      IO.copyFile(rt, outDir / "components" / "UniversalRuntime.brs")
      // Shared image assets — the SAME files the web bundle serves, so the two
      // backends are compared on identical pixels.
      val images = (ThisBuild / baseDirectory).value / "assets" / "images"
      if (images.isDirectory) IO.copyDirectory(images, outDir / "images")
      // Bundled faces, loaded by SceneGraph Font nodes from pkg:/fonts/.
      val fonts = (ThisBuild / baseDirectory).value / "assets" / "fonts"
      (fonts * "*.ttf").get().foreach(f => IO.copyFile(f, outDir / "fonts" / f.getName))
      // The scene is generated; add our runtime to its <script> list so the
      // ui* globals resolve in the component's scope.
      val scene = outDir / "components" / "AppScene.xml"
      val patched = IO.read(scene).replace(
        """<script type="text/brightscript" uri="pkg:/components/App.brs" />""",
        """<script type="text/brightscript" uri="pkg:/components/UniversalRuntime.brs" />
  <script type="text/brightscript" uri="pkg:/components/App.brs" />"""
      )
      if (patched == IO.read(scene)) sys.error("emitRoku: AppScene.xml no longer has the App.brs script line to patch")
      IO.write(scene, patched)
      outDir
    }
  )

// --- server: serves the web build over Spice --------------------------------
// Replaces `python3 -m http.server`. Spice is outr's own HTTP stack (youi's
// example server used it too), so the backend is part of the system rather than
// a dev-only prop — where API endpoints, WebSocket hot-reload or OpenAPI go
// when the framework needs them. Consumed from ~/.ivy2/local.
lazy val spiceVersion = "1.10.9-SNAPSHOT2"

lazy val server = (project in file("server"))
  .settings(
    name := "universal-server",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "com.outr" %% "spice-server" % spiceVersion,
      "com.outr" %% "spice-server-undertow" % spiceVersion
    ),
    // The web root is resolved relative to the BUILD root, not server/.
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    Compile / run / fork := true,
    run / connectInput := true
  )

// --- tvos: the UIKit backend, through the Swift compiler ----------------------
// The Roku backend's twin: the same @native facade names, implemented by
// swift/runtime/UniversalRuntime.swift over UIKit. `tvos/emitSwift` emits the
// program with reactify compiled through; tools/tvos-run.sh builds the app on
// the Mac and runs it on the Apple TV simulator.
lazy val tvos = (project in file("tvos"))
  .dependsOn(reactive, compilerApi)
  .settings(
    name := "universal-tvos",
    publish / skip := true,
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / unmanagedSourceDirectories += sharedSources,
    emitSwift := {
      val outDir = emitSwiftTask("out-swift", reactiveLibrary).value
      val swift = (ThisBuild / baseDirectory).value / "swift"
      IO.copyFile(swift / "runtime" / "ScalaRuntime.swift", outDir / "ScalaRuntime.swift")
      IO.copyFile(swift / "runtime" / "UniversalRuntime.swift", outDir / "UniversalRuntime.swift")
      IO.copyFile(swift / "app" / "main.swift", outDir / "main.swift")
      IO.copyFile(swift / "app" / "Info.plist", outDir / "Info.plist")
      val assets = (ThisBuild / baseDirectory).value / "assets"
      IO.copyDirectory(assets / "images", outDir / "images")
      (assets / "fonts" * "*.ttf").get().foreach(f => IO.copyFile(f, outDir / "fonts" / f.getName))
      outDir
    }
  )

// --- desktop: the JavaFX backend ---------------------------------------------
// JavaFX's retained scene graph (Pane, Text, ImageView) on the JVM — DESIGN.md's
// near-term desktop path, to ship later as a GraalVM native image.
//
//   sbt desktop/run                 a 1920x1080 window
//   tools/check.sh                  freezes a frame under Xvfb and compares it
lazy val javafxVersion = "21"
lazy val javafxPlatform = {
  val os = sys.props("os.name").toLowerCase
  if (os.contains("mac")) "mac" else if (os.contains("win")) "win" else "linux"
}
@transient val desktopClasspath = taskKey[File]("Compile the desktop backend and write its runtime classpath to desktop/out/classpath.txt")

lazy val desktop = (project in file("desktop"))
  .dependsOn(reactive)
  .settings(
    name := "universal-desktop",
    publish / skip := true,
    Compile / unmanagedSourceDirectories += sharedSources,
    libraryDependencies ++= Seq("base", "graphics").map(m => ("org.openjfx" % s"javafx-$m" % javafxVersion).classifier(javafxPlatform)),
    Compile / mainClass := Some("universal.desktop.DesktopApp"),
    Compile / run / fork := true,
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    desktopClasspath := {
      val conv = fileConverter.value
      val cp = (Runtime / fullClasspath).value.map(e => conv.toPath(e.data).toAbsolutePath.toString)
      val out = baseDirectory.value / "out" / "classpath.txt"
      IO.write(out, cp.mkString(java.io.File.pathSeparator))
      out
    }
  )

// --- android: the Android Views backend --------------------------------------
// Native Views (TextView, ImageView, a ViewGroup that positions absolutely) for
// Android phones and Android TV. No Gradle: the SDK's own tools package it —
// R8 (the program and the Scala library shrunk, optimised and dexed; it ships
// inside build-tools' d8.jar), aapt2 (manifest and assets), zipalign, apksigner
// (the debug key).
//
//   sbt android/packageApk          android/out-apk/universal-ui.apk
//   tools/check.sh --android        runs it on an emulator and compares a frame
lazy val androidSdk = file(sys.env.getOrElse("ANDROID_HOME", sys.props("user.home") + "/opt/android-sdk"))
lazy val androidPlatform = "android-35"
lazy val androidBuildTools = "36.0.0"
lazy val androidMinSdk = "30"
lazy val androidJar = androidSdk / "platforms" / androidPlatform / "android.jar"
@transient val packageApk = taskKey[File]("Compile the Android backend and package it as a debug-signed APK in android/out-apk/")

lazy val android = (project in file("android"))
  .dependsOn(reactive)
  .settings(
    name := "universal-android",
    publish / skip := true,
    Compile / unmanagedSourceDirectories += sharedSources,
    Compile / unmanagedJars += Attributed.blank[xsbti.HashedVirtualFileRef](fileConverter.value.toVirtualFile(androidJar.toPath)),
    Compile / scalacOptions ++= Seq("-release", "17"),
    packageApk := {
      val log = streams.value.log
      val conv = fileConverter.value
      val buildRoot = (ThisBuild / baseDirectory).value
      val tools = androidSdk / "build-tools" / androidBuildTools
      val work = target.value / "apk"
      IO.delete(work)
      IO.createDirectory(work)
      def exec(cmd: Seq[String]): Unit = {
        val rc = sys.process.Process(cmd).!
        if (rc != 0) sys.error(s"packageApk: ${cmd.head} exited $rc")
      }

      // The program: this project, reactify and the Scala library — the classpath without android.jar. d8 reads jars,
      // so class directories are zipped first.
      val inputs = (Compile / fullClasspath).value
        .map(e => conv.toPath(e.data).toFile)
        .filterNot(_ == androidJar)
        .zipWithIndex
        .map { case (f, i) =>
          if (f.isDirectory) {
            val jar = work / "classes" / s"$i.jar"
            IO.zip(Path.allSubpaths(f).filterNot(_._1.isDirectory), jar, None)
            jar
          } else f
        }
      val dex = work / "dex"
      IO.createDirectory(dex)
      // R8: whole-program shrinking from the activity the manifest names (android/proguard-rules.pro),
      // then dexing — in place of d8's dex-everything.
      val rules = baseDirectory.value / "proguard-rules.pro"
      // what the rules matched, and what R8 removed — the record when a shrunk build misbehaves
      val diagnostics = work / "diagnostics.pro"
      IO.write(diagnostics, s"-printseeds ${work / "seeds.txt"}\n-printusage ${work / "usage.txt"}\n")
      log.info(s"packageApk: R8 over ${inputs.size} inputs")
      exec(Seq("java", "-cp", (tools / "lib" / "d8.jar").toString, "com.android.tools.r8.R8",
        "--release", "--min-api", androidMinSdk, "--lib", androidJar.toString, "--pg-conf", rules.toString,
        "--pg-map-output", (work / "mapping.txt").toString, "--pg-conf", diagnostics.toString,
        "--output", dex.toString) ++ inputs.map(_.toString))
      (dex * "*.dex").get().foreach(d => log.info(s"packageApk: ${d.getName} ${d.length / 1024} KB"))

      // Shared assets, the same files the other backends load.
      val assets = work / "assets"
      IO.copyDirectory(buildRoot / "assets" / "images", assets / "images")
      (buildRoot / "assets" / "fonts" * "*.ttf").get().foreach(f => IO.copyFile(f, assets / "fonts" / f.getName))
      val linked = work / "linked.apk"
      exec(Seq((tools / "aapt2").toString, "link", "-o", linked.toString,
        "--manifest", (baseDirectory.value / "AndroidManifest.xml").toString, "-I", androidJar.toString,
        "-A", assets.toString, "--min-sdk-version", androidMinSdk, "--target-sdk-version", "35",
        "--version-code", "1", "--version-name", version.value))

      // aapt2 cannot add dex files: copy its APK entry for entry (stored entries stay stored) and add them.
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
      if (!keystore.exists) sys.error(s"packageApk: no debug keystore at $keystore")
      val apk = baseDirectory.value / "out-apk" / "universal-ui.apk"
      IO.createDirectory(apk.getParentFile)
      exec(Seq((tools / "apksigner").toString, "sign", "--ks", keystore.toString, "--ks-pass", "pass:android",
        "--key-pass", "pass:android", "--ks-key-alias", "androiddebugkey", "--out", apk.toString, aligned.toString))
      log.info(s"packageApk: $apk")
      apk
    }
  )

// --- webcheck: the web build in a real browser ---------------------------------
// Headless Chrome throttles requestAnimationFrame, so it cannot measure the web
// backend's frame rate. RoboBrowser (outr's CDP library, from ~/.ivy2/local)
// runs a HEADFUL Chrome on its own Xvfb display at 1920x1080 — rAF at the
// display's 60 Hz — and relays the page's console. Not part of the app: a tool.
//
//   tools/check.sh --web-live       runs the web build live and checks its frame rate
@transient val webcheckClasspath = taskKey[File]("Compile the web checker and write its runtime classpath to webcheck/out/classpath.txt")

lazy val webcheck = (project in file("webcheck"))
  .settings(
    name := "universal-webcheck",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "com.outr" %% "robobrowser-cdp" % "2.4.7-SNAPSHOT",
      // spice's HTTP client is pluggable; RoboBrowser talks to Chrome's debug endpoint through it
      "com.outr" %% "spice-client-netty" % spiceVersion
    ),
    webcheckClasspath := {
      val conv = fileConverter.value
      val cp = (Runtime / fullClasspath).value.map(e => conv.toPath(e.data).toAbsolutePath.toString)
      val out = baseDirectory.value / "out" / "classpath.txt"
      IO.write(out, cp.mkString(java.io.File.pathSeparator))
      out
    }
  )

lazy val root = (project in file("."))
  .aggregate(compiler, compilerApi, reactive, reactiveTest, roku, tvos, desktop, android, webcheck, server)
  .settings(name := "universal-ui", publish / skip := true)
