package everyui.app

import everyui.component.{Row, TextView}
import everyui.component.types.WhiteSpace
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui

/** Words cut short to fit (`TextView.maxWidth` and `maxLines`) where they were made before they were on the screen: a
  * row of five names in Open Sans, 11 points, no wider than 80, made off the screen and put on it together; the same in
  * Roboto Mono, a face the page loads only once something is drawn in it; a paragraph 220 wide cut to two lines, made
  * off the screen; and a row made a second later. On the web such words were measured before they could be (off the
  * page, or in a face still loading), and drawn whole, past their width. Logs each one's laid-out width and what it
  * draws:
  *
  *   [ui] textcut open 80x<height> "A fairly long…" …
  */
object TextCutDemo {
  private val name = "A fairly long name · 180 eps"
  private val synopsis = "A family trip becomes an unexpected adventure when the children find an old map in their " +
    "grandfather's attic and set out to find a treasure lost a hundred years ago."

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    def cut(family: String): TextView = new TextView {
      content @= name
      font.family @= family
      font.size @= 11.0
      color := Theme.text()
      maxWidth @= 80.0
    }
    // made before the rows that hold them, and the rows before the screen
    val open = (1 to 5).map(_ => cut("Open Sans"))
    val mono = (1 to 5).map(_ => cut("Roboto Mono"))
    val paragraph = new TextView {
      content @= synopsis
      whiteSpace @= WhiteSpace.Normal
      maxLines @= 2
      font.family @= "Open Sans"
      font.size @= 14.0
      color := Theme.text()
      size.width @= 220.0
      position.left @= 40.0
      position.top @= 120.0
    }
    val first = Row(spacing = 12.0)(open*)
    first.position.left @= 40.0
    first.position.top @= 40.0
    val second = Row(spacing = 12.0)(mono*)
    second.position.left @= 40.0
    second.position.top @= 80.0
    ui.children ++= List(first, second, paragraph)
    val late = (1 to 5).map(_ => cut("Open Sans"))
    Platform.after(1.0, () => {
      val row = Row(spacing = 12.0)(late*)
      row.position.left @= 40.0
      row.position.top @= 200.0
      ui.children += row
    })
    def box(t: TextView): String =
      t.size.width().round.toString + "x" + t.size.height().round.toString + " \"" + t.node.getText() + "\""
    Platform.after(4.0, () => {
      Platform.log("textcut open " + open.map(box).mkString(" ") + " mono " + mono.map(box).mkString(" ") +
        " late " + late.map(box).mkString(" ") + " paragraph " + box(paragraph))
      if (Platform.freezeSeconds() >= 0.0) Platform.frozen()
    })
  }
}
