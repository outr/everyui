package everyui.zoom

import everyui.event.Modifiers

/** A click on a [[ZoomCanvas]] (`ZoomCanvas.clicked`), or OK pressed on it, which clicks the middle of the view: where
  * in the world (`x`, `y`), what item is there, if one is, and the modifier keys held. A drag or a pinch is no click. */
case class ZoomPoint(x: Double, y: Double, hit: Option[ZoomHit], modifiers: Modifiers)
