package everyui.render

/** Pinches (`everyui.zoom.ZoomCanvas`): a remote has no fingers to pinch with, so none ever comes; a canvas zooms by
  * its keys here. */
object Gestures {
  def pinches(): Boolean = false

  def take(node: RenderNode, pinch: (Double, Double, Double) => Unit, ended: () => Unit): Unit = ()

  /** No finger, so no reach past a control (`everyui.event.TapSlop`), and no pointer to let through. */
  def slop(node: RenderNode, on: Boolean): Unit = ()
  def passThrough(node: RenderNode): Unit = ()
}
