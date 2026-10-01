package everyui.event

import everyui.component.Component
import everyui.render.Platform

/** When the focus last moved, by `Platform.ticks()`, and what had it before: what a click on a container asks before it
  * takes the focus, since a field inside it takes the focus as the press goes down, before the click that press ends
  * in arrives; and what a menu dropped from a button goes back to, where the press on the button took the focus from
  * a field. */
object FocusMoves {
  /** How long before a click a focus that moved counts as that click's own press having moved it, in milliseconds. */
  val press: Double = 1000.0
  private var at: Double = -1.0e9
  private var watching: Boolean = false
  private var now: Option[Component] = None
  // what had the focus before it last moved: kept a press's length, then let go of
  private var was: Option[Component] = None

  /** Follow the focus from now on (once): the first click channel made asks for it. */
  def watch(): Unit = if (!watching) {
    watching = true
    now = everyui.focus.Focus.focused()
    everyui.focus.Focus.focused.attach { f =>
      was = now
      now = f
      at = Platform.ticks()
      Platform.after(press / 1000.0, () => if (!justMoved) was = None)
    }
  }

  /** Whether the focus moved just now: within a press's length of this moment. */
  def justMoved: Boolean = Platform.ticks() - at < press

  /** What had the focus before it moved, where it moved just now; else None. */
  def before: Option[Component] = if (justMoved) was else None
}
