package everyui.event

import everyui.render.Platform

/** A pointer at a component, in the render root's coordinates (for the wheel, the deltas), with the button it was
  * (`PointerEvent.PrimaryButton`, `MiddleButton` or `SecondaryButton`; a finger is the primary) and the modifier keys
  * held with it: a remote holds none. `finger` is a touch on the screen rather than a mouse or a trackpad: what a
  * scrolling surface follows and flicks on, on any device, a touch laptop as much as a phone. `consume()` keeps it
  * from the components underneath: a button's click is not also its card's. */
case class PointerEvent(kind: PointerKind, x: Double, y: Double, button: Int = 0, ctrl: Boolean = false,
                        meta: Boolean = false, alt: Boolean = false, shift: Boolean = false,
                        finger: Boolean = false) {
  def consume(): Unit = {
    Pointer.consumed = Some(this)
    Pointer.consumedAt = Platform.ticks()
    Pointer.consumedEvent = Platform.pointerEvent()
  }

  /** The platform's shortcut modifier held: Command on a Mac, Ctrl elsewhere (as `Chord.primary`). */
  def primary: Boolean = if (Platform.commandKey()) meta else ctrl
  def modifiers: Modifiers = Modifiers(ctrl, meta, alt, shift)
}

object PointerEvent {
  val PrimaryButton: Int = 0
  val MiddleButton: Int = 1
  val SecondaryButton: Int = 2
}
