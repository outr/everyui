package everyui.app

import everyui.Color
import everyui.app.screen.ScreenManager
import everyui.apps.{AppLink, Apps}
import everyui.component.Container
import everyui.focus.Focus
import everyui.motion.{Keyframe, Track}
import everyui.render.Platform
import everyui.ui

/** The app as a TV runs it (a webOS package; any platform): two screens, Back between them and out of the app at the
  * first, its launches, its coming and going and where the focus goes, each logged:
  *
  *   [ui] tv: home shown (kind WebOS, version 24, open=/title/12)
  *   [ui] tv: details shown
  *   [ui] tv: focus Container "Settings"
  *   [ui] tv: launch open=/title/7
  *   [ui] tv: installed netflix true, tv: launched netflix true (a launch with other=netflix, and
  *       target= as its contentTarget, uri= or intent=)
  *   [ui] tv: opened https://example.com/ true (a launch with url=: `Platform.openUrl`, the TV's browser on webOS)
  *   [ui] tv: away, tv: resumed
  *   [ui] tv: screen 960x540 at 2 (as Home shows, and as the window changes size)
  *
  * On webOS, Back at Home leaves the app by the system's own way ("[ui] webOS: leaving by webOSSystem").
  *
  * With `screen=language` it is an app's first launch instead: a language screen (TvLanguageScreen) made under a
  * loading overlay, its screens hidden until it shows, then the overlay taken away, as an app's first screen is. */
object TvDemo extends ScreenManager {
  lazy val home: TvHomeScreen = new TvHomeScreen
  lazy val details: TvDetailScreen = new TvDetailScreen
  lazy val signIn: TvSignInScreen = new TvSignInScreen
  lazy val language: TvLanguageScreen = new TvLanguageScreen

  private lazy val first: Boolean = Platform.launchArg("screen") == "language"
  // where the screens go on a first launch, hidden under the loading overlay until the first is ready
  private lazy val stage: Container = new Container {
    size.width := ui.size.width()
    size.height := ui.size.height()
  }
  // a pulse over the screen while the first is made
  private lazy val loading: Container = new Container {
    size.width := ui.size.width()
    size.height := ui.size.height()
    backgroundColor @= Color.Black
    children += new Container {
      size.width @= 40.0
      size.height @= 40.0
      backgroundColor @= Color.White
      motion @= List(Track(Track.Opacity, 1.6, List(Keyframe(0.0, 0.0), Keyframe(0.5, 1.0), Keyframe(1.0, 0.0))))
    }
  }

  override def host: Container = if (first) stage else ui

  def run(): Unit = {
    Platform.onLaunch { params =>
      Platform.log("tv: launch " + params.toList.sortBy(_._1).map(p => p._1 + "=" + p._2).mkString(" "))
      val target = params.get("target").fold(Map.empty[String, String])(t => Map("contentTarget" -> t))
      params.get("other").foreach(id => open(AppLink(id, target, uri = params.getOrElse("uri", ""),
        intent = params.getOrElse("intent", ""))))
      params.get("url").foreach(url =>
        Platform.openUrl(url, opened => Platform.log("tv: opened " + url + " " + opened.toString)))
    }
    Platform.onAway(() => Platform.log("tv: away"))
    Platform.onResume(() => Platform.log("tv: resumed"))
    Focus.focused.attach(_.foreach(c => Platform.log("tv: focus " + Focus.named(c))))
    Platform.onResize(() => logScreen())
    if (first) {
      ui.children ++= List(stage, loading)
      stage.visible @= false
      run(language)
    }
    else run(home, details, signIn)
  }

  /** The first screen is up: the loading overlay goes, and the screens under it show. */
  def ready(): Unit = if (!stage.visible()) {
    stage.visible @= true
    ui.children -= loading
  }

  /** Another app, `link`'s, asked whether it is installed and then opened, each answer logged. */
  def open(link: AppLink): Unit = Apps.installed(link.id).flatMap { found =>
    Platform.log("tv: installed " + link.id + " " + found.fold("unknown")(_.toString))
    Apps.launch(link)
  }.map(opened => Platform.log("tv: launched " + link.id + " " + opened.toString)).start()

  /** The screen in the app's units and their scale: "[ui] tv: screen 960x540 at 2". */
  def logScreen(): Unit = Platform.log("tv: screen " + math.round(Platform.screenWidth()).toString + "x" +
    math.round(Platform.screenHeight()).toString + " at " + Platform.pixelRatio().toString)
}
