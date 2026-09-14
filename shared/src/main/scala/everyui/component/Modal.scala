package everyui.component

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
