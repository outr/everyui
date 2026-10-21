package everyui.component

import everyui.Color
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A box that is checked or not, its label beside it and a note under it; a click, a tap or Enter flips it. The focus
  * on it is a ring in the accent, which shows on a dialog as well as on the page. A `compact` one is for a dense tool
  * window: 24 pixels tall, the box and the words smaller, as wide as its label unless given a `width`, the ring still
  * clear of the box and the words. */
class Checkbox(label: String, initial: Boolean = false, note: Option[String] = None, width: Double = Checkbox.width,
               compact: Boolean = false) extends Container {
  val checked: Var[Boolean] = own(Var(initial))
  /** The label's colour; bind it to mark a choice worth noticing. */
  val labelColor: Var[Color] = own(Var(Theme.text()))

  private val glyph: Double = if (compact) 16.0 else 20.0
  private val line: Double = if (compact) 24.0 else 28.0
  private val inset: Double = (line - glyph) / 2.0
  private val start: Double = glyph + inset * 2.0 + (if (compact) 2.0 else 4.0)
  private val box = new MaterialIconView(MaterialIcon.CheckBoxOutlineBlank, glyph) {
    icon := (if (checked()) MaterialIcon.CheckBox else MaterialIcon.CheckBoxOutlineBlank)
    color := (if (checked()) Theme.accent() else Theme.muted())
    position.left @= inset
    position.top @= inset
  }
  private val title = new TextView {
    content @= label
    font.size @= (if (compact) 12.5 else 14.0)
    color := labelColor()
    position.left @= start
    position.top := (line - size.height()) / 2.0
  }
  private val detail = note.map { n =>
    val t = TextView.wrapped(n, width - start - 8.0, if (compact) 10.5 else 11.0, Theme.muted())
    t.position.left @= start
    t.position.top @= line - 4.0
    t
  }

  focusable @= true
  radius @= (if (compact) 4.0 else 6.0)
  // a compact one given no width of its own is as wide as its label, with room for the ring past its end
  if (compact && width == Checkbox.width) size.width := start + title.size.width() + 8.0 else size.width @= width
  size.height := line + detail.map(_.size.height() + 4.0).getOrElse(0.0)
  backgroundColor := (if (focusShown() || event.pointer.over()) Theme.controlLit() else Color.Clear)
  borderWidth := (if (focusShown()) (if (compact) 2.0 else 2.5) else 0.0)
  borderColor := Theme.accent()
  children ++= List(box, title)
  detail.foreach(d => children += d)
  tapSlop @= true
  event.click.attach { e =>
    e.consume()
    checked @= !checked()
  }
}

object Checkbox {
  /** How wide a checkbox is unless it says (a compact one is as wide as its label). */
  val width: Double = 400.0
}
