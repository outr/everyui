package everyui.event

import everyui.render.Platform

/** A pointer at a component, in the render root's coordinates (for the wheel, the deltas), with the button it was
  * (`PointerEvent.PrimaryButton`, `MiddleButton` or `SecondaryButton`; a finger is the primary) and the modifier keys
  * held with it: a remote holds none. `consume()` keeps it from the components underneath: a button's click is not
  * also its card's. */
case class PointerEvent(kind: PointerKind, x: Double, y: Double, button: Int = 0, ctrl: Boolean = false,
                        meta: Boolean = false, alt: Boolean = false, shift: Boolean = false) {
  def consume(): Unit = Pointer.consumed = Some(this)

  /** The platform's shortcut modifier held: Command on a Mac, Ctrl elsewhere (as `Chord.primary`). */
  def primary: Boolean = if (Platform.commandKey()) meta else ctrl
  def modifiers: Modifiers = Modifiers(ctrl, meta, alt, shift)
}

object PointerEvent {
  val PrimaryButton: Int = 0
  val MiddleButton: Int = 1
  val SecondaryButton: Int = 2
}
