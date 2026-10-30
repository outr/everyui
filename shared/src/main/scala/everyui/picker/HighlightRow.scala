package everyui.picker

import everyui.component.*
import everyui.component.types.{FontWeight, WhiteSpace}
import everyui.list.ListRow
import everyui.text.TextRun
import everyui.theme.Theme
import reactify.*

/** A picker's row: the item's `text` with the runs `spans` names lit in the accent and bold (what the query matched),
  * and its `detail` after it, muted (a file's folder, a symbol's kind). The one in use (`ListRow.inUse`) is in the
  * accent, in its ring.
  *
  * `leading` puts something before the words (an icon, a workspace's tile), centred on the row's line: made once for
  * the row and handed its item, which it binds, since a row is handed one item after another as a list scrolls. */
class HighlightRow[T](text: T => String, spans: T => List[(Int, Int)], detail: T => String = (_: T) => "",
                      points: Double = 14.0, leading: Option[Val[Option[T]] => Component] = None)
  extends ListRow[T] {
  private val lead: Option[Component] = leading.map(make => make(item))
  private val label = new RichText {
    whiteSpace @= WhiteSpace.NoWrap
    font.size @= points
    // the one in use in the accent, its ring and ground the row's own (`ListRow.inUse`)
    color := (if (inUse()) Theme.accent() else Theme.text())
    runs := item().map(t => HighlightRow.runs(text(t), spans(t), points)).getOrElse(Nil)
  }
  private val note = new TextView {
    // (on the row's one line, cut at its end: the tip has it whole)
    textOverflow @= everyui.component.types.TextOverflow.Ellipsis
    font.size @= points * 0.9
    color := Theme.muted()
    content := item().map(detail).getOrElse("")
  }

  lead.foreach { l =>
    l.position.left @= ListRow.inset
    l.position.top := (size.height() - l.size.height()) / 2.0
  }
  label.position.left := lead.map(l => ListRow.inset + l.size.width() + points * 0.6).getOrElse(ListRow.inset)
  label.position.top := (size.height() - label.size.height()) / 2.0
  note.position.left := label.position.left() + label.size.width() + points * 0.8
  note.position.top := (size.height() - note.size.height()) / 2.0
  size.width.attach(_ => node.setClip(size.width(), size.height()))
  children ++= lead.toList ++ List(label, note)
  // a picker's row is a line tall, its words cut at its end: where they run past it, they are read whole in its tip
  tip := item().filter(_ => note.position.left() + note.size.width() > size.width() - ListRow.inset)
    .map(t => (text(t) :: List(detail(t)).filter(_.nonEmpty)).mkString("  "))
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
