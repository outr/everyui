package everyui.render

/** Pinches (`everyui.zoom.ZoomCanvas`): a remote has no fingers to pinch with, so none ever comes; a canvas zooms by
  * its keys here. */
object Gestures {
  def pinches(): Boolean = false

  def take(node: RenderNode, pinch: (Double, Double, Double) => Unit, ended: () => Unit): Unit = ()
}
