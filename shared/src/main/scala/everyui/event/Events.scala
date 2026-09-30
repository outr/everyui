package everyui.event

import everyui.component.Component
import everyui.focus.Focus
import reactify.*

/** youi's `Events`, over the render seam: the pointer at the component, the keys while it has the focus, and
  * `click` — a pointer click, or Enter while focused. */
class Events(component: Component) {
  lazy val pointer: PointerEvents = new PointerEvents(component)
  lazy val key: KeyEvents = new KeyEvents

  /** A click focuses the focusable component it lands on, the innermost one: a list does not take the focus from a
    * field in one of its rows that the same click focused, or that the press focused as it went down. */
  lazy val click: Channel[PointerEvent] = {
    val channel = pointer.channel(PointerKind.Click)
    FocusMoves.watch()
    channel.attach { e =>
      if (component.focusable() && !Pointer.focusedBy(e) && !heldInside) {
        component.focus()
        if (Focus.focused().contains(component)) Pointer.focused(e)
      }
    }
    channel
  }

  // the focus on something inside this, put there just now: a field takes it natively as the press goes down
  private def heldInside: Boolean = FocusMoves.justMoved &&
    Focus.focused().exists(f => (f ne component) && Focus.within(f, component))

  /** OK held down on it, for half a second: what a remote has for a long press, where a context menu is. A
    * component that listens for it is activated when OK is RELEASED rather than pressed — a short press clicks as
    * it lets go, a long one holds and clicks nothing — while everything else keeps clicking on the press. */
  lazy val hold: Channel[Unit] = {
    holdable = true
    Channel[Unit]
  }

  /** Whether anything listens for `hold`. */
  var holdable: Boolean = false
}
