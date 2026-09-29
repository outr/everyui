package everyui.app

import everyui.component.{Button, ButtonStyle, Column, Component, Container, InputView, RichText, Row, ScrollContainer, TextView}
import everyui.component.types.FontWeight
import everyui.render.Platform
import everyui.text.TextRun
import everyui.theme.Theme
import everyui.ui

/** Letter spacing: Material's type scale, each style's words drawn plain and then spaced as Material spaces them, with
  * a bar under each as wide as it measures, so the spaced one's bar runs past the plain one's by the spacing after
  * each letter. Then a pair of buttons, a paragraph with a run spaced wider than the rest, and a field. Logs
  * `letterspacing <style> <plain> <spaced>` for each style's measured widths, and `letterspacing ok` once built. */
object LetterSpacingDemo {
  private case class Style(name: String, points: Double, weight: FontWeight, spacing: Double)

  // Material 3's type scale, as Flutter's Typography has it
  private val scale: List[Style] = List(
    Style("displaySmall", 36.0, FontWeight.Normal, 0.0),
    Style("headlineSmall", 24.0, FontWeight.Normal, 0.0),
    Style("titleMedium", 16.0, FontWeight.Medium, 0.15),
    Style("titleSmall", 14.0, FontWeight.Medium, 0.1),
    Style("bodyLarge", 16.0, FontWeight.Normal, 0.5),
    Style("bodyMedium", 14.0, FontWeight.Normal, 0.25),
    Style("bodySmall", 12.0, FontWeight.Normal, 0.4),
    Style("labelLarge", 14.0, FontWeight.Medium, 0.1),
    Style("labelMedium", 12.0, FontWeight.Medium, 0.5),
    Style("labelSmall", 11.0, FontWeight.Medium, 0.5)
  )

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val pairs: List[(Style, TextView, TextView)] = scale.map(s => (s, words(s, 0.0), words(s, s.spacing)))
    val rows: List[Component] = pairs.map(p => Column(4.0)(p._2, bar(p._2), p._3, bar(p._3)))
    val plainButton = new Button("Continue", style = ButtonStyle.Filled, points = 14.0)
    val spacedButton = new Button("Continue", style = ButtonStyle.Filled, points = 14.0)
    spacedButton.letterSpacing @= 0.1
    val paragraph = RichText(List(TextRun("A paragraph with "), TextRun("SPACED CAPITALS", weight = 600,
      letterSpacing = Some(2.0)), TextRun(" among its words, the rest at bodyLarge's 0.5.")), 420.0)
    paragraph.font.letterSpacing @= 0.5
    val field = new InputView
    field.text @= "A field at 0.5"
    field.font.size @= 16.0
    field.font.letterSpacing @= 0.5
    field.size.width @= 292.0
    field.size.height @= 40.0
    field.position.left @= 14.0
    field.color := Theme.text()
    val box = new Container {
      size.width @= 320.0
      size.height @= 40.0
      radius @= 8.0
      backgroundColor := Theme.control()
    }
    box.children += field
    val body = Column(14.0)(rows ++ List(Row(12.0, middle = true)(plainButton, spacedButton), paragraph, box)*)
    body.position.left @= 16.0
    body.position.top @= 16.0
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    scroll.content.children += body
    ui.children += scroll
    // once laid out
    Platform.after(0.5, () => {
      pairs.foreach(p => Platform.log("letterspacing " + p._1.name + " " + p._2.size.width().toString + " " +
        p._3.size.width().toString))
      Platform.log("letterspacing button " + plainButton.size.width().toString + " " + spacedButton.size.width().toString)
      Platform.log("letterspacing ok")
      // a frame saved for a check (--freeze with --screenshot on the desktop)
      if (Platform.freezeSeconds() >= 0.0) Platform.frozen()
    })
  }

  private def words(style: Style, spacing: Double): TextView = {
    val text = TextView(style.name + " " + style.spacing.toString, style.points)
    text.font.weight @= style.weight
    text.font.letterSpacing @= spacing
    text
  }

  private def bar(text: TextView): Container = new Container {
    size.width := text.size.width()
    size.height @= 2.0
    backgroundColor := Theme.accent()
  }
}
