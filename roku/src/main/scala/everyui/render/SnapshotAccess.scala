package everyui.render

import everyui.Color
import everyui.capture.SnapshotImage
import everyui.draw.Figure

/** Pictures of the app (`everyui.capture.Snapshot`): this platform takes none, of the window, a component or a
  * drawing's figures, so each answers None at once. */
object SnapshotAccess {
  def supported(): Boolean = false

  def draws(): Boolean = false

  def node(node: RenderNode, scale: Double, done: Option[SnapshotImage] => Unit): Unit = done(None)

  def window(scale: Double, done: Option[SnapshotImage] => Unit): Unit = done(None)

  def figures(figures: List[Figure], width: Double, height: Double, scale: Double, background: Option[Color],
              done: Option[SnapshotImage] => Unit): Unit = done(None)

  /** Nothing is ever taken here to show. */
  def preview(png: Array[Byte]): String = ""
}
