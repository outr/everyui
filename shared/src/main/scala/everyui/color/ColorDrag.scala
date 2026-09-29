package everyui.color

import everyui.component.Component
import everyui.event.PointerDrag

/** A pointer or a finger dragged across a picker's `target` (everyui.event.PointerDrag): `place` hears where it is,
  * relative to the target's box, from the press until it lets go. */
class ColorDrag(target: Component, place: (Double, Double) => Unit) extends PointerDrag(target, place)
