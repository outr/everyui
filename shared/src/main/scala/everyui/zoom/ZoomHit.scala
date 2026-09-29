package everyui.zoom

import everyui.component.Component
import everyui.draw.{Figure, Hit}

/** What is under a point of a [[ZoomCanvas]] (`ZoomCanvas.hit`): the item, by the keys from the top of the canvas down
  * to it (`path`), its component where it has been made, and the point in the component's own units (`x`, `y`: a
  * drawing's pixels, when the item is a drawing), where one pixel of the screen is `pixel` of them. */
case class ZoomHit(path: List[String], item: ZoomItem, component: Option[Component], x: Double, y: Double,
                   pixel: Double) {
  /** The item's own key. */
  def key: String = item.key

  /** Which of `figures` (the item's drawing's) is at the point, by index: within `slop` pixels of the screen of its
    * stroke, however far the view is zoomed. */
  def figure(figures: List[Figure], slop: Double = 4.0): Option[Int] = Hit.figureAt(figures, x, y, slop * pixel)
}
