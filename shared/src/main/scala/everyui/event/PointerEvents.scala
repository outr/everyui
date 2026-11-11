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

  /** Whether the pointer is over the component: as it comes onto it and goes off it, and as what is under a still
    * pointer moves (a card a list moved up under it is over, the one it moved away is not), where `enter` and `leave`
    * are the pointer's own moves only, so what takes the focus as the pointer comes onto it does not take it from the
    * keys scrolling it under a still pointer. */
  lazy val over: Val[Boolean] = {
    val state = Var(false)
    enter.attach(_ => state @= true)
    leave.attach(_ => state @= false)
    // over it only where the pointer coming onto it would be heard (a coach mark's fence); off it always
    component.node.onOver(on =>
      everyui.error.Errors.guard("pointer over on " + everyui.focus.Focus.named(component), ())(
        if (!on || everyui.focus.Focus.hears(component, PointerKind.Enter)) state @= on))
    state
  }

  /** Whether a pointer that hovers (a mouse, a pen, a trackpad) is over the component: `over`, but not while the last
    * pointer to come onto it or press it was a finger (`PointerEvent.finger`). A finger rests nowhere once it is
    * lifted, yet JavaFX, Android and some browsers count what it tapped as under the pointer until the next touch
    * lands elsewhere, so a light that follows `over` stays on after a tap. Told by the input, not the device: on a
    * touch laptop a tap leaves it off, and the mouse moving over it lights it again. What lights under a hovering
    * pointer follows this; what a finger's press shows (a pressed state) does not need it. */
  lazy val hover: Val[Boolean] = {
    val fingered = Var(false)
    enter.attach(e => fingered @= e.finger)
    down.attach(e => fingered @= e.finger)
    move.attach(e => if (!e.finger) fingered @= false)
    Val(over() && !fingered())
  }

  def channel(kind: PointerKind): Channel[PointerEvent] = {
    val channel = Channel[PointerEvent]
    // a handler that throws is told as this event's, on this component (Errors.guard)
    component.node.onPointer(kind, (x, y) =>
      everyui.error.Errors.guard("pointer " + kind + " on " + everyui.focus.Focus.named(component), ())(
        heard(channel, kind, x, y)))
    channel
  }

  /** What the platform heard, handed to `channel` unless a component inside this consumed it: a menu asked for that
    * something put a dialog up for (a `Menu`) is consumed, as answered. */
  private[everyui] def heard(channel: Channel[PointerEvent], kind: PointerKind, x: Double, y: Double): Unit = {
    // a press that scrolled something is a scroll: its release clicks nothing, here or anywhere it climbs (ScrollPress)
    if (kind == PointerKind.Down) ScrollPress.down()
    else if (kind == PointerKind.Up) ScrollPress.up()
    if (kind != PointerKind.Click || !ScrollPress.holdsClick()) told(channel, kind, x, y)
  }

  private def told(channel: Channel[PointerEvent], kind: PointerKind, x: Double, y: Double): Unit = {
    val held = everyui.render.Platform.modifiers()
    val event = PointerEvent(kind, x, y, everyui.render.Platform.pointerButton(), held.ctrl, held.meta, held.alt,
      held.shift, kind != PointerKind.Wheel && everyui.render.Platform.pointerFinger())
    // a hand on the pointer is somebody using the app as surely as a key is: a finger scrolling a list on a phone,
    // or a mouse moving over a desktop window, holds a screensaver off just as a remote does
    val use = kind == PointerKind.Down || kind == PointerKind.Move || kind == PointerKind.Wheel ||
      kind == PointerKind.Click
    // the pointer coming onto a component, or off it, is the pointer's doing too, for what the focus does next (not for
    // the screensaver: a list scrolling under a still pointer brings it onto things): a card that takes the focus as
    // the pointer comes onto it was focused by the pointer, not a key, and a scroll revealing it as for a key brought the
    // next card under the still pointer, which took the focus, and on down the page (NaboTV's Home ran 3 to 4338 px)
    val pointed = kind == PointerKind.Enter || kind == PointerKind.Leave
    if (use) everyui.focus.Focus.used()
    if ((use && kind != PointerKind.Wheel) || pointed) Pointer.usedAt = everyui.render.Platform.ticks()
    // a press ends the keys' moving of the focus, and may let it go (Focus.pointerDown, pointerUp)
    if (kind == PointerKind.Down) everyui.focus.Focus.pointerDown(event)
    else if (kind == PointerKind.Up) everyui.focus.Focus.pointerUp()
    // nor is it heard by what a coach mark holds it from: outside a confined region, or under a spotlight's dim
    if (!Pointer.blocks(event) && everyui.focus.Focus.hears(component, kind) &&
      everyui.coach.Spotlight.lets(component, kind, x, y)) {
      val dialogs = everyui.component.Modal.opened
      channel @= event
      if (kind == PointerKind.Menu && everyui.component.Modal.opened != dialogs) event.consume()
    }
    // a menu held back is answered, as nothing: the page's own does not show in its place
    else if (kind == PointerKind.Menu) event.consume()
  }
}
