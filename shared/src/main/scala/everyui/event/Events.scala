package everyui.event

import everyui.component.Component
import reactify.*

/** A pointer at a component, in the render root's coordinates. */
case class PointerEvent(x: Double, y: Double)

/** A key, named as the web names it: "Enter", "Escape", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight",
  * "MediaPlayPause", "a"… Every remote's OK is "Enter" and its back is "Escape". */
case class KeyEvent(key: String)

/** youi's `Events`, over the render seam: the pointer at the component, the keys while it has the focus, and
  * `click` — a pointer click, or Enter while focused. Each channel listens on the node only once something
  * asks for it. */
class Events(component: Component):
  lazy val pointer: PointerEvents = new PointerEvents(component)
  lazy val key: KeyEvents = new KeyEvents

  lazy val click: Channel[PointerEvent] =
    val channel = pointer.channel("click")
    channel.attach(_ => if component.focusable() then component.focus())
    channel

class PointerEvents(component: Component):
  lazy val down: Channel[PointerEvent] = channel("down")
  lazy val up: Channel[PointerEvent] = channel("up")
  lazy val move: Channel[PointerEvent] = channel("move")
  lazy val enter: Channel[PointerEvent] = channel("enter")
  lazy val leave: Channel[PointerEvent] = channel("leave")

  /** Whether the pointer is over the component. */
  lazy val over: Val[Boolean] =
    val state = Var(false)
    enter.attach(_ => state @= true)
    leave.attach(_ => state @= false)
    state

  def channel(kind: String): Channel[PointerEvent] =
    val channel = Channel[PointerEvent]
    component.node.onPointer(kind, (x, y) => channel @= PointerEvent(x, y))
    channel

class KeyEvents:
  lazy val down: Channel[KeyEvent] = Channel[KeyEvent]
  lazy val up: Channel[KeyEvent] = Channel[KeyEvent]
