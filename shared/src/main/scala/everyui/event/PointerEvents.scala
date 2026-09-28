package everyui.event

import everyui.component.Component
import reactify.*

/** The pointer at a component: each channel listens on the node only once something asks for it. */
class PointerEvents(component: Component) {
  lazy val down: Channel[PointerEvent] = channel(PointerKind.Down)
  lazy val up: Channel[PointerEvent] = channel(PointerKind.Up)
  lazy val move: Channel[PointerEvent] = channel(PointerKind.Move)
  lazy val enter: Channel[PointerEvent] = channel(PointerKind.Enter)
  lazy val leave: Channel[PointerEvent] = channel(PointerKind.Leave)
  /** The wheel: `x` and `y` are the deltas, in pixels. */
  lazy val wheel: Channel[PointerEvent] = channel(PointerKind.Wheel)
  /** A context menu asked for where the pointer is: the secondary button (the page's own menu is not shown). */
  lazy val menu: Channel[PointerEvent] = channel(PointerKind.Menu)

  /** Whether the pointer is over the component. */
  lazy val over: Val[Boolean] = {
    val state = Var(false)
    enter.attach(_ => state @= true)
    leave.attach(_ => state @= false)
    state
  }

  def channel(kind: PointerKind): Channel[PointerEvent] = {
    val channel = Channel[PointerEvent]
    // a hand on the pointer is somebody using the app as surely as a key is: a finger scrolling a list on a phone,
    // or a mouse moving over a desktop window, holds a screensaver off just as a remote does
    val use = kind == PointerKind.Down || kind == PointerKind.Move || kind == PointerKind.Wheel ||
      kind == PointerKind.Click
    component.node.onPointer(kind, (x, y) => {
      val event = PointerEvent(kind, x, y)
      if (use) everyui.focus.Focus.used()
      if (!Pointer.consumed.contains(event)) channel @= event
    })
    channel
  }
}
