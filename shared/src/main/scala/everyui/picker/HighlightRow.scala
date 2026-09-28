package everyui.picker

import everyui.component.*
import everyui.component.types.{FontWeight, WhiteSpace}
import everyui.list.ListRow
import everyui.text.TextRun
import everyui.theme.Theme
import reactify.*

/** A picker's row: the item's `text` with the runs `spans` names lit in the accent and bold (what the query matched),
  * and its `detail` after it, muted (a file's folder, a symbol's kind). */
class HighlightRow[T](text: T => String, spans: T => List[(Int, Int)], detail: T => String = (_: T) => "",
                      points: Double = 14.0) extends ListRow[T] {
  private val label = new RichText {
    whiteSpace @= WhiteSpace.NoWrap
    font.size @= points
    runs := item().map(t => HighlightRow.runs(text(t), spans(t), points)).getOrElse(Nil)
  }
  private val note = new TextView {
    font.size @= points * 0.9
    color := Theme.muted()
    content := item().map(detail).getOrElse("")
  }

  label.position.left @= ListRow.inset
  label.position.top := (size.height() - label.size.height()) / 2.0
  note.position.left := label.position.left() + label.size.width() + points * 0.8
  note.position.top := (size.height() - note.size.height()) / 2.0
  size.width.attach(_ => node.setClip(size.width(), size.height()))
  children ++= List(label, note)
}

object HighlightRow {
  /** `text` as runs, those in `spans` lit. */
  def runs(text: String, spans: List[(Int, Int)], points: Double): List[TextRun] = {
    val lit = Theme.accent().toHex
    var out: List[TextRun] = Nil
    var at = 0
    spans.foreach { case (start, end) =>
      val s = math.max(at, math.min(start, text.length))
      val e = math.max(s, math.min(end, text.length))
      if (s > at) out = TextRun(text.substring(at, s)) :: out
      if (e > s) out = TextRun(text.substring(s, e), weight = FontWeight.Bold.value, color = lit) :: out
      at = e
    }
    if (at < text.length) out = TextRun(text.substring(at)) :: out
    out.reverse
  }
}
