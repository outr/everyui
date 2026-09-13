package universal.image

import universal.async.Task
import universal.render.Platform
import universal.spatial.Size

/** youi's `Image`: a loaded bitmap and its natural size. */
class Image(val source: String, val width: Double, val height: Double):
  lazy val size: Size = Size(width, height)

  override def toString: String = "Image(" + width.toString + " x " + height.toString + ")"

object Image:
  lazy val empty: Image = new Image("", 0.0, 0.0)

  /** Load `source` (a site-absolute path, as in youi) and complete with its natural size. */
  def apply(source: String): Task[Image] =
    val uri = Platform.resolvePath(source)
    Task.async[Image](done => Platform.loadImage(uri, (w, h) => done(new Image(uri, w, h))))
