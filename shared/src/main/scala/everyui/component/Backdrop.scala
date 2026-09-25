package everyui.component

import everyui.easing.Easing
import everyui.image.Image
import everyui.task.*
import reactify.*

import scala.concurrent.duration.*

/** An image covering the whole of its box at its own proportions, centred, with what overflows cut off: what sits
  * behind a screen. It follows `source` (a site-absolute path or a URL; empty shows nothing), keeping the image it has
  * until the next one has loaded, so changing it never flashes an empty box.
  *
  * Where the picture sits as it is cropped is `alignX` and `alignY`, each from -1 (its left or top edge on the box's)
  * to 1 (its right or bottom), 0 in the middle. A `fallbackSource` stands in for a source that will not load — an
  * image addressed indirectly, which the server may not have — rather than the box going on showing a picture that
  * belongs to something else. With `crossfade`, a new source cross-fades in: what is showing fades out at once, and
  * the new picture fades up only once it has loaded, so a slow one holds on the ground beneath rather than flashing
  * blank; one that never loads brings the old picture back. */
class Backdrop extends Container {
  override def kind: String = "Backdrop"

  val source: Var[String] = Var("")
  /** What the picture shows, in words (`ImageView.alt`). */
  def alt: Var[String] = view.alt
  val alignX: Var[Double] = Var(0.0)
  val alignY: Var[Double] = Var(0.0)
  /** Shown in place of `source` when that cannot be loaded; empty for none. */
  val fallbackSource: Var[String] = Var("")
  /** Whether a change of picture cross-fades rather than cutting. */
  val crossfade: Var[Boolean] = Var(false)

  private val view = new ImageView
  private var shown: Image = Image.empty
  /** The fade under way, so the next one calls it off rather than fighting it. */
  private var fading: Option[TaskInstance] = None

  children += view
  source.attach { requested =>
    if (crossfade() && requested.nonEmpty) fadeTo(0.0, Backdrop.fadeOut)
    load(requested, requested)
  }
  size.width.attach(_ => place())
  size.height.attach(_ => place())
  alignX.attach(_ => place())
  alignY.attach(_ => place())

  /** Load `target` for the source `requested` and, once it has arrived and nothing newer has been asked for, show it.
    * An image that comes back with no size did not load: the `fallbackSource` is tried then, where there is one. */
  private def load(requested: String, target: String): Unit =
    if (target.isEmpty) show(Image.empty)
    else Image(target).map { loaded =>
      if (source() == requested) {
        if (loaded.width > 0.0 && loaded.height > 0.0) show(loaded)
        else if (target == requested && fallbackSource().nonEmpty && fallbackSource() != requested)
          load(requested, fallbackSource())
        // not to be had: fading, the picture that was showing comes back up rather than leaving the box faded out
        else if (crossfade()) fadeTo(1.0, Backdrop.fadeIn)
        else show(loaded)
      }
    }.startUnit()

  private def show(image: Image): Unit = {
    shown = image
    view.image @= image
    place()
    if (crossfade()) fadeTo(1.0, Backdrop.fadeIn)
  }

  /** The picture's opacity to `target` over `time`, in place of whatever fade was under way. */
  private def fadeTo(target: Double, time: FiniteDuration): Unit = {
    fading.foreach(_.cancel())
    fading = Some(view.opacity.to(target).in(time).easing(Easing.cubicInOut).start())
  }

  /** The image scaled until it covers the box on both sides, placed by `alignX` and `alignY`. */
  private def place(): Unit = {
    node.setClip(size.width(), size.height())
    val scale =
      if (shown.width > 0.0 && shown.height > 0.0) math.max(size.width() / shown.width, size.height() / shown.height)
      else 0.0
    view.size.width @= shown.width * scale
    view.size.height @= shown.height * scale
    view.position.left @= (size.width() - view.size.width()) * (1.0 + alignX()) / 2.0
    view.position.top @= (size.height() - view.size.height()) * (1.0 + alignY()) / 2.0
  }
}

object Backdrop {
  /** How long what is showing takes to fade out once another picture is asked for, and the new one to fade up. */
  private val fadeOut: FiniteDuration = 200.millis
  private val fadeIn: FiniteDuration = 380.millis
}
