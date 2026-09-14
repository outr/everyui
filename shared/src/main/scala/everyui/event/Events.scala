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
}
