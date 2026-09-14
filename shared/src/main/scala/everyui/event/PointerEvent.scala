package everyui.event

/** A pointer at a component, in the render root's coordinates (for the wheel, the deltas). `consume()` keeps it
  * from the components underneath: a button's click is not also its card's. */
case class PointerEvent(kind: String, x: Double, y: Double) {
  def consume(): Unit = Pointer.consumed = Some(this)
}
