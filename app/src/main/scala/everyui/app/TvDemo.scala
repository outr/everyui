package everyui.app

import everyui.app.screen.ScreenManager
import everyui.focus.Focus
import everyui.render.Platform

/** The app as a TV runs it (a webOS package; any platform): two screens, Back between them and out of the app at the
  * first, its launches, its coming and going and where the focus goes, each logged:
  *
  *   [ui] tv: home shown (kind WebOS, version 24, open=/title/12)
  *   [ui] tv: details shown
  *   [ui] tv: focus Container "Settings"
  *   [ui] tv: launch open=/title/7
  *   [ui] tv: away, tv: resumed
  *
  * On webOS, Back at Home leaves the app by the system's own way ("[ui] webOS: leaving by webOSSystem"). */
object TvDemo extends ScreenManager {
  lazy val home: TvHomeScreen = new TvHomeScreen
  lazy val details: TvDetailScreen = new TvDetailScreen
  lazy val signIn: TvSignInScreen = new TvSignInScreen

  def run(): Unit = {
    Platform.onLaunch(params =>
      Platform.log("tv: launch " + params.toList.sortBy(_._1).map(p => p._1 + "=" + p._2).mkString(" ")))
    Platform.onAway(() => Platform.log("tv: away"))
    Platform.onResume(() => Platform.log("tv: resumed"))
    Focus.focused.attach(_.foreach(c => Platform.log("tv: focus " + Focus.named(c))))
    run(home, details, signIn)
  }
}
