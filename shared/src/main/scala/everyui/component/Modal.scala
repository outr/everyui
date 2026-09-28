package everyui.component

import everyui.focus.Focus
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** youi's `Modal`: dialogs over everything, each on a sheet that dims what is under it. Showing one focuses the
  * component it names and keeps the focus inside it; a click on the sheet, Escape or Back closes it (before Back does
  * anything else), and the focus goes back to where it was.
  *
  * They stack. `show` puts a dialog up IN PLACE of the one on top, which is what most dialogs mean by opening;
  * `over` puts one up ON TOP of it — a confirmation over a picker, a word that something is under way over the dialog
  * that started it — and closing that one is back to the one under it, focus and all. A dialog that is not
  * `dismissible` ignores Back and the sheet (something is being written, and leaving now would act on a screen that
  * is about to change); only the code that put it up takes it down, by `remove`. */
object Modal {
  /** The dialogs up, the top one first. */
  private var layers: List[ModalLayer] = Nil

  /** Whether something covering the WHOLE screen on its own account is over the top of it — a screensaver.
    *
    * A dialog is drawn over everything, which is right for a dialog and wrong against a screensaver: the screensaver
    * is a screen like any other and so goes UNDER it, leaving a question hanging over a picture nobody is there to
    * read, and holding the focus and Back as well — so the press meant to put the screensaver away answered the
    * dialog instead. Covered, the dialogs stay exactly where they are but are not drawn, do not hold the focus and
    * do not take Back. Uncovered, they are there again with the focus where it was, which is what somebody coming
    * back to the television expects to find. */
  val covered: Var[Boolean] = Var(false)

  covered.attach(now => if (layers.nonEmpty) {
    layers.foreach(_.sheet.visible @= !now)
    if (now) {
      Focus.scope @= None
      Focus.focused @= None
    }
    else {
      Focus.scope @= layers.headOption.map(_.sheet)
      layers.headOption.foreach(_.inside.focus())
    }
  })

  /** Whether a dialog is up at all, covered or not. */
  def showing: Boolean = {
    forget()
    layers.nonEmpty
  }

  /** Whether a dialog is up where it can be seen, and so holds the focus and Back: one waiting under a cover does
    * neither, and the screen it waits over answers Back as though it were not there. */
  def inView: Boolean = showing && !covered()

  /** Whether `c` is in a dialog waiting under a cover: not to be focused. A focus put there was on something nobody
    * could see, and OK pressed at the screensaver answered the question hidden under it. */
  def hides(c: Component): Boolean = covered() && layers.exists(layer => Focus.within(c, layer.sheet))

  /** Let go of each dialog whose sheet something else took off the screen (a screen shown in its place, which puts
    * nothing but itself in the host): left on the stack, it went on claiming Back, and keeping the screen under it from
    * hearing it, with nothing on the screen to close. It is gone as though closed, without giving the focus back to
    * what it covered, which has gone with it. */
  private def forget(): Unit = layers.filterNot(layer => Focus.showing(layer.sheet)).foreach { layer =>
    layers = layers.filterNot(_ eq layer)
    layer.release()
    if (Focus.scope().exists(_ eq layer.sheet)) Focus.scope @= (if (covered()) None else layers.headOption.map(_.sheet))
    layer.after()
  }

  /** Put `panel` up in place of the dialog on top, if there is one, with the focus on `focus`; `after` hears it close,
    * however it closes, once the focus is back. */
  def show(panel: Container, focus: Component, after: () => Unit = () => (), dismissible: Boolean = true,
           dim: Boolean = true, centre: Boolean = true): Unit = {
    close()
    over(panel, focus, after, dismissible, dim, centre)
  }

  /** Put `panel` up on top of whatever dialog is up, which is there again when it closes. A sheet that does not `dim`
    * leaves what it covers as it was (a menu over a page), and one that does not `centre` leaves the panel where the
    * caller puts it. */
  def over(panel: Container, focus: Component, after: () => Unit = () => (), dismissible: Boolean = true,
           dim: Boolean = true, centre: Boolean = true): Unit = {
    val sheet = new Container {
      size.width := ui.size.width
      size.height := ui.size.height
      if (dim) backgroundColor := Theme.backdrop()
      if (centre) {
        panel.position.center := size.center
        panel.position.middle := size.middle
      }
      children += panel
    }
    val layer = new ModalLayer(sheet, panel, Focus.focused(), after, focus, dismissible)
    sheet.event.click.attach(_ => if (layer.dismissible && layers.headOption.exists(_ eq layer)) take(layer))
    panel.event.click.attach(_.consume())
    // Back is this dialog's while it is on top and in view: it closes it, or — where it cannot be dismissed — does
    // nothing at all, rather than reaching whatever is under it
    layer.release = Focus.onBack(_ =>
      if (!Focus.showing(sheet)) {
        forget()
        false
      }
      else if (covered() || !layers.headOption.exists(_ eq layer)) false
      else {
        if (layer.dismissible) take(layer)
        true
      })
    // on the screen before it is on the stack, which lets go of any dialog whose sheet is not
    ui.children += sheet
    layers = layer :: layers
    // Put up under a cover, it waits there rather than appearing over it: a dialog raised while the screensaver is
    // running is one the viewer is not present for, and it belongs to the screen they left, not to the picture.
    if (covered()) sheet.visible @= false
    else {
      Focus.scope @= Some(sheet)
      focus.focus()
    }
  }

  /** Take down the dialog on top: the one a button in it closes. */
  def close(): Unit = layers.headOption.foreach(take)

  /** Take down the dialog that is `panel`, wherever it is in the stack — what put up a dialog that cannot be
    * dismissed takes it down this way, without closing whatever may have been put up over it since. */
  def remove(panel: Container): Unit = layers.find(_.panel eq panel).foreach(take)

  private def take(layer: ModalLayer): Unit = {
    val top = layers.headOption.exists(_ eq layer)
    // the one directly above it, if any, now goes back to where this one would have
    var above: Option[ModalLayer] = None
    var rest = layers
    while (rest.nonEmpty && !(rest.head eq layer)) {
      above = Some(rest.head)
      rest = rest.tail
    }
    above.foreach(_.before = layer.before)
    layers = layers.filterNot(_ eq layer)
    layer.release()
    ui.children -= layer.sheet
    if (top) {
      Focus.scope @= (if (covered()) None else layers.headOption.map(_.sheet))
      // back where it was; or, where that has left the screen meanwhile (a card taken off a row read again under the
      // dialog), on what took its place, rather than on nothing. Not under a cover, whose own screen has the focus
      if (!covered()) Focus.restore(layer.before)
    }
    layer.after()
  }
}
