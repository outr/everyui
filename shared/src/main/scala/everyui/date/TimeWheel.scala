package everyui.date

import everyui.Color
import everyui.component.types.{FontWeight, SemanticRange, Semantics}
import everyui.component.{Container, TextView}
import everyui.event.{Key, KeyEvent}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** One column of a time picker (`TimePicker`): `count` values, `at` the one chosen, each written by `show`, with the
  * one before over it and the one after under it, as a wheel turned to it shows them; round from the last to the first.
  * Up and Down turn it a value, Page Up and Page Down a sixth of the way round, Home and End to its ends, and digits
  * typed go to the value they begin ("4" to 4, "45" to 45); the pointer's wheel turns it, and a click (a tap) on the
  * value before or after goes to it. A screen reader hears it as a spin button named `label`, at its value. */
class TimeWheel(label: String, count: Int, show: Int => String, val at: Var[Int]) extends Container {
  private val row: Double = 40.0
  private def words(offset: Int, big: Boolean): TextView = new TextView {
    // a wheel of two (morning or afternoon) shows the other on its own side, not on both
    content := (if (count <= 2 && (at() + offset < 0 || at() + offset >= count)) ""
      else show(TimeWheel.round(at() + offset, count)))
    font.size @= (if (big) 28.0 else 18.0)
    font.weight @= (if (big) FontWeight.SemiBold else FontWeight.Normal)
    color := (if (big) Theme.text() else Theme.muted())
  }
  private val before = words(-1, big = false)
  private val now = words(0, big = true)
  private val after = words(1, big = false)

  focusable @= true
  clickFocus @= true
  size.width @= 72.0
  size.height @= row * 3.0
  radius @= 12.0
  backgroundColor := (if (focusShown()) Theme.controlLit() else Theme.raised())
  borderWidth := (if (focusShown()) 2.5 else 0.0)
  borderColor := (if (focusShown()) Theme.accent() else Color.Clear)
  List((before, 0), (now, 1), (after, 2)).foreach { case (t, i) =>
    t.position.left := (size.width() - t.size.width()) / 2.0
    t.position.top := row * i + (row - t.size.height()) / 2.0
  }
  children ++= List(before, now, after)
  semantics := Semantics("spinbutton", label = label,
    range = Some(SemanticRange(at().toDouble, 0.0, (count - 1).toDouble, show(at()))))

  /** Turned `by` values (round the wheel). */
  def turn(by: Int): Unit = at @= TimeWheel.round(at() + by, count)

  event.click.attach { e =>
    val y = e.y - absoluteTop
    // (on a wheel of two, only where the other value is shown)
    if (y < row && (count > 2 || at() > 0)) turn(-1)
    else if (y >= row * 2.0 && (count > 2 || at() < count - 1)) turn(1)
  }
  event.pointer.wheel.attach { e =>
    e.consume()
    if (e.y > 0.0) turn(1) else if (e.y < 0.0) turn(-1)
  }

  override def keepsEvent(e: KeyEvent): Boolean = keyFor(e).nonEmpty || super.keepsEvent(e)
  event.key.down.attach(e => keyFor(e).foreach(_()))

  // digits typed within a moment of each other, and when the last was
  private var typed = ""
  private var typedAt = 0.0

  private def keyFor(e: KeyEvent): Option[() => Unit] =
    if (e.ctrl || e.meta || e.alt) None
    else if (e.key == Key.ArrowUp) Some(() => turn(-1))
    else if (e.key == Key.ArrowDown) Some(() => turn(1))
    else if (e.key == Key.PageUp) Some(() => turn(-math.max(1, count / 6)))
    else if (e.key == Key.PageDown) Some(() => turn(math.max(1, count / 6)))
    else if (e.name == "Home") Some(() => at @= 0)
    else if (e.name == "End") Some(() => at @= count - 1)
    else if (e.name.length == 1 && e.name.charAt(0).isDigit) Some(() => digit(e.name))
    else None

  // the value the digits typed begin, as a number: "4" goes to 4, "45" on within a moment to 45
  private def digit(d: String): Unit = {
    val nowAt = Platform.ticks()
    typed = (if (nowAt - typedAt > 1000.0 || typed.length >= 2) "" else typed) + d
    typedAt = nowAt
    val wanted = typed.toInt
    (0 until count).find(i => show(i).toIntOption.contains(wanted))
      .orElse((0 until count).find(i => show(i).stripPrefix("0").startsWith(typed)))
      .foreach(i => at @= i)
  }

  override def kind: String = "TimeWheel(" + label + ")"
}

object TimeWheel {
  /** `i` round a wheel of `count`. */
  def round(i: Int, count: Int): Int = if (count <= 0) 0 else ((i % count) + count) % count
}
