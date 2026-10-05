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
      pinch(e.getZoomFactor, Host.unscaled(e.getSceneX), Host.unscaled(e.getSceneY))
      e.consume()
    })
    node.fx.addEventHandler(ZoomEvent.ZOOM_FINISHED, (e: ZoomEvent) => {
      ended()
      e.consume()
    })
    node.fx.addEventHandler(ScrollEvent.SCROLL, (e: ScrollEvent) => e.consume())
  }

  /** A finger's reach past a small control (`everyui.event.TapSlop`): none here, where JavaFX hears a mouse, and a
    * touch screen's taps as the mouse at their point. */
  def slop(node: RenderNode, on: Boolean): Unit = ()

  /** `node` never the pointer's: a click and the wheel go through it to what is under it (a shade drawn over a
    * table). */
  def passThrough(node: RenderNode): Unit = node.fx.setMouseTransparent(true)

  /** `node` the pointer's only where one of its children is (a spotlight's dim round its hole): a pane not picked by
    * its bounds is already. */
  def passBetween(node: RenderNode): Unit = node.fx.setPickOnBounds(false)
}
