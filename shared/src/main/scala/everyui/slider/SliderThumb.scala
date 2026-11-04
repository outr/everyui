package everyui.slider

import everyui.component.{Container, TextView}
import everyui.event.KeyEvent
import everyui.theme.Theme

/** Where a slider is set: a disc `SliderThumb.disc` across in `fill`, in the middle of a halo `SliderThumb.halo`
  * across, which lights as the pointer is over it, more while it `lit` (its slider has the focus) or has the focus
  * itself, with a ring of the accent round it then, and more again while it is `pressed`. Over it (beside it, on a
  * vertical slider), while `bubble` has words, the value in a label of the accent. Its box is the halo's; the slider
  * places it. `keeps` is the keys it keeps while it has the focus, where it takes the focus itself (a range's thumbs). */
class SliderThumb(fill: => everyui.Color, pressed: => Boolean, lit: => Boolean, enabled: => Boolean,
                  bubble: => Option[String], vertical: Boolean, keeps: KeyEvent => Boolean = _ => false) extends Container {
  private val dot = new Container
  private val words = TextView.bound(bubble.getOrElse(""), 13.0, Theme.onAccent())
  private val tip = new Container

  size.width @= SliderThumb.halo
  size.height @= SliderThumb.halo
  radius @= SliderThumb.halo / 2.0
  backgroundColor := Theme.accent().withAlpha(
    if (!enabled) 0.0 else if (pressed) 0.24 else if (lit || focusShown()) SliderThumb.focusGlow else if (event.pointer.over()) 0.08 else 0.0)
  borderWidth := (if (enabled && (lit || focusShown())) 2.0 else 0.0)
  borderColor := Theme.accent()

  dot.size.width @= SliderThumb.disc
  dot.size.height @= SliderThumb.disc
  dot.radius @= SliderThumb.disc / 2.0
  dot.position.left @= (SliderThumb.halo - SliderThumb.disc) / 2.0
  dot.position.top @= (SliderThumb.halo - SliderThumb.disc) / 2.0
  dot.backgroundColor := fill
  dot.shadow @= Some(everyui.Shadow(0.0, 1.0, 2.0, everyui.Color.Black.withAlpha(0.3)))

  tip.size.width := math.max(SliderThumb.tip, words.size.width() + 16.0)
  tip.size.height @= SliderThumb.tip
  tip.radius @= SliderThumb.tip / 2.0
  tip.backgroundColor := Theme.accent()
  tip.visible := bubble.nonEmpty
  if (vertical) {
    tip.position.left := -tip.size.width() - 4.0
    tip.position.top @= (SliderThumb.halo - SliderThumb.tip) / 2.0
  }
  else {
    tip.position.left := (SliderThumb.halo - tip.size.width()) / 2.0
    tip.position.top @= -SliderThumb.tip - 2.0
  }
  words.position.left := (tip.size.width() - words.size.width()) / 2.0
  words.position.top := (SliderThumb.tip - words.size.height()) / 2.0
  tip.children += words
  children ++= List(dot, tip)
  // the tip stands out past the halo
  clipsToBox @= Some(false)

  override def keepsEvent(e: KeyEvent): Boolean = keeps(e)

  override def kind: String = "SliderThumb"
}

object SliderThumb {
  // brighter on a remote's screen, where the ring is not drawn (a Roku's Rectangle has no edge) and the room is far
  private[slider] def focusGlow: Double = if (everyui.render.Platform.remoteNavigation()) 0.36 else 0.16
  /** How big the halo is, which is the thumb's box. */
  val halo: Double = 40.0
  /** How big the disc is. */
  val disc: Double = 20.0
  /** How tall the value's label is. */
  val tip: Double = 28.0
}
