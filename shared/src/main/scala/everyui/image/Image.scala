package everyui.image

import everyui.async.Task
import everyui.render.Platform
import everyui.spatial.Size

/** youi's `Image`: a loaded bitmap and its natural size. */
class Image(val source: String, val width: Double, val height: Double) {
  lazy val size: Size = Size(width, height)

  override def toString: String = "Image(" + width.toString + " x " + height.toString + ")"
}

object Image {
  lazy val empty: Image = new Image("", 0.0, 0.0)

  /** Load `source` (a site-absolute path, as in youi, a URL, or a `data:` address) and complete with its natural size:
    * 0 by 0 where it cannot be had or decoded. An SVG's is its own (its width and height, else its viewBox's); see
    * CONSUMING.md for which formats each platform draws. */
  def apply(source: String): Task[Image] = {
    val uri = if (source.contains("://") || source.startsWith("data:")) source else Platform.resolvePath(source)
    Task.async[Image](done => Platform.loadImage(uri, (w, h) => done(new Image(uri, w, h))))
  }
}
