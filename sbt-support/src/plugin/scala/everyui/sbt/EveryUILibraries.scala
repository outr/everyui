package everyui.sbt

import sbt.*

/** What an EveryUI backend is compiled against but leaves to the app to add, since only the app's build knows the form
  * it needs: JavaFX's jars for the machine the desktop app is built on, and the Android archives an Android app's
  * build opens (Aars). The backends are published with these as `provided`, so none of them comes along by itself. */
object EveryUILibraries {
  val javafxVersion: String = "26.0.2"
  val media3Version: String = "1.9.2"
  val credentialsVersion: String = "1.6.0"
  val workVersion: String = "2.12.0"

  /** The Media3 (ExoPlayer) modules the Android backend plays with: what a television needs from a player and
    * `MediaPlayer` has no answer to — its own timeouts, a buffer that can be chosen, and a measurement of what the
    * link is carrying. The OkHttp data source is the one that fetches the media (ExoMedia says why it is not the
    * default one). */
  val media3Modules: Seq[String] = Seq("media3-common", "media3-container", "media3-database", "media3-datasource",
    "media3-datasource-okhttp", "media3-decoder", "media3-extractor", "media3-exoplayer", "media3-exoplayer-hls")

  /** Google's Maven repository, where the Android archives are. */
  val google: MavenRepository = "Google" at "https://dl.google.com/dl/android/maven2/"

  /** JavaFX's jars are one per operating system: the classifier for the machine the build runs on. */
  def javafxPlatform: String = {
    val os = sys.props("os.name").toLowerCase
    if (os.contains("mac")) "mac" else if (os.contains("win")) "win" else "linux"
  }

  /** JavaFX's `modules` for this machine, base, graphics, controls and media unless others are named: what
    * everyui-desktop draws with. */
  def javafx(modules: String*): Seq[ModuleID] =
    (if (modules.isEmpty) Seq("base", "graphics", "controls", "media") else modules)
      .map(m => ("org.openjfx" % s"javafx-$m" % javafxVersion).classifier(javafxPlatform))

  /** Credential Manager (androidx.credentials), which everyui-android's passkeys ask (`everyui.passkey.Passkeys`). On
    * Android 14 and later it is all they need, handing each request to the system's own Credential Manager; before 14
    * it hands them to Google Play services, through `credentials-play-services-auth`, which an app that signs in with
    * passkeys there adds for itself (CONSUMING.md). An app that never asks for a passkey keeps none of it: the shrinker
    * takes it out. */
  def credentials: ModuleID =
    ("androidx.credentials" % "credentials" % credentialsVersion).intransitive().artifacts(Artifact("credentials", "aar", "aar"))

  /** Firebase Cloud Messaging's archives, which everyui-android's push notifications ask (`everyui.push.Push`) and
    * compiles against, but does not bring: an app that wants push adds these, with the rest of Firebase messaging's
    * runtime, its configuration and its manifest's entries (CONSUMING.md). Without them there is no push on Android. */
  def firebase: Seq[ModuleID] = Seq(
    "com.google.firebase" -> "firebase-messaging" -> "25.1.3", "com.google.firebase" -> "firebase-common" -> "22.0.1",
    "com.google.firebase" -> "firebase-components" -> "19.0.0", "com.google.android.gms" -> "play-services-tasks" -> "18.4.0",
    "com.google.android.gms" -> "play-services-basement" -> "18.9.0",
    "com.google.android.gms" -> "play-services-cloud-messaging" -> "17.4.0").map { case ((group, name), version) =>
    (group % name % version).intransitive().artifacts(Artifact(name, "aar", "aar"))
  }

  /** WorkManager (androidx.work), which everyui-android's scheduled work runs on (`everyui.background.Work`), and what
    * it runs with, each named, none taken transitively, and none the backend has already: Kotlin's standard library
    * and its coroutines, Room and SQLite (where it keeps its work), lifecycle, startup, tracing and
    * concurrent-futures. Left out: what another of these already is (Guava's ListenableFuture for `listenablefuture`,
    * `annotation-jvm` and `collection-jvm` for their roots, the `-jvm` and `-android` builds for theirs), annotations
    * only (`jspecify`, JetBrains' `annotations`), and what only `core` reaches for (`interpolator`,
    * `versionedparcelable`, `profileinstaller`). Its archives' resources are linked and their R classes written by
    * `AndroidApk.build` (`libraries`), which WorkManager reads its own through. */
  def work: Seq[ModuleID] = Seq(
    ("androidx.work", "work-runtime", workVersion, "aar"),
    ("androidx.room", "room-runtime-android", "2.7.0", "aar"),
    ("androidx.room", "room-common-jvm", "2.7.0", "jar"),
    ("androidx.sqlite", "sqlite-android", "2.5.0", "aar"),
    ("androidx.sqlite", "sqlite-framework-android", "2.5.0", "aar"),
    ("androidx.lifecycle", "lifecycle-common", "2.6.2", "jar"),
    ("androidx.lifecycle", "lifecycle-runtime", "2.6.2", "aar"),
    ("androidx.lifecycle", "lifecycle-livedata", "2.6.2", "aar"),
    ("androidx.lifecycle", "lifecycle-livedata-core", "2.6.2", "aar"),
    ("androidx.lifecycle", "lifecycle-service", "2.6.2", "aar"),
    ("androidx.arch.core", "core-common", "2.2.0", "jar"),
    ("androidx.arch.core", "core-runtime", "2.2.0", "aar"),
    ("androidx.startup", "startup-runtime", "1.1.1", "aar"),
    ("androidx.tracing", "tracing", "1.2.0", "aar"),
    ("androidx.tracing", "tracing-ktx", "1.2.0", "aar"),
    ("androidx.concurrent", "concurrent-futures", "1.1.0", "jar"),
    ("androidx.concurrent", "concurrent-futures-ktx", "1.1.0", "jar"),
    ("androidx.annotation", "annotation-experimental", "1.4.1", "aar"),
    ("org.jetbrains.kotlin", "kotlin-stdlib", "2.1.20", "jar"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.9.0", "jar"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.9.0", "jar"),
    ("org.jetbrains.kotlinx", "atomicfu-jvm", "0.17.0", "jar")).map { (group, name, version, kind) =>
    if (kind == "aar") (group % name % version).intransitive().artifacts(Artifact(name, "aar", "aar"))
    else (group % name % version).intransitive()
  }

  /** What everyui-android compiles against beside android.jar: Media3's archives, Credential Manager's and
    * WorkManager's (`work`), each named and none taken transitively (an archive's dependencies are archives too,
    * and a build takes only those it can open), and what their classes reach for: androidx's collections and core
    * among them, without which the shrinker fails on a class it cannot find (Media3's AsynchronousMediaCodecCallback's
    * CircularIntArray). Resolved from `google`. */
  def android: Seq[ModuleID] =
    media3Modules.map(m => ("androidx.media3" % m % media3Version).intransitive().artifacts(Artifact(m, "aar", "aar"))) ++
      Seq(credentials, "androidx.annotation" % "annotation-jvm" % "1.9.1",
        "com.google.guava" % "guava" % "33.4.0-android",
        "androidx.collection" % "collection-jvm" % "1.5.0",
        ("androidx.core" % "core" % "1.13.1").intransitive().artifacts(Artifact("core", "aar", "aar"))) ++ work
}
