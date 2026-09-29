package everyui.render

import javafx.scene.input.{ScrollEvent, ZoomEvent}

/** Pinches (`everyui.zoom.ZoomCanvas`): a trackpad's pinch, or two fingers on a touch screen, which JavaFX recognises
  * itself and sends as zoom events. */
object Gestures {
  def pinches(): Boolean = true

  /** `node`'s gestures its own: a pinch told to `pinch` as it goes (how much larger since the last, and where, in the
    * scene), and `ended` as it finishes; and the wheel over it kept from what it is in. */
  def take(node: RenderNode, pinch: (Double, Double, Double) => Unit, ended: () => Unit): Unit = {
    node.fx.addEventHandler(ZoomEvent.ZOOM, (e: ZoomEvent) => {
      pinch(e.getZoomFactor, e.getSceneX, e.getSceneY)
      e.consume()
    })
    node.fx.addEventHandler(ZoomEvent.ZOOM_FINISHED, (e: ZoomEvent) => {
      ended()
      e.consume()
    })
    node.fx.addEventHandler(ScrollEvent.SCROLL, (e: ScrollEvent) => e.consume())
  }
}
