package everyui.component

import everyui.focus.Focus
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** youi's `Modal`: one dialog at a time over everything, on a sheet that dims the page. Showing one focuses the
  * component it names and keeps the focus inside it; a click on the sheet, Escape or Back closes it (before Back does
  * anything else), and the focus goes back to where it was. */
object Modal {
  private var open: Option[Container] = None
  private var release: () => Unit = () => ()
  private var before: Option[Component] = None
  private var closed: () => Unit = () => ()

  /** Put `panel` up, with the focus on `focus`; `after` hears it close, however it closes, once the focus is back. */
  def show(panel: Container, focus: Component, after: () => Unit = () => ()): Unit = {
    close()
    closed = after
    val sheet = new Container {
      size.width := ui.size.width
      size.height := ui.size.height
      backgroundColor := Theme.backdrop()
      panel.position.center := size.center
      panel.position.middle := size.middle
      children += panel
      event.click.attach(_ => close())
    }
    panel.event.click.attach(_.consume())
    before = Focus.focused()
    open = Some(sheet)
    release = Focus.onBack(_ => {
      close()
      true
    })
    ui.children += sheet
    Focus.scope @= Some(sheet)
    focus.focus()
  }

  def close(): Unit = open.foreach { sheet =>
    ui.children -= sheet
    open = None
    Focus.scope @= None
    release()
    release = () => ()
    val previous = before
    before = None
    previous.foreach(c => if (Focus.showing(c)) c.focus() else Focus.focused @= None)
    val heard = closed
    closed = () => ()
    heard()
  }
}
