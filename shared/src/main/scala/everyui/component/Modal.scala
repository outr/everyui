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
  /** What the dialog wants focused, kept so it can be given the focus again when it comes back out from under. */
  private var inside: Option[Component] = None

  /** Whether something covering the WHOLE screen on its own account is over the top of it — a screensaver.
    *
    * A dialog is drawn over everything, which is right for a dialog and wrong against a screensaver: the screensaver
    * is a screen like any other and so goes UNDER it, leaving a question hanging over a picture nobody is there to
    * read, and holding the focus and Back as well — so the press meant to put the screensaver away answered the
    * dialog instead. Covered, the dialog stays exactly where it is but is not drawn, does not hold the focus and
    * does not take Back. Uncovered, it is there again with the focus where it was, which is what somebody coming
    * back to the television expects to find. */
  val covered: Var[Boolean] = Var(false)

  covered.attach(now => open.foreach { sheet =>
    sheet.visible @= !now
    if (now) {
      Focus.scope @= None
      release()
      release = () => ()
      Focus.focused @= None
    }
    else {
      release = Focus.onBack(_ => {
        close()
        true
      })
      Focus.scope @= Some(sheet)
      inside.foreach(_.focus())
    }
  })

  /** Whether a dialog is up at all, covered or not. */
  def showing: Boolean = open.nonEmpty

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
    inside = Some(focus)
    ui.children += sheet
    // Put up under a cover, it waits there rather than appearing over it: a dialog raised while the screensaver is
    // running is one the viewer is not present for, and it belongs to the screen they left, not to the picture.
    if (covered()) sheet.visible @= false
    else {
      release = Focus.onBack(_ => {
        close()
        true
      })
      Focus.scope @= Some(sheet)
      focus.focus()
    }
  }

  def close(): Unit = open.foreach { sheet =>
    ui.children -= sheet
    open = None
    inside = None
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
