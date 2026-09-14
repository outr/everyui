package everyui.event

import everyui.component.Component
import reactify.*

/** The pointer at a component: each channel listens on the node only once something asks for it. */
class PointerEvents(component: Component) {
  lazy val down: Channel[PointerEvent] = channel("down")
  lazy val up: Channel[PointerEvent] = channel("up")
  lazy val move: Channel[PointerEvent] = channel("move")
  lazy val enter: Channel[PointerEvent] = channel("enter")
  lazy val leave: Channel[PointerEvent] = channel("leave")
  /** The wheel: `x` and `y` are the deltas, in pixels. */
  lazy val wheel: Channel[PointerEvent] = channel("wheel")

  /** Whether the pointer is over the component. */
  lazy val over: Val[Boolean] = {
    val state = Var(false)
    enter.attach(_ => state @= true)
    leave.attach(_ => state @= false)
    state
  }

  def channel(kind: String): Channel[PointerEvent] = {
    val channel = Channel[PointerEvent]
    component.node.onPointer(kind, (x, y) => {
      val event = PointerEvent(kind, x, y)
      if (!Pointer.consumed.contains(event)) channel @= event
    })
    channel
  }
}
