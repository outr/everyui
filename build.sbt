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
    emitRoku := emitRokuTask("out-roku", "ReactifyTest", reactiveLibrary).value
  )

// ─── the render-node seam: one shared UI layer, two backends ────────────────
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

lazy val root = (project in file("."))
  .aggregate(compiler, compilerApi, reactive, reactiveTest, roku, server)
  .settings(name := "universal-ui", publish / skip := true)
