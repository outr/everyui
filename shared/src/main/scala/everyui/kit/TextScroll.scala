package everyui.kit

import everyui.component.*
import everyui.Color
import everyui.event.Key
import everyui.theme.Theme
import reactify.*

/** Words scrolled by a remote or the arrows: it takes the focus, and Up and Down scroll it `step` at a time, gliding,
  * until there is no further to go, when they move the focus on as usual. A thin rail in the accent down its left edge
  * says the focus is on it, since a page of text has nothing else to light up. */
class TextScroll extends ScrollContainer {
  focusable @= true
  step @= 140.0
  glide @= 0.15

  private val mark = new Container {
    size.width @= 3.0
    size.height := TextScroll.this.size.height()
    radius @= 1.5
    backgroundColor := (if (TextScroll.this.focusShown()) Theme.accent() else Color.Clear)
  }
  children += mark
  // the words stand clear of the rail (TextScroll.framed keeps their width)
  content.position.left @= TextScroll.gutter
  content.size.width := size.width() - TextScroll.gutter

  override def keepsKey(key: Key): Boolean =
    (key == Key.ArrowUp && offset() > 0.0) ||
      (key == Key.ArrowDown && offset() < content.contentHeight() - size.height())
}

object TextScroll {
  /** How far the words stand in from the rail. */
  val gutter: Double = 10.0

  /** `scroll` holding words laid out `across` wide, in a box of that width with the rail out in the margin to its left:
    * the words keep all their width (inside the scroll's own box they would lose the gutter at the right, where the
    * scroll cuts them off), and the rail shows, since a plain box does not clip what stands out of it. */
  def framed(scroll: TextScroll, across: Double): Container = {
    scroll.position.left @= -gutter
    scroll.size.width @= across + gutter
    new Container {
      size.width @= across
      size.height := scroll.size.height()
      clipsToBox @= Some(false)
      children += scroll
    }
  }
}
