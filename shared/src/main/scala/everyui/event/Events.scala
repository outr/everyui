package everyui.event

import everyui.component.Component
import reactify.*

/** A pointer at a component, in the render root's coordinates (for the wheel, the deltas). `consume()` keeps it
  * from the components underneath: a button's click is not also its card's. */
case class PointerEvent(kind: String, x: Double, y: Double) {
  def consume(): Unit = Pointer.consumed = Some(this)
}

/** The last pointer event a handler consumed; a component under it does not fire it again. */
object Pointer {
  var consumed: Option[PointerEvent] = None
}

/** A key, named as the web names it: "Enter", "Escape", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight",
  * "MediaPlayPause", "a"… Every remote's OK is "Enter" and its back is "Escape". */
case class KeyEvent(key: String)

/** youi's `Events`, over the render seam: the pointer at the component, the keys while it has the focus, and
  * `click` — a pointer click, or Enter while focused. Each channel listens on the node only once something
  * asks for it. */
class Events(component: Component) {
  lazy val pointer: PointerEvents = new PointerEvents(component)
  lazy val key: KeyEvents = new KeyEvents

  lazy val click: Channel[PointerEvent] = {
    val channel = pointer.channel("click")
    channel.attach(_ => if (component.focusable()) component.focus())
    channel
  }
}

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

class KeyEvents {
  lazy val down: Channel[KeyEvent] = Channel[KeyEvent]
  lazy val up: Channel[KeyEvent] = Channel[KeyEvent]
}
