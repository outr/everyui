package everyui.component

import everyui.Color
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A box that is checked or not, its label beside it and a note under it; a click, a tap or Enter flips it. */
class Checkbox(label: String, initial: Boolean = false, note: Option[String] = None, width: Double = 400.0) extends Container {
  val checked: Var[Boolean] = Var(initial)
  /** The label's colour; bind it to mark a choice worth noticing. */
  val labelColor: Var[Color] = Var(Theme.text())

  private val box = new MaterialIconView(MaterialIcon.CheckBoxOutlineBlank, 20.0) {
    icon := (if (checked()) MaterialIcon.CheckBox else MaterialIcon.CheckBoxOutlineBlank)
    color := (if (checked()) Theme.accent() else Theme.muted())
    position.left @= 4.0
    position.top @= 4.0
  }
  private val title = new TextView {
    content @= label
    font.size @= 14.0
    color := labelColor()
    position.left @= 32.0
    position.top @= 4.0
  }
  private val detail = note.map { n =>
    val t = TextView.wrapped(n, width - 40.0, 11.0, Theme.muted())
    t.position.left @= 32.0
    t.position.top @= 24.0
    t
  }

  focusable @= true
  radius @= 6.0
  size.width @= width
  size.height := 28.0 + detail.map(_.size.height() + 4.0).getOrElse(0.0)
  backgroundColor := (if (focused() || event.pointer.over()) Theme.raised() else Color.Clear)
  children ++= List(box, title)
  detail.foreach(d => children += d)
  event.click.attach { e =>
    e.consume()
    checked @= !checked()
  }
}
