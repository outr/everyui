package everyui.event

import everyui.component.Component
import everyui.focus.Focus
import reactify.*

/** youi's `Events`, over the render seam: the pointer at the component, the keys while it has the focus, and
  * `click` — a pointer click, or Enter while focused. */
class Events(component: Component) {
  lazy val pointer: PointerEvents = new PointerEvents(component)
  lazy val key: KeyEvents = new KeyEvents

  /** A click is the innermost focusable component's it lands on: a list does not take the focus from a field in one of
    * its rows that the same click focused, or that the press focused as it went down. It focuses it where a remote
    * moves the focus or it takes the focus from a click (`Component.clickFocus`: a field, a list); elsewhere (a
    * button, a tab) the focus is let go of (`Focus.clicked`). */
  lazy val click: Channel[PointerEvent] = {
    component.clickHeard = true
    val channel = pointer.channel(PointerKind.Click)
    FocusMoves.watch()
    channel.attach { e =>
      if (component.focusable() && !Pointer.focusedBy(e) && !heldInside) {
        val takes = Focus.clickFocuses(component)
        Focus.clicked(component, takes)
        if (takes) component.focus()
        if (!takes || Focus.focused().contains(component)) Pointer.focused(e)
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
