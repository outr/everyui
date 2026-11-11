package everyui.app

import everyui.component.{Column, Component, ScrollContainer, TextView}
import everyui.component.types.FontWeight
import everyui.font.{Face, Faces}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui

/** Weights 300 to 800 side by side, to hold a platform's against another's: a line at each in the bundled Open Sans,
  * which has a 400 and a 600 (each weight drawn in the nearer, 600 and up semibold), and a line at each in "Open Sans
  * Weights", a family registered here as an app registers its own: its 400 and 600 from the bundled files, and with
  * `six=1` OpenSans-Light, -Medium, -Bold and -ExtraBold too, which do not ship with EveryUI (put them in the app's
  * `fonts/`, assets/fonts, first). Logs `weights <family> <weight> <file Faces chose>` for each line, and
  * `weights ok` once built. */
object FontWeightsDemo {
  private val family = "Open Sans Weights"
  private val files: List[(Int, String)] = List(300 -> "OpenSans-Light.ttf", 400 -> "OpenSans-Regular.ttf",
    500 -> "OpenSans-Medium.ttf", 600 -> "OpenSans-SemiBold.ttf", 700 -> "OpenSans-Bold.ttf",
    800 -> "OpenSans-ExtraBold.ttf")
  private val weights: List[FontWeight] = List(FontWeight.Light, FontWeight.Normal, FontWeight.Medium,
    FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)

  def run(): Unit = {
    val six = Platform.launchArg("six") == "1"
    files.filter(f => six || f._1 == 400 || f._1 == 600)
      .foreach(f => Faces.register(new Face(family, f._1, f._2, 2048.0, 2189.0, 600.0)))
    ui.backgroundColor := Theme.background()
    val lines: List[Component] = List("Open Sans", family).flatMap { f =>
      val heading = if (f != family) f + " (bundled: 400, 600)" else f + (if (six) " (six faces)" else " (400, 600)")
      TextView(heading, 13.0, Theme.muted()) ::
        weights.map(w => line(f, w))
    }
    val body = Column(6.0)(lines*)
    body.position.left @= 16.0
    body.position.top @= 16.0
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    scroll.content.children += body
    ui.children += scroll
    weights.foreach(w => List("Open Sans", family).foreach(f =>
      Platform.log(s"weights $f ${w.value} ${Faces(f, w.value).file}")))
    Platform.after(0.5, () => {
      Platform.log("weights ok")
      // a frame saved for a comparison (--freeze with --screenshot on the desktop)
      if (Platform.freezeSeconds() >= 0.0) Platform.frozen()
    })
  }

  private def line(f: String, w: FontWeight): TextView = {
    val t = TextView(s"${w.value} The quick brown fox jumps", 22.0)
    t.font.family @= f
    t.font.weight @= w
    t
  }
}
