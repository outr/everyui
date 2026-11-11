package everyui.app

import everyui.clipboard.Clipboard
import everyui.component.{Button, Column, ImageView, Row, TextView}
import everyui.component.types.WhiteSpace
import everyui.error.Errors
import everyui.render.Platform
import everyui.theme.{Theme, UiScale}
import everyui.ui
import reactify.*

/** What the platform says of the device, and errors thrown on purpose: `demo=device`. The logo at the top left, which
  * on a phone shows whether the app keeps out of the status bar and the camera, the platform and the device, whether a
  * remote drives it, touch, a keyboard, the app's version and build, its language, the system's light or dark, the
  * safe area and the screen; a button that counts its presses, one that throws as it is pressed and one that throws
  * from a timer, which nothing catches but the platform; the system's services: a copy and a paste through the
  * clipboard (Copy, Copy rich, Paste), a link opened (Open everyui.com, and Open a scheme nothing has), the text size
  * the device asks for as it changes, an interpolated string's tab and line break, and a setting and a secret kept
  * across launches (the launches counted). Each problem is logged as `Errors` hears it:
  *
  *   [ui] device IOS iPhone remote=false touch=true version=0.1.4 (1) safe=62,0,34,0 screen=402x778
  *   [ui] device pressed 1
  *   [ui] device problem uncaught: RuntimeException: thrown from a timer on purpose
  *   [ui] device pasted copied\tfrom EveryUI 1
  *   [ui] device text scale 1.0980392156862746
  *   [ui] device launches 2 secret before "secret 1" kept true
  */
object DeviceDemo {
  private def hundredths(d: Double): String = ((d * 100.0).round / 100.0).toString

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    Errors.onProblem(p => Platform.log("device problem " + p.where + ": " + p.message))
    val safe = Platform.safeArea()
    // a setting and a secret kept across launches: the launches counted, the secret read back as it was kept
    val kept = Platform.setting("device.launches")
    val launches = (if (kept.isEmpty) 0 else kept.toInt) + 1
    Platform.saveSetting("device.launches", launches.toString)
    val secretBefore = Platform.secret("device.secret")
    Platform.saveSecret("device.secret", "secret " + launches.toString)
    val secretKept = Platform.secret("device.secret") == "secret " + launches.toString
    Platform.log("device launches " + launches.toString + " secret before \"" + secretBefore + "\" kept " +
      secretKept.toString)
    val facts = List(
      s"${Platform.kind} · ${Platform.deviceName()} · ${Platform.deviceModel()}",
      s"remote ${Platform.remoteNavigation()} · touch ${Platform.touchSupport()} · " +
        s"pointer ${Platform.pointerSupport()} · keyboard ${Platform.keyboardSupport()}",
      s"version ${Platform.appVersionName()} (${Platform.appVersion()}) · ${Platform.language()} · " +
        s"${Platform.systemColorScheme()} · ${Platform.timeZone()}",
      s"safe ${safe.top.round},${safe.right.round},${safe.bottom.round},${safe.left.round} · " +
        s"screen ${Platform.screenWidth().round}x${Platform.screenHeight().round} · ratio ${Platform.pixelRatio()}",
      s"launches $launches · secret ${if (secretKept) "kept" else "not kept"} · before \"$secretBefore\"")
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
    // an interpolated string's escapes, as the emitters take them: a tab and a line break shown, its codes logged
    val escaped = s"tab:\t${presses() + 2} after it\nnext line"
    val shown = TextView(escaped, 14.0, Theme.text())
    shown.whiteSpace @= WhiteSpace.Pre
    Platform.log("device interp " + escaped.map(_.toInt).mkString(","))
    // the text size the device asks for, as it changes
    val scale = TextView("", 14.0, Theme.text())
    scale.content := "text scale " + hundredths(UiScale.system()) + " · drawn at " + hundredths(UiScale())
    Platform.watchTextScale(s => Platform.log("device text scale " + s.toString))
    Platform.log("device text scale " + Platform.systemTextScale().toString)
    // a copy and a paste through the clipboard, plain and rich
    val copies = Var(0)
    val pasted = TextView("", 14.0, Theme.text())
    pasted.content @= "clipboard " + (if (Clipboard.copies) "on" else "none")
    val copy = new Button("Copy", () => {
      copies @= copies() + 1
      Clipboard.copy(s"copied\tfrom EveryUI ${copies()}")
    })
    val rich = new Button("Copy rich", () => Clipboard.copyMarkdown("**bold** and *leaning* words"))
    val paste = new Button("Paste", () => Clipboard.read().map { t =>
      val words = t.getOrElse("")
      pasted.content @= "pasted " + t.map(w => "\"" + w + "\"").getOrElse("nothing")
      Platform.log("device pasted " + words.replace("\t", "\\t"))
    }.start())
    val open = new Button("Open everyui.com", () => Platform.openUrl("https://everyui.com"))
    val nowhere = new Button("Open a scheme nothing has", () => Platform.openUrl("everyui-nothing://here"))
    val column = Column(spacing = 12.0)(
      (ImageView("/images/logo.png", 48.0) :: facts.map(f => TextView(f, 14.0, Theme.text())) :::
        List(Row(spacing = 12.0)(press, count), now, later, shown, scale, Row(spacing = 12.0)(copy, rich, paste),
          pasted, open, nowhere))*)
    column.position.left @= 16.0
    column.position.top @= 8.0
    ui.children += column
    Platform.log("device " + Platform.kind + " " + Platform.deviceName() + " remote=" + Platform.remoteNavigation() +
      " touch=" + Platform.touchSupport() + " version=" + Platform.appVersionName() + " (" + Platform.appVersion() +
      ") safe=" + List(safe.top, safe.right, safe.bottom, safe.left).map(_.round).mkString(",") + " screen=" +
      Platform.screenWidth().round + "x" + Platform.screenHeight().round)
  }
}
