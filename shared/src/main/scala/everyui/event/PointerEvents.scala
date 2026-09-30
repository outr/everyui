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
  /** A context menu asked for where the pointer is: the secondary button, or a finger held. A handler that shows one
    * puts up a `Menu` (or any dialog), or consumes the event: only then is the page's own menu kept away
    * (`Pointer.menuAnswered`), so words the app has no menu for keep the browser's Copy and Look Up. */
  lazy val menu: Channel[PointerEvent] = channel(PointerKind.Menu)

  /** A click with the middle button: down and up on the component (an app closes a tab with it). */
  lazy val middle: Channel[PointerEvent] = {
    val channel = Channel[PointerEvent]
    var pressed = false
    down.attach(e => pressed = e.button == PointerEvent.MiddleButton)
    up.attach { e =>
      if (pressed && e.button == PointerEvent.MiddleButton) channel @= e.copy(kind = PointerKind.Click)
      pressed = false
    }
    channel
  }

  /** Whether the pointer is over the component. */
  lazy val over: Val[Boolean] = {
    val state = Var(false)
    enter.attach(_ => state @= true)
    leave.attach(_ => state @= false)
    state
  }

  def channel(kind: PointerKind): Channel[PointerEvent] = {
    val channel = Channel[PointerEvent]
    component.node.onPointer(kind, (x, y) => heard(channel, kind, x, y))
    channel
  }

  /** What the platform heard, handed to `channel` unless a component inside this consumed it: a menu asked for that
    * something put a dialog up for (a `Menu`) is consumed, as answered. */
  private[everyui] def heard(channel: Channel[PointerEvent], kind: PointerKind, x: Double, y: Double): Unit = {
    val held = everyui.render.Platform.modifiers()
    val event = PointerEvent(kind, x, y, everyui.render.Platform.pointerButton(), held.ctrl, held.meta, held.alt,
      held.shift)
    // a hand on the pointer is somebody using the app as surely as a key is: a finger scrolling a list on a phone,
    // or a mouse moving over a desktop window, holds a screensaver off just as a remote does
    val use = kind == PointerKind.Down || kind == PointerKind.Move || kind == PointerKind.Wheel ||
      kind == PointerKind.Click
    if (use) everyui.focus.Focus.used()
    if (use && kind != PointerKind.Wheel) Pointer.usedAt = everyui.render.Platform.ticks()
    if (!Pointer.blocks(event)) {
      val dialogs = everyui.component.Modal.opened
      channel @= event
      if (kind == PointerKind.Menu && everyui.component.Modal.opened != dialogs) event.consume()
    }
  }
}
