// everyui — youi's reactive UI model, one lean UI on every screen.
//
// JVM, Scala.js and Scala Native run the shared layer and reactify natively.
// Roku has no Scala runtime, so `compiler/` — everyui's own Scala 3 TASTy →
// BrightScript compiler — translates the same code into a SceneGraph channel.

ThisBuild / scalaVersion := "3.10.0-RC2"
ThisBuild / organization := "com.outr"
// reactify is compiled from its source tree (the `reactive` project); the published jar Spice depends on must not
// come along too (R8 refuses a class defined twice)
ThisBuild / excludeDependencies += ExclusionRule("com.outr", "reactify_3")

@transient val emitRoku = taskKey[File]("Compile this project, and the libraries it emits with, into a Roku channel")

/** The compiler's runtime classpath, in `compiler/out/classpath.txt`: what an app's build outside this one reads to
  * emit its own channel with RokuChannel.emit. */
@transient val compilerClasspath = taskKey[File]("Compile the Roku compiler and write its runtime classpath to compiler/out/classpath.txt")

// --- compiler: Scala 3 TASTy → Roku BrightScript + SceneGraph ----------------
// `emitRoku` runs it as a forked `java` process over the TASTy of the project
// being emitted (kept inspectable by -Yretain-trees).
lazy val compiler = (project in file("compiler"))
  .settings(
    name := "everyui-compiler",
    publish / skip := true,
    libraryDependencies += "org.scala-lang" %% "scala3-tasty-inspector" % scalaVersion.value,
    // SwiftEmitter.scala is derived from RokuEmitter.scala by compiler/swift-derivation.txt (project/SwiftEmitterDerivation.scala)
    Compile / sourceGenerators += Def.task {
      val out = (Compile / sourceManaged).value / "everyui" / "compiler" / "SwiftEmitter.scala"
      SwiftEmitterDerivation.derive(
        baseDirectory.value / "src" / "main" / "scala" / "everyui" / "compiler" / "RokuEmitter.scala",
        baseDirectory.value / "swift-derivation.txt", out)
      Seq(out)
    }.taskValue,
    compilerClasspath := {
      val conv = fileConverter.value
      val out = baseDirectory.value / "out" / "classpath.txt"
      IO.write(out, (Compile / fullClasspath).value.map(e => conv.toPath(e.data).toAbsolutePath.toString).mkString(java.io.File.pathSeparator))
      out
    }
  )

// --- compiler-api: what user code shares with the compiler --------------------
// `@native` marks a facade over a platform global (roku/.../facades.scala).
// Compiled against, never emitted.
lazy val compilerApi = (project in file("compiler-api"))
  .settings(
    name := "everyui-compiler-api",
    publish / skip := true
  )

/** Compile the current project, then run the Roku compiler over its TASTy plus
  * each of `libraries` (classes directories compiled through with it). */
def emitRokuTask(outName: String, title: String, libraries: Def.Initialize[Task[Seq[File]]]) = Def.task {
  (Compile / compile).value
  val conv = fileConverter.value
  RokuChannel.emit(streams.value.log.info(_),
    (compiler / Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile),
    (Compile / classDirectory).value,
    (Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile),
    libraries.value, baseDirectory.value / outName, baseDirectory.value, title)
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
    Seq("java", "-cp", compilerCp, "everyui.compiler.Main", "--target=swift") ++ libArgs ++
      Seq(classes.getAbsolutePath, cp, outDir.getAbsolutePath, baseDirectory.value.getAbsolutePath, "App")
  ).!
  if (rc != 0) sys.error(s"everyui.compiler.Main exited $rc")
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
    name := "everyui-reactive",
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
    name := "everyui-reactive-test",
    publish / skip := true,
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / run / fork := true,
    emitRoku := emitRokuTask("out-roku", "ReactifyTest", reactiveLibrary).value,
    emitSwift := emitSwiftTask("out-swift", reactiveLibrary).value
  )

// ─── the render-node seam: one shared UI layer, a backend per platform ──────
//
// `shared/` is the portable UI layer (Prop, Component, Label, animation) plus
// the apps. It names `everyui.render.RenderNode` but never a DOM or
// SceneGraph type; each backend project supplies its OWN RenderNode source
// root, so the backend is chosen at COMPILE time (the Scala.js / Scala Native
// pattern) rather than by subtype dispatch.
lazy val sharedSources = (file("shared") / "src" / "main" / "scala").getAbsoluteFile
lazy val appSources = (file("app") / "src" / "main" / "scala").getAbsoluteFile
lazy val protocolSources = (file("app") / "protocol" / "src" / "main" / "scala").getAbsoluteFile   // the demo's wire types, shared with server/
lazy val networkSources = (file("network") / "src" / "main" / "scala").getAbsoluteFile
def networkBackend(name: String): File = (file("network") / name / "src" / "main" / "scala").getAbsoluteFile
/** A backend's source roots: the framework (`shared/`), the example app (`app/`), the network module and its
  * backend — with Spice's own client where Spice runs (`spice = true`: the JVM and JS backends). */
