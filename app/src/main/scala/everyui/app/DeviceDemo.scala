package everyui.app

import everyui.component.{Button, Column, ImageView, Row, TextView}
import everyui.error.Errors
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** What the platform says of the device, and errors thrown on purpose: `demo=device`. The logo at the top left, which
  * on a phone shows whether the app keeps out of the status bar and the camera, the platform and the device, whether a
  * remote drives it, touch, a keyboard, the app's version and build, its language, the system's light or dark, the
  * safe area and the screen; a button that counts its presses, one that throws as it is pressed and one that throws
  * from a timer, which nothing catches but the platform. Each problem is logged as `Errors` hears it:
  *
  *   [ui] device IOS iPhone remote=false touch=true version=0.1.4 (1) safe=62,0,34,0 screen=402x778
  *   [ui] device pressed 1
  *   [ui] device problem uncaught: RuntimeException: thrown from a timer on purpose
  */
object DeviceDemo {
  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    Errors.onProblem(p => Platform.log("device problem " + p.where + ": " + p.message))
    val safe = Platform.safeArea()
    val facts = List(
      s"${Platform.kind} · ${Platform.deviceName()} · ${Platform.deviceModel()}",
      s"remote ${Platform.remoteNavigation()} · touch ${Platform.touchSupport()} · " +
        s"pointer ${Platform.pointerSupport()} · keyboard ${Platform.keyboardSupport()}",
      s"version ${Platform.appVersionName()} (${Platform.appVersion()}) · ${Platform.language()} · " +
        s"${Platform.systemColorScheme()} · ${Platform.timeZone()}",
      s"safe ${safe.top.round},${safe.right.round},${safe.bottom.round},${safe.left.round} · " +
        s"screen ${Platform.screenWidth().round}x${Platform.screenHeight().round} · ratio ${Platform.pixelRatio()}")
    val presses = Var(0)
    val count = TextView("", 14.0, Theme.text())
    count.content := "Pressed " + presses().toString
    val press = new Button("Press me", () => {
      presses @= presses() + 1
      Platform.log("device pressed " + presses().toString)
    })
    val now = new Button("Throw now", () => throw new RuntimeException("thrown from a press on purpose"))
    val later = new Button("Throw in a timer", () =>
      Platform.after(0.1, () => throw new RuntimeException("thrown from a timer on purpose")))
    val column = Column(spacing = 12.0)(
      (ImageView("/images/logo.png", 48.0) :: facts.map(f => TextView(f, 14.0, Theme.text())) :::
        List(Row(spacing = 12.0)(press, count), now, later))*)
    column.position.left @= 16.0
    column.position.top @= 8.0
    ui.children += column
    Platform.log("device " + Platform.kind + " " + Platform.deviceName() + " remote=" + Platform.remoteNavigation() +
      " touch=" + Platform.touchSupport() + " version=" + Platform.appVersionName() + " (" + Platform.appVersion() +
      ") safe=" + List(safe.top, safe.right, safe.bottom, safe.left).map(_.round).mkString(",") + " screen=" +
      Platform.screenWidth().round + "x" + Platform.screenHeight().round)
  }
}
