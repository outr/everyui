package everyui.component

import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** youi's `Modal`: one dialog at a time over everything, on a sheet that dims the page. A click on the sheet or
  * Escape closes it. */
object Modal {
  private var open: Option[Container] = None

  def show(panel: Container): Unit = {
    close()
    val sheet = new Container {
      size.width := ui.size.width
      size.height := ui.size.height
      backgroundColor := Theme.backdrop()
      focusable @= true
      panel.position.center := size.center
      panel.position.middle := size.middle
      children += panel
      event.click.attach(_ => close())
      event.key.down.attach(k => if (k.key == "Escape") close())
    }
    panel.event.click.attach(_.consume())
    open = Some(sheet)
    ui.children += sheet
    sheet.focus()
  }

  def close(): Unit = {
    open.foreach(sheet => ui.children -= sheet)
    open = None
  }
}

/** A dialog: a title, whatever goes in `body` (stacked; it scrolls when taller than the screen leaves room for),
  * and its `buttons` on the right. `show()` puts it up on the modal sheet, `close()` takes it down. */
class Dialog(title: String, width: Double = 440.0) extends Container {
  val body: Container = new Container {
    layout @= Some(VerticalLayout(10.0))
    size.width @= width
    size.height := contentHeight
  }
  val buttons: Container = new Container {
    layout @= Some(HorizontalLayout(8.0))
    size.height := contentHeight
    size.width := contentWidth
    position.right := width
  }
  private val scroll = new ScrollContainer {
    size.width @= width
    size.height := math.min(body.contentHeight(), ui.size.height() - 220.0)
    content.size.height := body.contentHeight
    content.children += body
  }
  private val inner = new Container {
    layout @= Some(VerticalLayout(10.0))
    position.left @= 24.0
    position.top @= 20.0
    size.width @= width
    size.height := contentHeight
    children ++= List(TextView(title, 20.0), scroll, buttons)
  }

  backgroundColor := Theme.raised()
  radius @= 12.0
  size.width @= width + 48.0
  size.height := inner.contentHeight + 40.0
  children += inner

  def show(): Unit = Modal.show(this)
  def close(): Unit = Modal.close()
}