def backendSources(backend: String, spice: Boolean): Seq[File] =
  Seq(sharedSources, appSources, protocolSources, networkSources, networkBackend(backend)) ++ (if (spice) Seq(networkBackend("spice")) else Nil)

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
    name := "everyui-roku",
    publish / skip := true,
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / unmanagedSourceDirectories ++= backendSources("native", spice = false),
    libraryDependencies += "com.outr" %% "spice-core" % spiceVersion,
    emitRoku := {
      val outDir = emitRokuTask("out-roku", "EveryUI", networkLibraries).value
      // The shared image assets are the SAME files the web bundle serves, so the two backends are compared on
      // identical pixels; the channel's own icons and splash screens are roku/images (tools/make-icons.py).
      val root = (ThisBuild / baseDirectory).value
      RokuChannel.assemble(outDir, root, Seq(root / "assets" / "images"), Seq(root / "assets" / "fonts"), Nil,
        Some(baseDirectory.value / "images"))
    }
  )

// --- server: serves the web build over Spice --------------------------------
// Replaces `python3 -m http.server`. Spice is outr's own HTTP stack (youi's
// example server used it too), so the backend is part of the system rather than
// a dev-only prop — where API endpoints, WebSocket hot-reload or OpenAPI go
// when the framework needs them. Consumed from ~/.ivy2/local.
lazy val spiceVersion = "1.10.9-SNAPSHOT3"

@transient val serverClasspath = taskKey[File]("Compile the server and write its runtime classpath to server/out/classpath.txt")

lazy val server = (project in file("server"))
  .dependsOn(reactive)   // spice's reactify is the source tree, not the published jar (excluded above)
  .settings(
    name := "everyui-server",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "com.outr" %% "spice-server" % spiceVersion,
      "com.outr" %% "spice-server-undertow" % spiceVersion
    ),
    // The web root is resolved relative to the BUILD root, not server/.
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    Compile / unmanagedSourceDirectories += protocolSources,
    Compile / run / fork := true,
    run / connectInput := true,
    serverClasspath := {
      val conv = fileConverter.value
      val cp = (Runtime / fullClasspath).value.map(e => conv.toPath(e.data).toAbsolutePath.toString)
      val out = baseDirectory.value / "out" / "classpath.txt"
      IO.write(out, cp.mkString(java.io.File.pathSeparator))
      out
    }
  )

