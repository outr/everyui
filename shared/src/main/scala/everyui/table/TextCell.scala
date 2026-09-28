package everyui.table

import everyui.component.TextView
import everyui.component.types.TextAlign
import everyui.list.ListRow
import everyui.theme.Theme
import reactify.*

/** A cell of text: what `text` makes of the item, in the theme's colour or `colour`'s, set at the start of the cell or
  * where `align` says. */
class TextCell[T](text: T => String, align: TextAlign = TextAlign.Start, points: Double = 13.0,
                  colour: T => Option[everyui.Color] = (_: T) => None) extends GridCell[T] {
  val label: TextView = new TextView {
    content := item().map(text).getOrElse("")
    font.size @= points
    color := item().flatMap(colour).getOrElse(Theme.text())
    position.top := (TextCell.this.size.height() - size.height()) / 2.0
  }
  private val inset = ListRow.inset
  label.position.left := {
    val w = size.width()
    val start = if (align == TextAlign.End) w - label.size.width() - inset
      else if (align == TextAlign.Center) (w - label.size.width()) / 2.0 else inset
    ListRow.along(start, label.size.width(), w)
  }
  children += label
}
