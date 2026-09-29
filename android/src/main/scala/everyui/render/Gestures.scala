package everyui.render

import android.view.ScaleGestureDetector

/** Pinches (`everyui.zoom.ZoomCanvas`): two fingers on the screen, recognised by the platform's own detector from the
  * touches the node hears. */
object Gestures {
  /** Whether a pinch can come: where there is a touch screen. */
  def pinches(): Boolean = Platform.touchSupport()

  /** `node`'s pinches told to `pinch` as they go (how much larger since the last, and the point between the fingers, in
    * the render root's coordinates), and `ended` as they finish. */
  def take(node: RenderNode, pinch: (Double, Double, Double) => Unit, ended: () => Unit): Unit = {
    val detector = new ScaleGestureDetector(Host.activity, new ScaleGestureDetector.SimpleOnScaleGestureListener {
      override def onScale(d: ScaleGestureDetector): Boolean = {
        val at = Array(0, 0)
        val root = Array(0, 0)
        node.view.getLocationOnScreen(at)
        Host.root.getLocationOnScreen(root)
        pinch(d.getScaleFactor.toDouble, (at(0) - root(0) + d.getFocusX) / Host.scale,
          (at(1) - root(1) + d.getFocusY) / Host.scale)
        true
      }
      override def onScaleEnd(d: ScaleGestureDetector): Unit = ended()
    })
    node.touchHook = Some(e => detector.onTouchEvent(e))
    node.listen()
  }
}
