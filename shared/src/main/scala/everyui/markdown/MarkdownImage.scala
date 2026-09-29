package everyui.markdown

import everyui.component.{Container, ImageView, TextView}
import everyui.component.types.Semantics
import everyui.theme.Theme
import reactify.*

/** A picture of a `MarkdownView`, at its own size or smaller to fit `width` and the style's tallest, with its alt
  * text for whatever cannot see it. Where it cannot be loaded, its alt text shows in its place. */
class MarkdownImage(image: MdInline.Picture, ctx: MarkdownContext, width: => Double) extends Container {
  private val view = new ImageView
  private val natural: Var[(Double, Double)] = own(Var((0.0, 0.0)))
  private val failed: Var[Boolean] = own(Var(false))
  private val words = TextView.bound(if (failed()) "[" + (if (image.alt.isEmpty) image.src else image.alt) + "]" else "",
    ctx.style.points * 0.9, Theme.muted())

  view.alt @= image.alt
  semantics @= Semantics.image(image.alt)
  if (image.title.nonEmpty) tooltip @= image.title
  words.visible := failed()
  everyui.image.Image(MarkdownView.resolve(image.src, ctx.base())).map { loaded =>
    if (loaded.width <= 0.0 || loaded.height <= 0.0) failed @= true
    else {
      view.image @= loaded
      natural @= (loaded.width, loaded.height)
      // bound once the picture is in: taking it sets the view to its natural size
      view.size.width := natural()._1 * shrink
      view.size.height := natural()._2 * shrink
    }
  }.startUnit()

  /** How much smaller than its own size it is drawn: no larger, as wide as `width` and as tall as the style allows at
    * most. */
  private def shrink: Double = math.min(1.0, math.min(width / natural()._1, ctx.style.imageHeight / natural()._2))

  size.width := (if (failed()) words.size.width() else view.size.width())
  size.height := (if (failed()) words.size.height() else view.size.height())
  children ++= List(view, words)

  override def kind: String = "MarkdownImage(" + image.src + ")"
}
