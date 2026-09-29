package everyui.app

import everyui.component.{Button, ButtonStyle, Column, Component, Container, InputView, RichText, Row, ScrollContainer, TextView}
import everyui.render.Platform
import everyui.text.TextRun
import everyui.theme.Theme
import everyui.ui

/** Italic: an empty list's and a search's labels drawn upright and then slanted, each over a bar as wide as it
  * measures; then a paragraph slanted whole with a bold run in it, a pair of buttons and a field. Logs
  * `italic <words> <upright> <slanted>` for each label's measured widths, and `italic ok` once built. */
object ItalicDemo {
  // the words, their size, and a family of their own ("" for the theme's): Open Sans has no italic bundled
  private val labels: List[(String, Double, String)] = List(("-- No Results --", 14.0, ""), ("Searching…", 14.0, ""),
    ("Nothing here yet", 20.0, ""), ("Slanted headline", 32.0, ""), ("Open Sans, slanted by the platform", 20.0, "Open Sans"))

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val pairs: List[(String, TextView, TextView)] = labels.map(l => (l._1, words(l, false), words(l, true)))
    val rows: List[Component] = pairs.map(p => Column(4.0)(p._2, bar(p._2), p._3, bar(p._3)))
    val upright = new Button("Retry", style = ButtonStyle.Filled, points = 14.0)
    val slanted = new Button("Retry", style = ButtonStyle.Filled, points = 14.0)
    slanted.italic @= true
    val paragraph = RichText(List(TextRun("A paragraph slanted whole, with "), TextRun("a bold run", weight = 600),
      TextRun(" among its words, the whole of it wrapped to the width it measures.")), 420.0)
    paragraph.font.italic @= true
    paragraph.size.width := math.min(420.0, ui.size.width() - 32.0)
    val field = new InputView
    field.text @= "A slanted field"
    field.font.size @= 16.0
    field.font.italic @= true
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
    val body = Column(14.0)(rows ++ List(Row(12.0, middle = true)(upright, slanted), paragraph, box)*)
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
      pairs.foreach(p => Platform.log("italic " + p._1 + " " + p._2.size.width().toString + " " +
        p._3.size.width().toString))
      Platform.log("italic button " + upright.size.width().toString + " " + slanted.size.width().toString)
      Platform.log("italic ok")
      // a frame saved for a check (--freeze with --screenshot on the desktop)
      if (Platform.freezeSeconds() >= 0.0) Platform.frozen()
    })
  }

  private def words(label: (String, Double, String), italic: Boolean): TextView = {
    val text = TextView(label._1, label._2)
    if (label._3.nonEmpty) text.font.family @= label._3
    text.color := Theme.muted()
    text.font.italic @= italic
    text
  }

  private def bar(text: TextView): Container = new Container {
    size.width := text.size.width()
    size.height @= 2.0
    backgroundColor := Theme.accent()
  }
}
