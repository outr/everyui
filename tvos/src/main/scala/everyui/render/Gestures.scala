package everyui.render

/** Pinches (`everyui.zoom.ZoomCanvas`): the Siri Remote's touch surface reaches the app as its arrows, so no pinch ever
  * comes; a canvas zooms by its keys here. */
object Gestures {
  def pinches(): Boolean = false

  def take(node: RenderNode, pinch: (Double, Double, Double) => Unit, ended: () => Unit): Unit = ()
}
