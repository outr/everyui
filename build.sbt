// everyui — youi's reactive UI model, one lean UI on every screen.
//
// JVM, Scala.js and Scala Native run the shared layer and reactify natively.
// Roku has no Scala runtime, so `compiler/` — everyui's own Scala 3 TASTy →
// BrightScript compiler — translates the same code into a SceneGraph channel.

ThisBuild / scalaVersion := "3.10.0"
ThisBuild / organization := "com.outr"
ThisBuild / version := "0.1.6-SNAPSHOT"

// --- publishing: Maven Central, under the Apache License 2.0 ------------------------------------------------------
// sbt's own Central Portal support (sbt-pgp signs): `publishSigned` stages a release in localStaging, and `sonaRelease`
// uploads and releases it (publish.sh); a snapshot goes to Central's snapshots repository. The credentials are for
// central.sonatype.com, kept outside the build.
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots) else localStaging.value
}
ThisBuild / publishMavenStyle := true
ThisBuild / pomIncludeRepository := (_ => false)
ThisBuild / description := "EveryUI: one reactive Scala 3 UI on the web, the desktop, Android, Roku and Apple TV"
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / homepage := Some(url("https://github.com/outr/everyui"))
ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/outr/everyui"), "scm:git@github.com:outr/everyui.git"))
ThisBuild / developers := List(
  Developer(id = "darkfrog", name = "Matt Hicks", email = "matt@outr.com", url = url("https://github.com/darkfrog26")))
// reactify is compiled from its source (the `reactive` project, reactifySources); the published jar Spice depends on
// must not come along too (R8 refuses a class defined twice)
ThisBuild / excludeDependencies += ExclusionRule("com.outr", "reactify_3")

@transient val emitRoku = taskKey[File]("Compile this project, and the libraries it emits with, into a Roku channel")

/** The compiler's runtime classpath, in `compiler/out/classpath.txt`: what an app's build outside this one reads to
  * emit its own channel with RokuChannel.emit. */
@transient val compilerClasspath = taskKey[File]("Compile the Roku compiler and write its runtime classpath to compiler/out/classpath.txt")

