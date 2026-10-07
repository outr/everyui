package everyui.app

import everyui.Color
import everyui.component.{Container, TextView}
import everyui.component.types.{FontWeight, WhiteSpace}
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.ui

/** Wrapped words against the height laid out for them: each case drawn over a box as wide as its width and as tall
  * as its measured height, under a line saying what it is, so a look at the screen shows at once whether the words run
  * past their box (measured short) or leave it half empty (measured long). The cases are NaboTV's trivia question at
  * 26 bold in 560 (two lines, five, and a quotation after a blank line), then sizes from 18 to 40, regular and bold,
  * in 300 to 900, a word wider than its line, lines broken by the text and spaced at 1.6. `page=1` (the default) to
  * `page=3` shows a third of them, in Open Sans, a face of the channel's own as an app sets one (`family=Roboto` where the
  * channel ships it); every case is logged on each:
  *
  *   [ui] textmeasure <case> <size> <weight> in <width>: <height> (<height / size> sizes)
  */
object TextMeasureDemo {
  private case class Case(name: String, text: String, size: Double, bold: Boolean, width: Double, spacing: Double = 0.0)

  private val paranormal = "Paranormal investigators Ed and Lorraine Warren work to help a family terrorized by a " +
    "dark presence in their farmhouse. Forced to confront a powerful entity, the Warrens find themselves caught in " +
    "the most terrifying case of their lives."
  private val outlaw = "The 'outlaw country' movement of the 1970s is most associated with which pair of artists?"
  private val quote = "Which book is this famous line from?\n\n\"There is no greater agony than bearing an untold " +
    "story inside you.\""
  private val note = "Waylon Jennings and Willie Nelson led the outlaw country movement against the polished " +
    "Nashville sound, with records made their own way and sold on their own terms."

  private val pages: List[List[Case]] = List(
    List(
      Case("outlaw", outlaw, 26.0, true, 560.0),
      Case("paranormal", paranormal, 26.0, true, 560.0),
      Case("quote", quote, 26.0, true, 560.0)
    ),
    List(
      Case("paranormal22", paranormal, 22.0, true, 560.0),
      Case("note18", note + " " + note, 18.0, false, 400.0),
      Case("outlaw30", outlaw + " " + outlaw, 30.0, false, 700.0),
      Case("short34", "Who performs this song?", 34.0, true, 420.0)
    ),
    List(
      Case("outlaw40", outlaw, 40.0, true, 900.0),
      Case("longword", "A word longer than its line: Supercalifragilisticexpialidociousantidisestablishmentarianism, " +
        "then more words after it.", 22.0, false, 300.0),
      Case("breaks", "One\nTwo, and a line long enough to wrap at this width\n\nFour, after a blank line", 22.0, false,
        420.0),
      Case("spaced", note, 20.0, false, 560.0, 1.6)
    )
  )

  def run(): Unit = {
    val family = if (Platform.launchArg("family").nonEmpty) Platform.launchArg("family") else "Open Sans"
    ui.backgroundColor @= Color.fromHex("#101820")
    val page = math.max(1, math.min(pages.size, Platform.launchArg("page").toIntOption.getOrElse(1)))
    val built = pages.map(_.map(c => (c, words(c, family))))
    val column = new Container {
      layout @= Some(VerticalLayout(10.0, false))
      position.left @= 16.0
      position.top @= 12.0
    }
    built(page - 1).foreach { b =>
      val title = TextView(b._1.name + " " + b._1.size.toInt.toString + (if (b._1.bold) " bold" else "") + " in " +
        b._1.width.toInt.toString, 12.0, Color.fromHex("#8EA4BD"))
      title.size.height @= 16.0
      column.children ++= List(title, boxed(b._2))
    }
    ui.children += column
    // the heights as laid out, and again once drawn
    def report(when: String): Unit = built.flatten.foreach { b =>
      val h = b._2.size.height()
      Platform.log("textmeasure " + when + " " + b._1.name + " " + b._1.size.toString + " " +
        (if (b._1.bold) "bold" else "regular") + " in " + b._1.width.toString + ": " + h.toString + " (" +
        (math.round(h / b._1.size * 100.0) / 100.0).toString + " sizes)")
    }
    Platform.after(0.5, () => {
      report("laid")
      Platform.after(2.0, () => {
        report("drawn")
        Platform.log("textmeasure ok")
        if (Platform.freezeSeconds() >= 0.0) Platform.frozen()
      })
    })
  }

  private def words(c: Case, family: String): TextView = new TextView {
    content @= c.text
    font.family @= family
    whiteSpace @= WhiteSpace.Normal
    font.size @= c.size
    font.weight @= (if (c.bold) FontWeight.Bold else FontWeight.Normal)
    if (c.spacing > 0.0) font.lineHeight @= c.spacing
    color @= Color.fromHex("#EAF1FA")
    size.width @= c.width
  }

  /** The words over a box of the size measured for them. */
  private def boxed(t: TextView): Container = new Container {
    size.width := t.size.width()
    size.height := t.size.height()
    backgroundColor @= Color.fromHex("#2A5A8A")
    children += t
  }
}
