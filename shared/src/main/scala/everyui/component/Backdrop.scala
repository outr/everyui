package everyui.component

import everyui.image.Image
import reactify.*

/** An image covering the whole of its box at its own proportions, centred, with what overflows cut off: what sits
  * behind a screen. It follows `source` (a site-absolute path or a URL; empty shows nothing), keeping the image it has
  * until the next one has loaded, so changing it never flashes an empty box. */
class Backdrop extends Container {
  override def kind: String = "Backdrop"

  val source: Var[String] = Var("")
  /** What the picture shows, in words (`ImageView.alt`). */
  def alt: Var[String] = view.alt

  private val view = new ImageView
  private var shown: Image = Image.empty

  children += view
  source.attach(requested => load(requested))
  size.width.attach(_ => place())
  size.height.attach(_ => place())

  private def load(requested: String): Unit =
    if (requested.isEmpty) show(Image.empty)
    else Image(requested).map(loaded => if (source() == requested) show(loaded)).startUnit()

  private def show(image: Image): Unit = {
    shown = image
    view.image @= image
    place()
  }

  /** The image scaled until it covers the box on both sides, centred on it. */
  private def place(): Unit = {
    node.setClip(size.width(), size.height())
    val scale =
      if (shown.width > 0.0 && shown.height > 0.0) math.max(size.width() / shown.width, size.height() / shown.height)
      else 0.0
    view.size.width @= shown.width * scale
    view.size.height @= shown.height * scale
    view.position.left @= (size.width() - view.size.width()) / 2.0
    view.position.top @= (size.height() - view.size.height()) / 2.0
  }
}
