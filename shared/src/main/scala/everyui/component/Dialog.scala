package everyui.component

import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A dialog: a title, whatever goes in `body` (stacked; it scrolls when taller than the screen leaves room for),
  * and its `buttons` at the end of the line (the right, or the left in a right-to-left language). `show(defaultFocus)`
  * puts it up on the modal sheet with the focus there, `close()` takes it down. */
class Dialog(title: String, width: Double = 440.0) extends Container {
  val body: Container = new Container {
    size.width @= width
    layout @= Some(VerticalLayout(10.0))
  }
  val buttons: Container = Row(8.0)()
  private val scroll = new ScrollContainer {
    size.width @= width
    size.height := math.min(body.contentHeight(), ui.size.height() - 220.0)
    content.size.height := body.contentHeight
    content.children += body
  }

  // the buttons sit where the line ends, on a line as wide as the body: at the right, or at the left in a right-to-left
  // language, where their row mirrors itself (the title, in the column, already starts at the right there)
  private val buttonLine = new Container {
    size.width @= width
    size.height := buttons.size.height
    buttons.position.left := (if (everyui.i18n.Direction.rtl()) 0.0 else width - buttons.size.width())
    children += buttons
  }

  layout @= Some(VerticalLayout(10.0))
  padding @= 24.0
  size.width @= width + 48.0
  backgroundColor := Theme.raised()
  radius @= 12.0
  children ++= List(TextView(title, 20.0), scroll, buttonLine)

  /** Put it up, with the focus on `defaultFocus` (one of its buttons, usually the one that changes nothing). */
  def show(defaultFocus: Component): Unit = Modal.show(this, defaultFocus)
  def close(): Unit = Modal.close()
}
