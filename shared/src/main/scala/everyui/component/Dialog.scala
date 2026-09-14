package everyui.component

import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A dialog: a title, whatever goes in `body` (stacked; it scrolls when taller than the screen leaves room for),
  * and its `buttons` on the right. `show()` puts it up on the modal sheet, `close()` takes it down. */
class Dialog(title: String, width: Double = 440.0) extends Container {
  val body: Container = Column(10.0)()
  val buttons: Container = Row(8.0)()
  private val scroll = new ScrollContainer {
    size.width @= width
    size.height := math.min(body.contentHeight(), ui.size.height() - 220.0)
    content.size.height := body.contentHeight
    content.children += body
  }

  // the buttons sit at the right, on a line as wide as the body
  private val buttonLine = new Container {
    size.width @= width
    size.height := buttons.size.height
    buttons.position.right @= width
    children += buttons
  }

  body.size.width @= width
  layout @= Some(VerticalLayout(10.0))
  padding @= 24.0
  size.width @= width + 48.0
  backgroundColor := Theme.raised()
  radius @= 12.0
  children ++= List(TextView(title, 20.0), scroll, buttonLine)

  def show(): Unit = Modal.show(this)
  def close(): Unit = Modal.close()
}
