package everyui.kit

import everyui.component.{Component, Container}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import reactify.*

/** Parts placed by hand read from the right in a right-to-left language, as the layouts' rows do by themselves: a
  * part `left` in from the start of `width` sits that far in from the right instead, and the marks that point the way
  * the page reads (a chevron on, an arrow back) turn round with it. */
object Mirror {
  /** `child` `left` in from the start of a `width` wide parent. */
  def place(child: Component, width: () => Double, left: () => Double): Unit =
    child.position.left := (if (Direction.rtl()) width() - left() - child.size.width() else left())

  /** `c` at the start of a strip `width` wide, for a column whose lines should start where the language does. The
    * strip takes no room while `c` takes none, as a column closes up round an empty line. */
  def start(c: Component, width: () => Double): Container = {
    place(c, width, () => 0.0)
    new Container {
      size.width := (if (c.size.width() > 0.0 || c.size.height() > 0.0) width() else 0.0)
      size.height := c.size.height()
      children += c
    }
  }

  /** The chevron that leads on: right, or left in a right-to-left language. */
  def onward: MaterialIcon = if (Direction.rtl()) MaterialIcon.ChevronLeft else MaterialIcon.ChevronRight

  /** The arrow that goes back: left, or right in a right-to-left language. */
  def backward: MaterialIcon = if (Direction.rtl()) MaterialIcon.ArrowForward else MaterialIcon.ArrowBack
}
