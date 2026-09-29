package everyui.zoom

import everyui.component.Component
import everyui.spatial.Rect

/** An item where it is on its canvas: its box in the world (`rect`), the keys down to it (`path`), and an id made of
  * them, which its component is kept by; the component while it has one, the box it was last placed at, and the last
  * of the canvas's passes that showed it. Its children are placed the first time they are asked for. */
private[zoom] class ZoomPlaced(val item: ZoomItem, val path: List[String], val id: String, val rect: Rect) {
  var component: Option[Component] = None
  var placed: Option[Rect] = None
  var seen: Int = 0
  private var placedChildren: Option[Vector[ZoomPlaced]] = None

  def children: Vector[ZoomPlaced] = placedChildren match {
    case Some(all) => all
    case None =>
      val all = item.children().map(c => new ZoomPlaced(c, path :+ c.key, ZoomPlaced.idOf(id, c.key),
        Rect(rect.x + c.x * rect.width, rect.y + c.y * rect.height, c.width * rect.width, c.height * rect.height)))
      placedChildren = Some(all)
      all
  }
}

private[zoom] object ZoomPlaced {
  /** An id for `key` under `parent`, which no other path shares: each key counted out before it. */
  def idOf(parent: String, key: String): String = parent + key.length.toString + ":" + key + ";"

  def root(item: ZoomItem): ZoomPlaced = new ZoomPlaced(item, List(item.key), idOf("", item.key), item.box)
}