// --- tvos: the UIKit backend, through the Swift compiler ----------------------
// The Roku backend's twin: the same @native facade names, implemented by
// swift/runtime/UniversalRuntime.swift over UIKit. `tvos/emitSwift` emits the
// program with reactify compiled through; tools/tvos-run.sh builds the app on
// the Mac and runs it on the Apple TV simulator.
lazy val tvos = (project in file("tvos"))
  .dependsOn(reactive, compilerApi)
  .settings(
    name := "everyui-tvos",
    publish / skip := true,
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / unmanagedSourceDirectories ++= backendSources("native", spice = false),
    libraryDependencies += "com.outr" %% "spice-core" % spiceVersion,
    emitSwift := {
      val outDir = emitSwiftTask("out-swift", networkLibraries).value
      val swift = (ThisBuild / baseDirectory).value / "swift"
      IO.copyFile(swift / "runtime" / "ScalaRuntime.swift", outDir / "ScalaRuntime.swift")
      IO.copyFile(swift / "runtime" / "UniversalRuntime.swift", outDir / "UniversalRuntime.swift")
      IO.copyFile(swift / "app" / "main.swift", outDir / "main.swift")
      IO.copyFile(swift / "app" / "Info.plist", outDir / "Info.plist")
      IO.copyDirectory(swift / "app" / "Assets.xcassets", outDir / "Assets.xcassets")   // app icons (tools/make-icons.py)
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
lazy val javafxVersion = "26.0.2"
lazy val javafxPlatform = {
  val os = sys.props("os.name").toLowerCase
  if (os.contains("mac")) "mac" else if (os.contains("win")) "win" else "linux"
}
@transient val desktopClasspath = taskKey[File]("Compile the desktop backend and write its runtime classpath to desktop/out/classpath.txt")

lazy val desktop = (project in file("desktop"))
  .dependsOn(reactive)
  .settings(
    name := "everyui-desktop",
    publish / skip := true,
    Compile / unmanagedSourceDirectories ++= backendSources("jvm", spice = true),
    Compile / unmanagedSourceDirectories += (file("app") / "desktop" / "src" / "main" / "scala").getAbsoluteFile,   // the example's launcher
    libraryDependencies += "com.outr" %% "spice-client-jvm" % spiceVersion,
    libraryDependencies ++= Seq("base", "graphics", "controls", "media").map(m => ("org.openjfx" % s"javafx-$m" % javafxVersion).classifier(javafxPlatform)),
    Compile / mainClass := Some("everyui.desktop.DesktopApp"),
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
//   sbt android/packageApk          android/out-apk/everyui.apk
//   tools/check.sh --android        runs it on an emulator and compares a frame
lazy val androidSdk = file(sys.env.getOrElse("ANDROID_HOME", sys.props("user.home") + "/opt/android-sdk"))
lazy val androidPlatform = "android-37.0"
lazy val androidBuildTools = "37.0.0"
lazy val androidMinSdk = "30"
lazy val androidJar = androidSdk / "platforms" / androidPlatform / "android.jar"
@transient val packageApk = taskKey[File]("Compile the Android backend and package it as a debug-signed APK in android/out-apk/")
@transient val androidAars = taskKey[Seq[File]]("Open the Android archives the backend depends on: their classes, as jars")
@transient val androidAarRules = taskKey[Seq[File]]("The shrinker rules those archives ship")

lazy val media3Version = "1.9.2"
lazy val media3Modules = Seq("media3-common", "media3-container", "media3-database", "media3-datasource",
  // the OkHttp data source, which is what actually fetches the media: see ExoMedia for why it is not the default one
  "media3-datasource-okhttp",
  "media3-decoder", "media3-extractor", "media3-exoplayer", "media3-exoplayer-hls")

lazy val android = (project in file("android"))
  .dependsOn(reactive)
  .settings(
    name := "everyui-android",
    publish / skip := true,
    Compile / unmanagedSourceDirectories ++= backendSources("android", spice = true),
    Compile / unmanagedSourceDirectories += (file("app") / "android" / "src" / "main" / "scala").getAbsoluteFile,   // the example's activity
    libraryDependencies += "com.outr" %% "spice-client-okhttp" % spiceVersion,
    resolvers += "Google" at "https://dl.google.com/dl/android/maven2/",
    // Media3 (ExoPlayer): what a television needs from a player and `MediaPlayer` has no answer to — its own
    // timeouts, a buffer that can be chosen, and a measurement of what the link is carrying. It ships as Android
    // archives, so the classes are opened out of them (project/Aars.scala) before anything compiles against them.
    libraryDependencies ++= media3Modules.map(m =>
      ("androidx.media3" % m % media3Version).intransitive() artifacts Artifact(m, "aar", "aar")),
    libraryDependencies ++= Seq(
      "androidx.annotation" % "annotation-jvm" % "1.9.1",
      "com.google.guava" % "guava" % "33.4.0-android"),
    androidAars := Aars.open(streams.value.log.info(_),
      update.value.select(artifact = artifactFilter(extension = "aar")), Aars.room)._1,
    androidAarRules := Aars.open(streams.value.log.info(_),
      update.value.select(artifact = artifactFilter(extension = "aar")), Aars.room)._2,
    Compile / unmanagedJars ++= androidAars.value
      .map(j => Attributed.blank[xsbti.HashedVirtualFileRef](fileConverter.value.toVirtualFile(j.toPath))),
    Compile / unmanagedJars += Attributed.blank[xsbti.HashedVirtualFileRef](fileConverter.value.toVirtualFile(androidJar.toPath)),
    Compile / scalacOptions ++= Seq("-release", "17"),
    packageApk := {
      val conv = fileConverter.value
      val buildRoot = (ThisBuild / baseDirectory).value
      val assets = target.value / "apk-assets"
      IO.delete(assets)
      IO.copyDirectory(buildRoot / "assets" / "images", assets / "images")
      (buildRoot / "assets" / "fonts" * "*.ttf").get().foreach(f => IO.copyFile(f, assets / "fonts" / f.getName))
      AndroidApk.build(streams.value.log.info(_), androidSdk, androidPlatform, androidBuildTools, androidMinSdk, "37",
        // the archives themselves are not dexable: what was opened out of them stands in their place
        (Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile).filterNot(_.getName.endsWith(".aar")),
        baseDirectory.value / "AndroidManifest.xml", baseDirectory.value / "res", assets,
        Seq(baseDirectory.value / "proguard-rules.pro"), target.value / "apk", baseDirectory.value / "out-apk" / "everyui.apk",
        // the demo app is never uploaded anywhere, so it keeps the throwaway key and a code that need not climb
        version.value, 1, AndroidApk.debug)
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
  .dependsOn(reactive)
  .settings(
    name := "everyui-webcheck",
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

// --- checks: the helpers behind tools/check.sh and the device scripts -------
// One small Scala CLI (everyui.checks.Checks): frame extraction and comparison,
// the font table check, frame-rate floors, the Roku gap meter, sideloading and
// console capture, waiting on an Android device. Scripts run it from its staged
// classpath, `checks/out/classpath.txt`.
@transient val checksClasspath = taskKey[File]("Compile the checks CLI and write its runtime classpath to checks/out/classpath.txt")

lazy val checks = (project in file("checks"))
  .settings(
    name := "everyui-checks",
    publish / skip := true,
    Compile / mainClass := Some("everyui.checks.Checks"),
    checksClasspath := {
      val conv = fileConverter.value
      val cp = (Runtime / fullClasspath).value.map(e => conv.toPath(e.data).toAbsolutePath.toString)
      val out = baseDirectory.value / "out" / "classpath.txt"
      IO.write(out, cp.mkString(java.io.File.pathSeparator))
      out
    }
  )

// --- icons: every platform's icon from the logo (project/Icons.scala) ---------
// A bare setting in sbt 2 is ThisBuild-scoped, and aggregation would then run it in every project at once
// (racing on the same files), so the task lives on the root project only.
@transient val makeIcons = taskKey[Unit]("Render every platform's icons from work/logo/svg (needs rsvg-convert)")

lazy val root = (project in file("."))
  .aggregate(compiler, compilerApi, reactive, reactiveTest, roku, tvos, desktop, android, webcheck, checks, server)
  .settings(
    name := "everyui",
    publish / skip := true,
    makeIcons := {
      val log = streams.value.log
      Icons.make((ThisBuild / baseDirectory).value, m => log.info(m))
    },
    makeIcons / aggregate := false
  )

// --- network: HTTP and DurableSocket on every backend, with Spice as the core --
// Spice (outr's HTTP stack) already has the protocol: `spice.http.WebSocket`, the
// pluggable `HttpClientImplementation`, and `DurableSocketClient` — the same code
// DataWrangler's server runs. JVM and JS backends use spice-core as published
// (a client implementation per backend); Roku and tvOS compile spice's durable
// client THROUGH the emitter, with rapid's Task, fabric's Json and scribe as
// runtime shims. Optional: nothing in shared/ depends on it.
//
// `network/run` is the JVM truth of the loopback probe; `network/emitRoku` and
// `network/emitSwift` emit the same probe with spice's durable package compiled
// through — the spike that sizes the emitter work.
lazy val spiceDurableTasty = Def.task {
  val conv = fileConverter.value
  val jar = (Compile / dependencyClasspath).value
    .map(e => conv.toPath(e.data).toFile)
    .find(_.getName.startsWith("spice-core_3"))
    .getOrElse(sys.error("spice-core_3 jar not on the network classpath"))
  RokuChannel.spiceTasty(jar, target.value / "spice-tasty")
}

lazy val networkLibraries = Def.task { reactiveLibrary.value :+ spiceDurableTasty.value }

lazy val network = (project in file("network"))
  .dependsOn(reactive, compilerApi)
  .settings(
    name := "everyui-network",
    publish / skip := true,
    libraryDependencies += "com.outr" %% "spice-core" % spiceVersion,
    // the probe runs on the JVM with the desktop's backend; emitted, only what the probe reaches is kept
    Compile / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "probe" / "src" / "main" / "scala", networkBackend("jvm"), networkBackend("spice")),
    libraryDependencies += "com.outr" %% "spice-client-jvm" % spiceVersion,
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / run / fork := true,
    emitRoku := emitRokuTask("out-roku", "NetworkProbe", networkLibraries).value,
    emitSwift := emitSwiftTask("out-swift", networkLibraries).value
  )
