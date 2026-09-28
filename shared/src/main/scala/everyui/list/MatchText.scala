package everyui.list

import everyui.component.{Container, TextView}
import everyui.component.types.WhiteSpace
import everyui.layout.HorizontalLayout
import everyui.theme.Theme
import reactify.*

/** A line of text with stretches of it lit (what a search matched), `lead` before it in a quieter colour (a line's
  * number), cut off at `width`. A long line starts a little before its first match, with an ellipsis, so the match
  * shows. Spans are (start, end) pairs, end excluded, in the text's own characters. */
class MatchText(width: => Double, height: => Double, points: Double = 13.0) extends Container {
  val text: Var[String] = own(Var(""))
  val spans: Var[Seq[(Int, Int)]] = own(Var(Nil))
  val lead: Var[String] = own(Var(""))

  private val run = new Container {
    layout @= Some(HorizontalLayout(0.0))
    position.top := (height - size.height()) / 2.0
  }
  private var pool: Vector[TextView] = Vector.empty

  size.width := width
  size.height := height
  size.width.attach(_ => node.setClip(size.width(), size.height()))
  size.height.attach(_ => node.setClip(size.width(), size.height()))
  node.setClip(size.width(), size.height())
  children += run

  private def piece(words: String, lit: Boolean, quiet: Boolean, at: Int): TextView = {
    while (at >= pool.length) pool = pool :+ new TextView {
      whiteSpace @= WhiteSpace.Pre
      font.size @= points
      radius @= 2.0
    }
    val view = pool(at)
    view.content @= words
    view.color @= (if (quiet) Theme.muted() else Theme.text())
    view.backgroundColor @= (if (lit) Theme.accent().withAlpha(0.35) else everyui.Color.Clear)
    view
  }

  private def show(): Unit = {
    val parts = MatchText.pieces(text(), spans())
    val lit = parts.zipWithIndex.map { case ((words, on), i) => piece(words, on, false, i + 1) }
    val views = (if (lead().isEmpty) Vector.empty else Vector(piece(lead() + " ", false, true, 0))) ++ lit
    run.children @= views
  }

  text.attach(_ => show())
  spans.attach(_ => show())
  lead.attach(_ => show())
}

object MatchText {
  /** How far before its first match a long line starts, in characters. */
  val before: Int = 24

  /** `text` cut into its plain and lit stretches, in order; tabs as spaces, and a long start cut short. */
  def pieces(text: String, spans: Seq[(Int, Int)]): Vector[(String, Boolean)] = {
    val line = text.replace('\t', ' ')
    val sorted = spans.map { case (a, b) => (math.max(0, math.min(a, line.length)), math.max(0, math.min(b, line.length))) }
      .filter(s => s._2 > s._1).sortBy(_._1)
    val from = sorted.headOption.map(_._1 - before).filter(_ > 4).getOrElse(0)
    var out = Vector.empty[(String, Boolean)]
    var at = from
    sorted.foreach { case (a, b) =>
      val start = math.max(a, at)
      if (start > at) out = out :+ (line.substring(at, start) -> false)
      if (b > start) out = out :+ (line.substring(start, b) -> true)
      at = math.max(at, b)
    }
    if (at < line.length) out = out :+ (line.substring(at) -> false)
    if (from > 0) out = ("…" -> false) +: out
    out
  }
}
