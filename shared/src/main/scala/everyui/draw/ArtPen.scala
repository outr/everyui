package everyui.draw

import everyui.Color

/** What a picture of [[VectorArt]] is drawn with (`VectorArt.paint`): a platform's own vector drawing (Android's Canvas
  * into a Bitmap, JavaFX's GraphicsContext snapshotted to an Image), handed each shape in order, already placed in the
  * picture's own pixels. A path is the four steps every backend draws (`PathOp`). */
trait ArtPen {
  /** `path` filled with `color` (its alpha the shape's opacity), by the even-odd rule or else the non-zero one. */
  def fill(path: Path, color: Color, evenOdd: Boolean): Unit

  /** `path`'s outline in `color`, `width` pixels wide, its ends `cap` ("butt", "round" or "square") and its corners
    * `join` ("miter", "round" or "bevel"). */
  def stroke(path: Path, color: Color, width: Double, cap: String, join: String): Unit
}