// --- compiler: Scala 3 TASTy → Roku BrightScript + SceneGraph ----------------
// `emitRoku` runs it as a forked `java` process over the TASTy of the project
// being emitted (kept inspectable by -Yretain-trees).
lazy val compiler = (project in file("compiler"))
  .settings(published)
  .settings(
    name := "everyui-compiler",
    description := "Scala 3 TASTy to BrightScript and Swift, the compiler sbt-everyui runs for Roku and Apple TV",
    libraryDependencies += "org.scala-lang" %% "scala3-tasty-inspector" % scalaVersion.value,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test,
    // SwiftEmitterSpec reads the derived Swift emitter's templates for the runtime functions they call
    Test / fork := true,
    Test / javaOptions +=
      s"-Deveryui.swiftEmitter=${(Compile / sourceManaged).value / "everyui" / "compiler" / "SwiftEmitter.scala"}",
    // SwiftEmitter.scala is derived from RokuEmitter.scala by compiler/swift-derivation.txt
    // (project/SwiftEmitterDerivation.scala), and SwiftRuntimeNames.scala lists the functions the Swift runtime
    // defines, which an emit checks its calls against
    Compile / sourceGenerators += Def.task {
      val dir = (Compile / sourceManaged).value / "everyui" / "compiler"
      val runtime = (ThisBuild / baseDirectory).value / "swift" / "runtime"
      Seq(
        SwiftEmitterDerivation.derive(
          baseDirectory.value / "src" / "main" / "scala" / "everyui" / "compiler" / "RokuEmitter.scala",
          baseDirectory.value / "swift-derivation.txt", dir / "SwiftEmitter.scala"),
        SwiftEmitterDerivation.runtimeNames((runtime * "*.swift").get().sortBy(_.getName),
          dir / "SwiftRuntimeNames.scala"))
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
  .settings(published)
  .settings(
    name := "everyui-compiler-api",
    description := "EveryUI's @native, for a facade over a Roku or Apple TV global"
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
  (Compile / compile).value
  val conv = fileConverter.value
  AppleApp.emit(streams.value.log.info(_),
    (compiler / Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile),
    (Compile / classDirectory).value,
    (Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile),
    libraries.value, baseDirectory.value / outName, baseDirectory.value)
}

/** reactify's classes, compiled through with every channel that uses it. */
lazy val reactiveLibrary = Def.task {
  (reactive / Compile / compile).value
  Seq((reactive / Compile / classDirectory).value)
}

// --- reactive: reactify's real source, compiled for emission -----------------
// reactify's own source, unmodified (no vendoring), compiled with -Yretain-trees so the
// compiler can read its TASTy. `reactive/emitRoku` emits reactify alone — the
// input to the gap meter (`tools/emit-gaps.py`).
@transient val reactifySourceDirectory = taskKey[File](
  "reactify's source: its sources jar at reactifyVersion, unpacked, or the tree EVERYUI_REACTIFY_SOURCES names")

/** reactify's source files, for the projects that compile it (`reactive`, and the Android ones, which compile it with
  * their own options): its published sources jar at reactifyVersion, unpacked into target/reactify-sources/<version>,
  * or the tree the environment variable or system property EVERYUI_REACTIFY_SOURCES names, for working on reactify
  * with EveryUI (project/LibrarySources.scala). `reactive` is named by its id, as it names reactifySources itself and two
  * lazy vals naming each other never settle (the build's load waits on itself). */
lazy val reactifySources: Def.Initialize[Task[Seq[File]]] =
  LibrarySources.sources(LocalProject("reactive") / reactifySourceDirectory)

lazy val reactive = (project in file("reactive"))
  .settings(
    name := "everyui-reactive",
    reactifySourceDirectory := LibrarySources.directory("com.outr" %% "reactify" % reactifyVersion,
      Some("EVERYUI_REACTIFY_SOURCES"), file("target").getAbsoluteFile).value,
    // the build excludes reactify_3 everywhere, and its sources jar is fetched with this project's resolution (it has
    // no dependency of its own for the exclusion to keep out)
    excludeDependencies := Nil,
    Compile / unmanagedSourceDirectories := Nil,
    Compile / sourceGenerators += reactifySources.taskValue,
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
/** A log's plain model (`everyui.log.LogEvent` and its kin on the wire, LogLevel and LogClassifier): no more than Scala,
  * in the shared layer; the network module's codecs for it (LogWire) and a server's log (LogBacklog) are compiled with it
  * where the shared layer is not. */
lazy val logModelSources = (file("shared") / "src" / "main" / "scala" / "everyui" / "log" / "model").getAbsoluteFile
/** The example app's demos of what only the web, the desktop and Android run (lists, pickers): the Roku and tvOS
  * compile `app/native` in their place, which runs none of them. */
def demoSources(backend: String): File =
  (file("app") / (if (backend == "native") "native" else "full") / "src" / "main" / "scala").getAbsoluteFile
def networkBackend(name: String): File = (file("network") / name / "src" / "main" / "scala").getAbsoluteFile
/** A backend's source roots: the framework (`shared/`), the example app (`app/`), the network module and its
  * backend, and its pieces over the UI layer (`network/files`: a picked file as a form part) — with Spice's own client
  * where Spice runs (`spice = true`: the JVM and JS backends), and there the store's fabric codec (`network/fabric`),
  * which needs both the UI layer and fabric. */
def backendSources(backend: String, spice: Boolean): Seq[File] =
  Seq(sharedSources, appSources, demoSources(backend), protocolSources, networkSources, networkBackend(backend),
    networkBackend("files")) ++
    (if (spice) Seq(networkBackend("spice"), networkBackend("fabric")) else Nil)

// --- published: what an app outside this build depends on (CONSUMING.md) ----------------------------------------
// `sbt publishLocal` publishes these, and only these:
//
//   everyui-web           (Scala.js)   the shared UI layer over the DOM backend
//   everyui-desktop       (JVM)        the shared UI layer over JavaFX, with the network module
//   everyui-android       (JVM)        the shared UI layer over Android's Views, with the network module
//   everyui-roku          (JVM, TASTy) the shared UI layer over SceneGraph, with the network module, for the compiler
//   everyui-tvos          (JVM, TASTy) the shared UI layer over UIKit, with the network module, for the compiler
//   everyui-compiler      (JVM)        Scala 3 TASTy to BrightScript and Swift, which the sbt plugin runs
//   everyui-compiler-api  (JVM)        `@native`, which the Roku and tvOS facades are written with
//   everyui-network       (JVM and JS) HTTP and DurableSocket with Spice as the core: the web's, and a server's
//   everyui-seo           (JVM and JS) what an app's server writes for search and answer engines
//   everyui-monaco        (Scala.js)   Monaco as EveryUI components
//   sbt-everyui           (sbt 2)      an app's build: web targets, and packaging for the other platforms
//
// The shared layer names `everyui.render.RenderNode` and a backend supplies it at compile time, so it ships inside
// each backend's artifact rather than as a core of its own. reactify comes as its published snapshot here (4.2.1,
// what the `reactive` project compiles from source), which evicts the older release Spice depends on.
lazy val reactifyVersion = "4.2.1"
/** The commit a published artifact is built from, "-dirty" after it when the tree had uncommitted changes: written into
  * each published jar's manifest as EveryUI-Commit, so an app's build can say which EveryUI it has (sbt-everyui's
  * everyuiCommit) while the version stays 0.1.0-SNAPSHOT. */
lazy val builtFrom: String = {
  import scala.sys.process.*
  val sha = scala.util.Try(Process(Seq("git", "rev-parse", "HEAD"), file(".")).!!.trim).getOrElse("unknown")
  val dirty = scala.util.Try(Process(Seq("git", "status", "--porcelain"), file(".")).!!.trim.nonEmpty).getOrElse(false)
  if (dirty) sha + "-dirty" else sha
}
/** What every published artifact is: published, its commit in its manifest, and EveryUI's LICENSE, NOTICE and
  * THIRD-PARTY.md in its jar's META-INF (the third-party licences beside what carries them: sharedLicenses, and the
  * `licenses/` of Monaco's, xterm.js's, hls.js's and LiveKit's files). */
lazy val published = Seq(publish / skip := false,
  Compile / packageBin / packageOptions += Package.ManifestAttributes("EveryUI-Commit" -> builtFrom),
  Compile / resourceGenerators += bundled("META-INF",
    root => Seq(root / "LICENSE", root / "NOTICE", root / "THIRD-PARTY.md")).taskValue)

/** `files` (from the build's root) as resources under `path` in a published jar, for the sbt plugin to take out. */
def bundled(path: String, files: File => Seq[File]): Def.Initialize[Task[Seq[File]]] = Def.task {
  val dir = (Compile / resourceManaged).value / path
  files((ThisBuild / baseDirectory).value).map { f =>
    val copy = dir / f.getName
    IO.copyFile(f, copy)
    copy
  }
}

/** The example's translations (assets/l10n), at their paths in an Apple bundle, a Roku package and an APK's assets,
  * where `Strings` reads them as `/l10n/app_<code>.arb` (demo=net). */
def exampleTranslations(root: File): Seq[(String, File)] =
  (root / "assets" / "l10n" * "*.arb").get().map(f => ("l10n/" + f.getName, f))

/** EveryUI's fonts (and Material Icons' codepoints), in each backend's jar under everyui/fonts/, which an app copies out
  * (sbt-everyui's everyuiFonts, and everyuiWebFonts on the web). */
lazy val fontResources = bundled("everyui/fonts", root => (root / "assets" / "fonts" * ("*.ttf" | "*.codepoints")).get())

/** The licences of what every backend's jar carries of others': the fonts' (assets/licenses: Open Sans's and Roboto
  * Mono's OFL, Material Icons' Apache 2.0, whose codepoints MaterialIcon was generated from) and gemoji's MIT
  * (assets/emoji, whose table EmojiData was generated from), under META-INF/licenses/. */
lazy val sharedLicenses = Def.task {
  val dir = (Compile / resourceManaged).value / "META-INF" / "licenses"
  val root = (ThisBuild / baseDirectory).value
  ((root / "assets" / "licenses" * "*.txt").get().map(f => f -> f.getName) :+
    (root / "assets" / "emoji" / "LICENSE" -> "gemoji.txt")).map { case (f, name) =>
    IO.copyFile(f, dir / name)
    dir / name
  }
}

/** A published backend's jar compiles the shared layer, the backend (`backend/src/main/scala`), and the network module
  * with the backend's side of it (`network`), as the example app compiles them: with Spice's own client and the store's
  * fabric codec where Spice runs (`spice = true`: the desktop and Android). The web's network module is everyui-network. */
def publishedSources(backend: String, network: String, spice: Boolean): Seq[File] =
  Seq(sharedSources, (file(backend) / "src" / "main" / "scala").getAbsoluteFile, networkSources, networkBackend(network),
    networkBackend("files")) ++
    (if (spice) Seq(networkBackend("spice"), networkBackend("fabric")) else Nil)

// --- web: the DOM backend -----------------------------------------------------
// Real Scala.js over the live DOM (see web/src/.../RenderNode.scala for why), with the shared layer compiled in. The
// example app's bundle is still built by scala-cli (web/project.scala), which compiles the same sources with the app:
//
//   mkdir -p web/out-js && cp web/index.html web/webview.html web/sandbox.html web/out-js/ && cp -r assets/images assets/fonts web/out-js/
//   scala-cli --power package web -o web/out-js/main.js -f   (add --js-mode release for Closure)
//   sbt server/run                                           (serves web/out-js)
lazy val web = (project in file("web"))
  .enablePlugins(ScalaJSPlugin)
  .settings(published)
  .settings(
    name := "everyui-web",
    description := "EveryUI's UI layer over the DOM, for Scala.js",
    sourcesInBase := false,   // web/project.scala is scala-cli's
    Compile / unmanagedSourceDirectories += sharedSources,
    libraryDependencies ++= Seq(
      "org.scala-js" %% "scalajs-dom" % "2.8.1",
      "com.outr" %% "reactify" % reactifyVersion
    ),
    Compile / resourceGenerators += fontResources.taskValue,
    Compile / resourceGenerators += sharedLicenses.taskValue,
    // hls.js, for a browser with no HLS of its own: served next to the page (project/HlsAssets.scala)
    Compile / resourceGenerators += Def.task(HlsAssets.generate(streams.value.log.info(_), (Compile / resourceManaged).value)).taskValue,
    // LiveKit's client, for a call (everyui.call): served next to the page, loaded once a call connects
    // (project/LiveKitAssets.scala)
    Compile / resourceGenerators += Def.task(LiveKitAssets.generate(streams.value.log.info(_),
      (ThisBuild / baseDirectory).value / "NOTICE", (Compile / resourceManaged).value)).taskValue
  )

// --- network, published: the module and its backend, for the JVM and the browser ------------------------------
// (the `network` project below is the probe and the Roku/tvOS spike, over the same sources)
lazy val networkJvm = (project in file("network/jvm"))
  .settings(published)
  .settings(
    name := "everyui-network",
    description := "EveryUI's HTTP and DurableSocket, with Spice as the core",
    // the published reactify, not the source tree: nothing here needs excluding
    excludeDependencies := Nil,
    Compile / unmanagedSourceDirectories ++= Seq(networkSources, networkBackend("spice"), logModelSources),
    libraryDependencies ++= Seq(
      "com.outr" %% "spice-core" % spiceVersion,
      "com.outr" %% "spice-client-jvm" % spiceVersion,
      "com.outr" %% "reactify" % reactifyVersion,
      // its own code reads and writes JSON (Network, FormPart): named, rather than the release spice-core brings
      "org.typelevel" %% "fabric-io" % fabricVersion
    )
  )

// on the web it also carries the store's fabric codec (everyui.store.FabricCodec), for which it takes everyui-web, and
// its pieces over the UI layer (network/files)
lazy val networkJs = (project in file("network/js"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(web)
  .settings(published)
  .settings(
    name := "everyui-network",
    description := "EveryUI's HTTP and DurableSocket, with Spice as the core",
    Compile / unmanagedSourceDirectories ++= Seq(networkSources, networkBackend("spice"), networkBackend("fabric"), networkBackend("files")),
    libraryDependencies ++= Seq(
      "com.outr" %% "spice-client" % spiceVersion,
      // spice-core_sjs1 as published locally lists its dependencies by their JVM names (reactify_3 is excluded
      // build-wide, and so in this module's POM): the Scala.js builds of them are named here
      "org.typelevel" %% "fabric-io" % "1.30.4",
      "com.outr" %% "rapid-core" % "2.9.10",
      "com.outr" %% "scribe" % "3.19.1",
      "com.outr" %% "profig" % "3.7.1",
      "com.outr" %% "reactify" % reactifyVersion,
      // UUID.randomUUID (the durable client id) needs SecureRandom, which Scala.js keeps in its own library
      ("org.scala-js" %% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13)
    )
  )

// --- roku: the SceneGraph backend --------------------------------------------
lazy val roku = (project in file("roku"))
  .dependsOn(reactive, compilerApi)
  .settings(
    name := "everyui-roku-example",   // the example app; everyui-roku is the published backend
    publish / skip := true,
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / unmanagedSourceDirectories ++= backendSources("native", spice = false),
    libraryDependencies += "com.outr" %% "spice-core" % spiceVersion,
    emitRoku := {
      val outDir = emitRokuTask("out-roku", "EveryUI", networkLibraries).value
      // The shared image assets are the SAME files the web bundle serves, so the two backends are compared on
      // identical pixels; the channel's own icons and splash screens are roku/images (tools/make-icons.py).
      val root = (ThisBuild / baseDirectory).value
      RokuChannel.assemble(outDir, root / "roku" / "runtime", Seq(root / "assets" / "images"), Seq(root / "assets" / "fonts"),
        (root / "assets" / "sounds" * "*.wav").get().map(f => ("sounds/" + f.getName, f)) ++ exampleTranslations(root),
        Some(baseDirectory.value / "images"))
    }
  )

// --- server: serves the web build over Spice --------------------------------
// Replaces `python3 -m http.server`. Spice is outr's own HTTP stack (youi's
// example server used it too), so the backend is part of the system rather than
// a dev-only prop — where API endpoints, WebSocket hot-reload or OpenAPI go
// when the framework needs them. Consumed from ~/.ivy2/local.
lazy val spiceVersion = "1.10.10"

// --- seo: what a page says to search and answer engines, written by an app's server -------------------------
// Plain Scala over the shared PageMeta and Alternate (every backend has those); the rest (the page's content as HTML
// and Markdown, JSON-LD, sitemap.xml, robots.txt, llms.txt) is the server's alone, kept out of shared so no device
// build carries it. An app's server compiles these two source directories in.
lazy val seo = (project in file("seo"))
  .settings(published)
  .settings(
    name := "everyui-seo",
    description := "What an EveryUI app's server writes for search and answer engines",
    Compile / unmanagedSourceDirectories += sharedSources / "everyui" / "seo",
    // the build's side of the well-known files (sbt-support's AppDomains), checked against the server's
    Test / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "sbt-support" / "src" / "main" / "scala",
    libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "com.aayushatharva.brotli4j" % "brotli4j" % brotli4jVersion % Test)
  )

// The same for the browser, for a page that writes its own structured data (JsonLd) and head: everyui-seo for
// Scala.js. The shared PageMeta and Alternate come with everyui-web, so they are not compiled in again here, and
// WellKnown (and WellKnownAnswer, over it), which read files, are the server's alone.
lazy val seoJs = (project in file("seo/js"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(web)
  .settings(published)
  .settings(
    name := "everyui-seo",
    description := "What an EveryUI app's server writes for search and answer engines",
    Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "seo" / "src" / "main" / "scala",
    Compile / unmanagedSources / excludeFilter := HiddenFileFilter || "WellKnown.scala" || "WellKnownAnswer.scala"
  )

/** brotli4j, which the plugin compresses with (as this build's meta-build does, project/plugins.sbt). */
lazy val brotli4jVersion = "1.23.0"

// --- sbt-everyui: an app's build side, published as an sbt plugin -----------------------------------------------
// For the web, everyuiWebTarget (JS, WebAssembly, or both with the loader choosing), everyuiWebScripts, and the plain
// helpers (WebApp, AppDomains, WebScripts); for the other platforms, the packaging helpers (AndroidApk, AndroidRuntime,
// Aars, RokuChannel, AppleApp) and the tasks that take what they need out of the published backends (everyuiCompiler,
// everyuiEmitLibraries, everyuiRokuRuntime, everyuiFonts, …). Compiled with sbt 2's own Scala; the helpers in
// src/main/scala are plain Scala (and brotli4j) an app's build can also compile from source (CONSUMING.md), the plugin in
// src/plugin/scala needs sbt, Scala.js and ASM. This build compiles both into its own meta-build (project/plugins.sbt).
lazy val sbtEveryui = (project in file("sbt-support"))
  .enablePlugins(SbtPlugin)
  .settings(published)
  .settings(
    name := "sbt-everyui",
    description := "EveryUI's sbt 2 plugin: web targets, and packaging for every other platform",
    scalaVersion := "3.8.4",
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "plugin" / "scala",
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0"),
    // asm itself too: asm-tree names it, but a plugin's own dependencies are not always followed that far
    libraryDependencies ++= Seq("org.ow2.asm" % "asm" % "9.10.1", "org.ow2.asm" % "asm-tree" % "9.10.1"),
    // Brotli at its best for a web bundle (WebBundle.precompress) and its fonts (Woff2): Google's encoder, natively
    libraryDependencies += "com.aayushatharva.brotli4j" % "brotli4j" % brotli4jVersion,
    // the release the plugin belongs to (everyuiVersion), which fetches the compiler at the same one
    Compile / resourceGenerators += Def.task {
      val out = (Compile / resourceManaged).value / "everyui" / "sbt" / "everyui.properties"
      IO.write(out, s"version=${version.value}\n")
      Seq(out)
    }.taskValue
  )

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
    // a log streamed to the example's demo=logs (LogBacklog): its plain model, their codecs and the server's log
    Compile / unmanagedSourceDirectories ++= Seq(logModelSources, networkSources / "everyui" / "log",
      networkBackend("spice") / "everyui" / "log"),
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
    name := "everyui-tvos-example",   // the example app; everyui-tvos is the published backend
    publish / skip := true,
    everyuiAppleInfoPlist := (ThisBuild / baseDirectory).value / "swift" / "app" / "Info.plist",
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / unmanagedSourceDirectories ++= backendSources("native", spice = false),
    libraryDependencies += "com.outr" %% "spice-core" % spiceVersion,
    emitSwift := {
      val outDir = emitSwiftTask("out-swift", networkLibraries).value
      val root = (ThisBuild / baseDirectory).value
      val swift = root / "swift"
      // the example's app icons (tools/make-icons.py)
      AppleApp.assemble(outDir, Seq(swift / "runtime" / "ScalaRuntime.swift", swift / "runtime" / "UniversalRuntime.swift",
        swift / "runtime" / "MediaRuntime.swift", swift / "app" / "main.swift"), everyuiAppleInfoPlist.value,
        Some(swift / "app" / "Assets.xcassets"), Seq(root / "assets" / "images"), Seq(root / "assets" / "fonts"),
        exampleTranslations(root))
      // demo=webview's pages, in the bundle beside the app as they are beside the web build
      Seq("webview.html", "sandbox.html").foreach(p => IO.copyFile(root / "web" / p, outDir / p))
      // the release and the build's number as Android's example has them (Platform.appVersionName, appVersion)
      AppleApp.version(outDir, version.value, 1)
      outDir
    }
  )

// --- desktop: the JavaFX backend ---------------------------------------------
// JavaFX's retained scene graph (Pane, Text, ImageView) on the JVM — DESIGN.md's
// near-term desktop path, to ship later as a GraalVM native image.
//
//   sbt desktop/run                 a 1920x1080 window
//   tools/check.sh                  freezes a frame under Xvfb and compares it
lazy val javafxVersion = EveryUILibraries.javafxVersion
lazy val javafxPlatform = EveryUILibraries.javafxPlatform
@transient val desktopClasspath = taskKey[File]("Compile the desktop backend and write its runtime classpath to desktop/out/classpath.txt")

lazy val desktop = (project in file("desktop"))
  .dependsOn(reactive)
  .settings(
    name := "everyui-desktop-example",   // the example app; everyui-desktop is the published backend
    publish / skip := true,
    Compile / unmanagedSourceDirectories ++= backendSources("jvm", spice = true),
    Compile / unmanagedSourceDirectories += (file("app") / "desktop" / "src" / "main" / "scala").getAbsoluteFile,   // the example's launcher
    libraryDependencies += "com.outr" %% "spice-client-jvm" % spiceVersion,
    libraryDependencies ++= Seq("base", "graphics", "controls", "media").map(m => ("org.openjfx" % s"javafx-$m" % javafxVersion).classifier(javafxPlatform)),
    // the focus layer checked against real nodes (FocusSpec), on a JavaFX thread of its own
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test,
    Test / fork := true,
    // a spec that finds something not let go of says what holds it (LeakPaths): the classes loaded, and the JDK's own
    // collections, read by reflection
    Test / javaOptions ++= Seq("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED"),
    // the specs share one `ui` and one window, so they take turns
    Test / parallelExecution := false,
    Test / testForkedParallel := false,
    // the bundled faces are found from the build's root, as the app finds them
    Test / baseDirectory := (ThisBuild / baseDirectory).value,
    // the web's choice of which faces words wait for (FaceWait), plain Scala, checked here with the rest
    Test / unmanagedSources += (file("web") / "src" / "main" / "scala" / "everyui" / "render" / "FaceWait.scala").getAbsoluteFile,
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
// The oldest Android the published backend runs on: Android 6, whose televisions are still in living rooms (an app
// builds for it or newer). The example apps are built for it too, so they meet what an older Android lacks: built for
// 11, the example never ran where an unguarded Android 11 call crashed every app at launch on Android 7 to 10.
lazy val androidMinSdk = "23"
lazy val androidLibraryMinSdk = androidMinSdk.toInt
lazy val androidJar = androidSdk / "platforms" / androidPlatform / "android.jar"
@transient val packageApk = taskKey[File]("Compile the Android backend and package it as a debug-signed APK in android/out-apk/")

// androidApiCheck fails the published Android jars, and so their publish, on any reference to a newer class, method or
// field that no Build.VERSION.SDK_INT guard keeps from running there (AndroidApiCheck, in sbt-support), less the
// methods android/api-allowed.txt names with why they are safe.
@transient val androidApiCheck = taskKey[Unit]("Fail on an Android API newer than androidLibraryMinSdk used unguarded")
def androidApiChecked: Seq[Setting[?]] = Seq(
  androidApiCheck := {
    val conv = fileConverter.value
    val _ = (Compile / compile).value
    val classes = (Compile / classDirectory).value
    val others = (Compile / dependencyClasspath).value.map(e => conv.toPath(e.data).toFile)
    val levels = AndroidApiCheck.levels(androidSdk / "platforms" / androidPlatform / "data" / "api-versions.xml")
    val allowed = AndroidApiCheck.allowedIn((ThisBuild / baseDirectory).value / "android" / "api-allowed.txt")
    AndroidApiCheck.require(levels, androidLibraryMinSdk, Seq(classes), others, allowed, streams.value.log.info(_))
  },
  Compile / packageBin := (Compile / packageBin).dependsOn(androidApiCheck).value
)

// Google's back-port of the Java library, dexed into the example apps as an app built for before Android 8 dexes it
// (CONSUMING.md: AndroidApk.Desugar): spice reads java.time as its headers load, which Android 6 and 7 do not have.
// The back-port and the configuration saying what it covers at the same version, as R8 and L8 require.
lazy val desugarJdkLibsVersion = "2.1.5"
def desugarJdkLibs: Def.Initialize[Task[AndroidApk.Desugar]] = Def.task {
  val resolution = dependencyResolution.value
  val into = (ThisBuild / baseDirectory).value / "target" / "desugar"
  def fetch(name: String): File = {
    val module = ModuleID("com.android.tools", name, desugarJdkLibsVersion)
    resolution.retrieve(resolution.wrapDependencyInModule(module), into / name, sbt.util.Logger.Null) match {
      case Right(files) => files.find(f => f.getName.startsWith(name + "-") && f.getName.endsWith(".jar"))
        .getOrElse(sys.error(s"AndroidApk: $name $desugarJdkLibsVersion fetched no jar"))
      case Left(problem) => sys.error(s"AndroidApk: could not fetch $name $desugarJdkLibsVersion: $problem")
    }
  }
  val library = fetch("desugar_jdk_libs")
  val configuration = fetch("desugar_jdk_libs_configuration")
  val config = into / "desugar.json"
  val zip = new java.util.zip.ZipFile(configuration)
  try {
    val entry = Option(zip.getEntry("META-INF/desugar/d8/desugar.json"))
      .getOrElse(sys.error("AndroidApk: no desugar.json in " + configuration.getName))
    IO.write(config, zip.getInputStream(entry).readAllBytes())
  } finally zip.close()
  AndroidApk.Desugar(config, library, Some(configuration))
}

lazy val media3Version = EveryUILibraries.media3Version
lazy val media3Modules = EveryUILibraries.media3Modules

lazy val android = (project in file("android"))
  .settings(
    // reactify compiled here rather than through `reactive`, so its lazy vals are the legacy kind too
    Compile / sourceGenerators += reactifySources.taskValue,
    name := "everyui-android-example",   // the example app; everyui-android is the published backend
    publish / skip := true,
    Compile / unmanagedSourceDirectories ++= backendSources("android", spice = true),
    Compile / unmanagedSourceDirectories += (file("app") / "android" / "src" / "main" / "scala").getAbsoluteFile,   // the example's activity
    libraryDependencies += "com.outr" %% "spice-client-okhttp" % spiceVersion,
    resolvers += "Google" at "https://dl.google.com/dl/android/maven2/",
    // Media3 (ExoPlayer): what a television needs from a player and `MediaPlayer` has no answer to — its own
    // timeouts, a buffer that can be chosen, and a measurement of what the link is carrying. It ships as Android
    // archives, whose classes sbt-everyui opens out of them before anything compiles against them
    // (everyuiAndroidLibraries).
    libraryDependencies ++= media3Modules.map(m =>
      ("androidx.media3" % m % media3Version).intransitive() artifacts Artifact(m, "aar", "aar")),
    // Credential Manager, which passkeys ask (EveryUILibraries.credentials), and Firebase Cloud Messaging, which push
    // notifications ask (EveryUILibraries.firebase: compiled against; without its runtime and configuration, no push)
    libraryDependencies += EveryUILibraries.credentials,
    libraryDependencies ++= EveryUILibraries.firebase,
    // WorkManager, which scheduled work runs on (everyui.background.Work, EveryUILibraries.work), and what it runs with
    libraryDependencies ++= EveryUILibraries.work,
    libraryDependencies ++= Seq(
      "androidx.annotation" % "annotation-jvm" % "1.9.1",
      "com.google.guava" % "guava" % "33.4.0-android"),
    // what Media3's classes reach for that the published backend leaves to the app, as NaboTV adds it: the shrinker
    // fails on a class it cannot find (AsynchronousMediaCodecCallback's CircularIntArray), and Android 12 on queues a
    // decoder's buffers through it
    libraryDependencies += "androidx.collection" % "collection-jvm" % "1.5.0",
    libraryDependencies += ("androidx.core" % "core" % "1.13.1").intransitive() artifacts Artifact("core", "aar", "aar"),
    // the archives' classes are on the classpath by sbt-everyui (everyuiAndroidLibraries), as in an app's build
    Compile / unmanagedJars += Attributed.blank[xsbti.HashedVirtualFileRef](fileConverter.value.toVirtualFile(androidJar.toPath)),
    // lazy vals without a VarHandle, which Android lacks before API 33 (the libraries are rewritten: AndroidRuntime, in sbt-support)
    Compile / scalacOptions ++= Seq("-release", "17", "-Ylegacy-lazy-vals"),
    // the example's home-screen widget ("inbox", everyui.app.InboxWidgets), as an app declares its own: sbt-everyui
    // writes its receiver, its manifest entries, its appwidget-provider and its shrinker rules, and draws it with
    // EveryUI's widget layouts, here from the source (widget-res) rather than out of the everyui-android jar
    everyuiWidgets := Some("everyui.app.InboxWidgets"),
    everyuiWidgetKinds := Seq(AndroidWidget("inbox", "Inbox", Seq("compact", "medium", "large"),
      description = "Unread mail from the accounts you choose", preview = "@mipmap/ic_launcher")),
    everyuiWidgetLayouts := Def.uncached(baseDirectory.value / "widget-res"),
    // what the example runs with the app closed (everyui.app.ExampleBackground, demo=background), as an app declares
    // its own: sbt-everyui adds its meta-data, EveryUI's receiver and job service, and their shrinker rules
    everyuiBackground := Some("everyui.app.ExampleBackground"),
    packageApk := {
      val conv = fileConverter.value
      val buildRoot = (ThisBuild / baseDirectory).value
      val assets = target.value / "apk-assets"
      IO.delete(assets)
      IO.copyDirectory(buildRoot / "assets" / "images", assets / "images")
      (buildRoot / "assets" / "fonts" * "*.ttf").get().foreach(f => IO.copyFile(f, assets / "fonts" / f.getName))
      exampleTranslations(buildRoot).foreach { case (path, f) => IO.copyFile(f, assets / path) }
      AndroidApk.build(streams.value.log.info(_), androidSdk, androidPlatform, androidBuildTools, androidMinSdk, "37",
        // the archives themselves are not dexable: what was opened out of them stands in their place
        AndroidRuntime.classpath((Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile).filterNot(_.getName.endsWith(".aar")),
          androidMinSdk, target.value / "android-runtime", streams.value.log.info(_)),
        everyuiAndroidManifest.value, everyuiAndroidResources.value, assets,
        // the archives' own rules (WorkManager's, Room's, Media3's) come with them (libraries), as Gradle applies them
        (baseDirectory.value / "proguard-rules.pro" +: everyuiWidgetRules.value) ++ everyuiBackgroundRules.value,
        target.value / "apk", baseDirectory.value / "out-apk" / "everyui.apk",
        // the demo app is never uploaded anywhere, so it keeps the throwaway key and a code that need not climb
        version.value, 1, AndroidApk.debug, desugar = Some(desugarJdkLibs.value),
        libraries = everyuiAndroidLibraries.value)
    }
  )

// --- the other backends, published: each with the shared layer compiled in, as everyui-web has it ----------------
// The example apps (desktop, android, roku, tvos below) compile the same sources with the app; these compile them
// alone, for an app's build to depend on (CONSUMING.md). What the sbt plugin takes out of a jar is a resource in it:
// the fonts, and the Roku's runtime, the Swift runtime and the Android shrinker rules.

// JavaFX is `provided`: its jars are one per operating system, so the app names them for its own
// (EveryUILibraries.javafx)
lazy val desktopPublished = (project in file("published/desktop"))
  .settings(published)
  .settings(
    name := "everyui-desktop",
    description := "EveryUI's UI layer over JavaFX, with the network module",
    excludeDependencies := Nil,   // the published reactify
    // with the web view's guest (desktop/web), which runs only where the app adds javafx-web (WebGuest)
    Compile / unmanagedSourceDirectories ++= publishedSources("desktop", "jvm", spice = true) :+ desktopWebSources,
    libraryDependencies ++= Seq(
      "com.outr" %% "reactify" % reactifyVersion,
      "com.outr" %% "spice-client-jvm" % spiceVersion,
      "org.typelevel" %% "fabric-io" % fabricVersion),   // the network module and the store's codec use it
    libraryDependencies ++= EveryUILibraries.javafx("base", "graphics", "controls", "media", "web").map(_ % Provided),
    Compile / resourceGenerators += fontResources.taskValue,
    Compile / resourceGenerators += sharedLicenses.taskValue
  )

// Plain JVM bytecode against android.jar, as a jar rather than an Android archive: the backend has no manifest of its
// own for an archive to carry (its widget layouts are resources in the jar, which sbt-everyui adds to an app's with
// widgets), and an app's build dexes jars (AndroidApk). Its Android archives are
// `provided` for the same reason as JavaFX (EveryUILibraries.android).
lazy val androidPublished = (project in file("published/android"))
  .settings(published)
  .settings(
    name := "everyui-android",
    description := "EveryUI's UI layer over Android's Views, with the network module",
    androidApiChecked,
    excludeDependencies := Nil,
    Compile / unmanagedSourceDirectories ++= publishedSources("android", "android", spice = true),
    libraryDependencies ++= Seq(
      "com.outr" %% "reactify" % reactifyVersion,
      "com.outr" %% "spice-client-okhttp" % spiceVersion,
      "org.typelevel" %% "fabric-io" % fabricVersion),   // the network module and the store's codec use it
    resolvers += EveryUILibraries.google,
    libraryDependencies ++= (EveryUILibraries.android ++ EveryUILibraries.firebase).map(_ % Provided),
    Compile / unmanagedJars += Attributed.blank[xsbti.HashedVirtualFileRef](fileConverter.value.toVirtualFile(androidJar.toPath)),
    Compile / scalacOptions ++= Seq("-release", "17", "-Ylegacy-lazy-vals"),
    Compile / resourceGenerators += fontResources.taskValue,
    Compile / resourceGenerators += sharedLicenses.taskValue,
    Compile / resourceGenerators += bundled("everyui/android", root => Seq(root / "android" / "proguard-rules.pro")).taskValue,
    // the layouts and drawables a home-screen widget is drawn with, under everyui/android/res/ as they are in
    // android/widget-res (sbt-everyui's everyuiWidgetLayouts)
    Compile / resourceGenerators += Def.task {
      val from = (ThisBuild / baseDirectory).value / "android" / "widget-res"
      val dir = (Compile / resourceManaged).value / "everyui" / "android" / "res"
      (from ** "*.xml").get().map { f =>
        val copy = dir / IO.relativize(from, f).getOrElse(f.getName)
        IO.copyFile(f, copy)
        copy
      }
    }.taskValue
  )

// The Roku and the Apple TV run no JVM: an app compiles against these, and the compiler (everyui-compiler) translates
// the app's TASTy with these jars' compiled through (sbt-everyui's everyuiEmitLibraries). -Yretain-trees as the
// example's own compile has it.
lazy val rokuPublished = (project in file("published/roku"))
  .dependsOn(compilerApi)
  .settings(published)
  .settings(
    name := "everyui-roku",
    description := "EveryUI's UI layer over SceneGraph, with the network module, for the compiler",
    excludeDependencies := Nil,
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / unmanagedSourceDirectories ++= publishedSources("roku", "native", spice = false),
    libraryDependencies ++= Seq(
      "com.outr" %% "reactify" % reactifyVersion,
      "com.outr" %% "spice-core" % spiceVersion),
    Compile / resourceGenerators += fontResources.taskValue,
    Compile / resourceGenerators += sharedLicenses.taskValue,
    Compile / resourceGenerators += bundled("everyui/roku/runtime",
      root => (root / "roku" / "runtime" * ("*.brs" | "*.xml")).get()).taskValue
  )

lazy val tvosPublished = (project in file("published/tvos"))
  .dependsOn(compilerApi)
  .settings(published)
  .settings(
    name := "everyui-tvos",
    description := "EveryUI's UI layer over UIKit, with the network module, for the compiler",
    excludeDependencies := Nil,
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / unmanagedSourceDirectories ++= publishedSources("tvos", "native", spice = false),
    libraryDependencies ++= Seq(
      "com.outr" %% "reactify" % reactifyVersion,
      "com.outr" %% "spice-core" % spiceVersion),
    Compile / resourceGenerators += fontResources.taskValue,
    Compile / resourceGenerators += sharedLicenses.taskValue,
    // the runtime, the launcher and the Info.plist template (everyuiSwiftRuntime, everyuiAppleInfoPlist)
    Compile / resourceGenerators += bundled("everyui/swift", root => Seq(root / "swift" / "runtime" / "ScalaRuntime.swift",
      root / "swift" / "runtime" / "UniversalRuntime.swift", root / "swift" / "runtime" / "MediaRuntime.swift",
      root / "swift" / "app" / "main.swift", root / "swift" / "app" / "Info.plist")).taskValue
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
  .aggregate(compiler, compilerApi, reactive, reactiveTest, roku, tvos, desktop, android, webcheck, checks, server, seo,
    web, networkJvm, networkJs, monaco, terminal, sbtEveryui, seoJs,
    desktopPublished, androidPublished, rokuPublished, tvosPublished, monacoDesktopPublished, monacoAndroidPublished,
    terminalDesktopPublished, terminalAndroidPublished)
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
    name := "everyui-network-probe",
    publish / skip := true,
    libraryDependencies += "com.outr" %% "spice-core" % spiceVersion,
    // the probe runs on the JVM with the desktop's backend; emitted, only what the probe reaches is kept
    Compile / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "probe" / "src" / "main" / "scala", networkBackend("jvm"), networkBackend("spice"), logModelSources),
    libraryDependencies += "com.outr" %% "spice-client-jvm" % spiceVersion,
    Compile / scalacOptions ++= Seq("-Yretain-trees"),
    Compile / run / fork := true,
    emitRoku := emitRokuTask("out-roku", "NetworkProbe", networkLibraries).value,
    emitSwift := emitSwiftTask("out-swift", networkLibraries).value
  )

// --- monaco: everyui-monaco, Monaco (VS Code's editor) as EveryUI components -------------------------------------------
// The shared Scala (monaco/src) over an Embed (everyui.component.Embed): in the page on the web, a web view of its own
// on Android and the desktop. Monaco's own files are fetched from npm and checked (project/MonacoAssets.scala), and
// ship as the module's resources under everyui-monaco/<version>/ with the bridge (monaco/assets).
//
//   sbt monaco/publishLocal              everyui-monaco (Scala.js)
//   sbt monacoGallery/galleryBundle      the example gallery for the web, in monaco/gallery/out-web
//   sbt monacoDesktop/run                the gallery on the desktop (JavaFX's web view)
//   sbt monacoDesktop/test               the bridge protocol and controller specs, and Monaco loaded in a web view
//   sbt monacoAndroid/packageApk         the gallery for Android, in monaco/android/out-apk/everyui-monaco.apk
lazy val fabricVersion = "1.30.4"
lazy val monacoSources = (file("monaco") / "src" / "main" / "scala").getAbsoluteFile
lazy val monacoGallerySources = (file("monaco") / "gallery" / "src" / "main" / "scala").getAbsoluteFile
/** The desktop's web guest (FxWebGuest): only where javafx-web is on the classpath. */
lazy val desktopWebSources = (file("desktop") / "web" / "src" / "main" / "scala").getAbsoluteFile
lazy val monacoResources = Def.task {
  MonacoAssets.generate(streams.value.log.info(_), (ThisBuild / baseDirectory).value / "monaco" / "assets",
    (Compile / resourceManaged).value)
}
@transient val galleryBundle = taskKey[File]("Link the Monaco gallery and put it, its page and Monaco's files in monaco/gallery/out-web")

lazy val monaco = (project in file("monaco"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(web)
  .settings(published)
  .settings(
    name := "everyui-monaco",
    description := "Monaco as EveryUI components, for Scala.js",
    libraryDependencies += "org.typelevel" %% "fabric-io" % fabricVersion,
    Compile / resourceGenerators += monacoResources.taskValue
  )

lazy val monacoGallery = (project in file("monaco/gallery"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(monaco)
  .settings(
    name := "everyui-monaco-gallery",
    publish / skip := true,
    Compile / unmanagedSourceDirectories += (file("monaco") / "gallery" / "web" / "src" / "main" / "scala").getAbsoluteFile,
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("everyui.monaco.gallery.GalleryWeb"),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)
      .withESFeatures(_.withESVersion(org.scalajs.linker.interface.ESVersion.ES2021))),
    galleryBundle := {
      val root = (ThisBuild / baseDirectory).value
      val out = baseDirectory.value / "out-web"
      val linked = (Compile / fastLinkJS).value
      val dir = (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
      IO.delete(out)
      IO.copyFile(dir / "main.js", out / "main.js")
      IO.copyFile(baseDirectory.value / "web" / "index.html", out / "index.html")
      (root / "assets" / "fonts" * "*.ttf").get().foreach(f => IO.copyFile(f, out / "fonts" / f.getName))
      val staged = (monaco / Compile / resourceManaged).value
      (monaco / Compile / resources).value.filter(_.getPath.startsWith(staged.getPath))
        .foreach(f => IO.copyFile(f, out / IO.relativize(staged, f).get))
      out
    }
  )

lazy val monacoDesktop = (project in file("monaco/desktop"))
  .dependsOn(reactive)
  .settings(
    name := "everyui-monaco-desktop-gallery",
    publish / skip := true,
    Compile / unmanagedSourceDirectories ++= backendSources("jvm", spice = true) ++ Seq((file("desktop") / "src" / "main" / "scala").getAbsoluteFile, desktopWebSources, monacoSources,
      monacoGallerySources, (file("monaco") / "gallery" / "desktop" / "src" / "main" / "scala").getAbsoluteFile),
    libraryDependencies += "com.outr" %% "spice-client-jvm" % spiceVersion,
    libraryDependencies += "org.typelevel" %% "fabric-io" % fabricVersion,
    libraryDependencies ++= Seq("base", "graphics", "controls", "media", "web").map(m => ("org.openjfx" % s"javafx-$m" % javafxVersion).classifier(javafxPlatform)),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test,
    Compile / resourceGenerators += monacoResources.taskValue,
    Compile / mainClass := Some("everyui.monaco.gallery.GalleryDesktop"),
    Compile / run / fork := true,
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    Test / fork := true,
    Test / parallelExecution := false,
    Test / baseDirectory := (ThisBuild / baseDirectory).value
  )

lazy val monacoAndroid = (project in file("monaco/android"))
  .settings(
    Compile / sourceGenerators += reactifySources.taskValue,
    name := "everyui-monaco-android-gallery",
    publish / skip := true,
    Compile / unmanagedSourceDirectories ++= backendSources("android", spice = true) ++ Seq(
      (file("android") / "src" / "main" / "scala").getAbsoluteFile, monacoSources, monacoGallerySources,
      (file("monaco") / "gallery" / "android" / "src" / "main" / "scala").getAbsoluteFile),
    libraryDependencies += "com.outr" %% "spice-client-okhttp" % spiceVersion,
    libraryDependencies += "org.typelevel" %% "fabric-io" % fabricVersion,
    resolvers += "Google" at "https://dl.google.com/dl/android/maven2/",
    // what the backend compiles against and leaves to the app, as an app adds it (EveryUILibraries.android: Media3,
    // Credential Manager, WorkManager, and androidx's collections and core, which Media3's classes reach for)
    libraryDependencies ++= EveryUILibraries.android ++ EveryUILibraries.firebase,
    Compile / unmanagedJars += Attributed.blank[xsbti.HashedVirtualFileRef](fileConverter.value.toVirtualFile(androidJar.toPath)),
    // lazy vals without a VarHandle, which Android lacks before API 33 (the libraries are rewritten: AndroidRuntime, in sbt-support)
    Compile / scalacOptions ++= Seq("-release", "17", "-Ylegacy-lazy-vals"),
    packageApk := {
      val conv = fileConverter.value
      val buildRoot = (ThisBuild / baseDirectory).value
      val assets = target.value / "apk-assets"
      IO.delete(assets)
      IO.copyDirectory(buildRoot / "assets" / "images", assets / "images")
      (buildRoot / "assets" / "fonts" * "*.ttf").get().foreach(f => IO.copyFile(f, assets / "fonts" / f.getName))
      // Monaco's files, as the web views ask for them (EmbedClient)
      val staged = target.value / "monaco-assets"
      MonacoAssets.generate(streams.value.log.info(_), buildRoot / "monaco" / "assets", staged)
      IO.copyDirectory(staged, assets)
      AndroidApk.build(streams.value.log.info(_), androidSdk, androidPlatform, androidBuildTools, androidMinSdk, "37",
        AndroidRuntime.classpath((Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile).filterNot(_.getName.endsWith(".aar")),
          androidMinSdk, target.value / "android-runtime", streams.value.log.info(_)),
        baseDirectory.value / "AndroidManifest.xml", buildRoot / "android" / "res", assets,
        Seq(buildRoot / "android" / "proguard-rules.pro", baseDirectory.value / "proguard-rules.pro"), target.value / "apk",
        baseDirectory.value / "out-apk" / "everyui-monaco.apk",
        version.value, 1, AndroidApk.debug, desugar = Some(desugarJdkLibs.value),
        libraries = everyuiAndroidLibraries.value)
    }
  )

// --- terminal: everyui-terminal, xterm.js as an EveryUI component -----------------------------------------------------
// The shared Scala (terminal/src) over an Embed, as Monaco is: in the page on the web, a web view of its own on Android
// and the desktop. xterm.js and its fit and web-links addons are fetched from npm and checked
// (project/TerminalAssets.scala), and ship as the module's resources under everyui-terminal/<version>/ with the bridge
// (terminal/assets). Plain Scala over the shared layer: no dependency beyond everyui-web.
//
//   sbt terminal/publishLocal            everyui-terminal (Scala.js)
//   sbt terminalDemo/demoBundle          the demo for the web, in terminal/demo/out-web
//   sbt terminalDesktop/run              the demo on the desktop (JavaFX's web view)
//   sbt terminalDesktop/test             the wire, the UTF-8 stream, and xterm.js driven in a web view
//   sbt terminalAndroid/packageApk       the demo for Android, in terminal/android/out-apk/everyui-terminal.apk
lazy val terminalSources = (file("terminal") / "src" / "main" / "scala").getAbsoluteFile
lazy val terminalDemoSources = (file("terminal") / "demo" / "src" / "main" / "scala").getAbsoluteFile
lazy val terminalResources = Def.task {
  TerminalAssets.generate(streams.value.log.info(_), (ThisBuild / baseDirectory).value / "terminal" / "assets",
    (Compile / resourceManaged).value)
}
@transient val demoBundle = taskKey[File]("Link the terminal demo and put it, its page and xterm.js's files in terminal/demo/out-web")

lazy val terminal = (project in file("terminal"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(web)
  .settings(published)
  .settings(
    name := "everyui-terminal",
    description := "A terminal (xterm.js) as an EveryUI component, for Scala.js",
    Compile / resourceGenerators += terminalResources.taskValue
  )

lazy val terminalDemo = (project in file("terminal/demo"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(terminal)
  .settings(
    name := "everyui-terminal-demo",
    publish / skip := true,
    Compile / unmanagedSourceDirectories += (file("terminal") / "demo" / "web" / "src" / "main" / "scala").getAbsoluteFile,
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("everyui.terminal.demo.TerminalDemoWeb"),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)
      .withESFeatures(_.withESVersion(org.scalajs.linker.interface.ESVersion.ES2021))),
    demoBundle := {
      val root = (ThisBuild / baseDirectory).value
      val out = baseDirectory.value / "out-web"
      val linked = (Compile / fastLinkJS).value
      val dir = (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
      IO.delete(out)
      IO.copyFile(dir / "main.js", out / "main.js")
      IO.copyFile(baseDirectory.value / "web" / "index.html", out / "index.html")
      (root / "assets" / "fonts" * "*.ttf").get().foreach(f => IO.copyFile(f, out / "fonts" / f.getName))
      val staged = (terminal / Compile / resourceManaged).value
      (terminal / Compile / resources).value.filter(_.getPath.startsWith(staged.getPath))
        .foreach(f => IO.copyFile(f, out / IO.relativize(staged, f).get))
      out
    }
  )

lazy val terminalDesktop = (project in file("terminal/desktop"))
  .dependsOn(reactive)
  .settings(
    name := "everyui-terminal-desktop-demo",
    publish / skip := true,
    Compile / unmanagedSourceDirectories ++= backendSources("jvm", spice = true) ++ Seq((file("desktop") / "src" / "main" / "scala").getAbsoluteFile,
      desktopWebSources, terminalSources, terminalDemoSources, (file("terminal") / "demo" / "desktop" / "src" / "main" / "scala").getAbsoluteFile),
    libraryDependencies += "com.outr" %% "spice-client-jvm" % spiceVersion,
    libraryDependencies += "org.typelevel" %% "fabric-io" % fabricVersion,
    libraryDependencies ++= Seq("base", "graphics", "controls", "media", "web").map(m => ("org.openjfx" % s"javafx-$m" % javafxVersion).classifier(javafxPlatform)),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test,
    Compile / resourceGenerators += terminalResources.taskValue,
    Compile / mainClass := Some("everyui.terminal.demo.TerminalDemoDesktop"),
    Compile / run / fork := true,
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    Test / fork := true,
    Test / parallelExecution := false,
    Test / baseDirectory := (ThisBuild / baseDirectory).value
  )

lazy val terminalAndroid = (project in file("terminal/android"))
  .settings(
    Compile / sourceGenerators += reactifySources.taskValue,
    name := "everyui-terminal-android-demo",
    publish / skip := true,
    Compile / unmanagedSourceDirectories ++= backendSources("android", spice = true) ++ Seq(
      (file("android") / "src" / "main" / "scala").getAbsoluteFile, terminalSources, terminalDemoSources,
      (file("terminal") / "demo" / "android" / "src" / "main" / "scala").getAbsoluteFile),
    libraryDependencies += "com.outr" %% "spice-client-okhttp" % spiceVersion,
    libraryDependencies += "org.typelevel" %% "fabric-io" % fabricVersion,
    resolvers += "Google" at "https://dl.google.com/dl/android/maven2/",
    // what the backend compiles against and leaves to the app, as an app adds it (EveryUILibraries.android: Media3,
    // Credential Manager, WorkManager, and androidx's collections and core, which Media3's classes reach for)
    libraryDependencies ++= EveryUILibraries.android ++ EveryUILibraries.firebase,
    Compile / unmanagedJars += Attributed.blank[xsbti.HashedVirtualFileRef](fileConverter.value.toVirtualFile(androidJar.toPath)),
    // lazy vals without a VarHandle, which Android lacks before API 33 (the libraries are rewritten: project/AndroidRuntime.scala)
    Compile / scalacOptions ++= Seq("-release", "17", "-Ylegacy-lazy-vals"),
    packageApk := {
      val conv = fileConverter.value
      val buildRoot = (ThisBuild / baseDirectory).value
      val assets = target.value / "apk-assets"
      IO.delete(assets)
      IO.copyDirectory(buildRoot / "assets" / "images", assets / "images")
      (buildRoot / "assets" / "fonts" * "*.ttf").get().foreach(f => IO.copyFile(f, assets / "fonts" / f.getName))
      // xterm.js's files, as the web views ask for them (EmbedClient)
      val staged = target.value / "terminal-assets"
      TerminalAssets.generate(streams.value.log.info(_), buildRoot / "terminal" / "assets", staged)
      IO.copyDirectory(staged, assets)
      AndroidApk.build(streams.value.log.info(_), androidSdk, androidPlatform, androidBuildTools, androidMinSdk, "37",
        AndroidRuntime.classpath((Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile).filterNot(_.getName.endsWith(".aar")),
          androidMinSdk, target.value / "android-runtime", streams.value.log.info(_)),
        baseDirectory.value / "AndroidManifest.xml", buildRoot / "android" / "res", assets,
        Seq(buildRoot / "android" / "proguard-rules.pro", baseDirectory.value / "proguard-rules.pro"), target.value / "apk",
        baseDirectory.value / "out-apk" / "everyui-terminal.apk",
        version.value, 1, AndroidApk.debug, desugar = Some(desugarJdkLibs.value),
        libraries = everyuiAndroidLibraries.value)
    }
  )

// --- monaco and terminal, published for the desktop and Android ---------------------------------------------------
// Each compiled against its backend's jar (provided: the app depends on the backend itself), with Monaco's or xterm's
// files as resources under everyui-monaco/<version>/ and everyui-terminal/<version>/: read from the classpath on the
// desktop, and taken out into an APK's assets by sbt-everyui's everyuiEmbedAssets on Android.
//
//   sbt monacoDesktopPublished/publishLocal   everyui-monaco-desktop
//   sbt monacoAndroidPublished/publishLocal   everyui-monaco-android
//   sbt terminalDesktopPublished/publishLocal everyui-terminal-desktop
//   sbt terminalAndroidPublished/publishLocal everyui-terminal-android
def embedDesktop(sources: File, resources: Def.Initialize[Task[Seq[File]]]): Seq[Setting[?]] = Seq(
  excludeDependencies := Nil,   // the published reactify, as the backends have it
  libraryDependencies += "com.outr" %% "reactify" % reactifyVersion % Provided,
  Compile / unmanagedSourceDirectories += sources,
  libraryDependencies += "org.typelevel" %% "fabric-io" % fabricVersion,
  libraryDependencies ++= EveryUILibraries.javafx().map(_ % Provided),
  Compile / resourceGenerators += resources.taskValue
)

def embedAndroid(sources: File, resources: Def.Initialize[Task[Seq[File]]]): Seq[Setting[?]] = Seq(
  excludeDependencies := Nil,   // the published reactify, as the backends have it
  libraryDependencies += "com.outr" %% "reactify" % reactifyVersion % Provided,
  Compile / unmanagedSourceDirectories += sources,
  libraryDependencies += "org.typelevel" %% "fabric-io" % fabricVersion,
  resolvers += EveryUILibraries.google,
  libraryDependencies ++= (EveryUILibraries.android ++ EveryUILibraries.firebase).map(_ % Provided),
  Compile / unmanagedJars += Attributed.blank[xsbti.HashedVirtualFileRef](fileConverter.value.toVirtualFile(androidJar.toPath)),
  Compile / scalacOptions ++= Seq("-release", "17", "-Ylegacy-lazy-vals", "-encoding", "UTF-8"),
  Compile / resourceGenerators += resources.taskValue
) ++ androidApiChecked

lazy val monacoDesktopPublished = (project in file("published/monaco-desktop"))
  .dependsOn(desktopPublished % Provided)
  .settings(published)
  .settings(name := "everyui-monaco-desktop",
    description := "Monaco as EveryUI components, over everyui-desktop's web view")
  .settings(embedDesktop(monacoSources, monacoResources))

lazy val monacoAndroidPublished = (project in file("published/monaco-android"))
  .dependsOn(androidPublished % Provided)
  .settings(published)
  .settings(name := "everyui-monaco-android",
    description := "Monaco as EveryUI components, over everyui-android's web view")
  .settings(embedAndroid(monacoSources, monacoResources))

lazy val terminalDesktopPublished = (project in file("published/terminal-desktop"))
  .dependsOn(desktopPublished % Provided)
  .settings(published)
  .settings(name := "everyui-terminal-desktop",
    description := "A terminal (xterm.js) as an EveryUI component, over everyui-desktop's web view")
  .settings(embedDesktop(terminalSources, terminalResources))

lazy val terminalAndroidPublished = (project in file("published/terminal-android"))
  .dependsOn(androidPublished % Provided)
  .settings(published)
  .settings(name := "everyui-terminal-android",
    description := "A terminal (xterm.js) as an EveryUI component, over everyui-android's web view")
  .settings(embedAndroid(terminalSources, terminalResources))
