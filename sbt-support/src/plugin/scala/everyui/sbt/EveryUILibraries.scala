package everyui.sbt

import sbt.*

/** What an EveryUI backend is compiled against but leaves to the app to add, since only the app's build knows the form
  * it needs: JavaFX's jars for the machine the desktop app is built on, and the Android archives an Android app's
  * build opens (Aars). The backends are published with these as `provided`, so none of them comes along by itself. */
object EveryUILibraries {
  val javafxVersion: String = "26.0.2"
  val media3Version: String = "1.9.2"

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

  /** What everyui-android compiles against beside android.jar: Media3's archives, each named and none taken
    * transitively (an archive's dependencies are archives too, and a build takes only those it can open), and what
    * their classes reach for. Resolved from `google`. */
  def android: Seq[ModuleID] =
    media3Modules.map(m => ("androidx.media3" % m % media3Version).intransitive().artifacts(Artifact(m, "aar", "aar"))) ++
      Seq("androidx.annotation" % "annotation-jvm" % "1.9.1", "com.google.guava" % "guava" % "33.4.0-android")
}
