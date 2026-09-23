package everyui.event

import everyui.component.Component
import reactify.*

/** youi's `Events`, over the render seam: the pointer at the component, the keys while it has the focus, and
  * `click` — a pointer click, or Enter while focused. */
class Events(component: Component) {
  lazy val pointer: PointerEvents = new PointerEvents(component)
  lazy val key: KeyEvents = new KeyEvents

  lazy val click: Channel[PointerEvent] = {
    val channel = pointer.channel(PointerKind.Click)
    channel.attach(_ => if (component.focusable()) component.focus())
    channel
  }

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
