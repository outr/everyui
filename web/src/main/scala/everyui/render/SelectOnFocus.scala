package everyui.render

import org.scalajs.dom

/** A field whose words are all chosen as it takes the keyboard (`InputView.selectOnFocus`), as a browser's address bar
  * does. Tab, or the app putting the keyboard in it, chooses them at once. A press on it while it has not the keyboard
  * chooses them as its click lands, since the browser places the caret as the press goes down and moves it again as it
  * lets go, undoing a choice made then; a press that dragged out a range keeps that range, and a press once it has the
  * keyboard places the caret as ever. The keyboard given back rather than newly taken chooses nothing: the window or the
  * tab come back to, or the field put back on the page after it moved (`Component.refocus`), still being typed in. */
final class SelectOnFocus(el: dom.Element, select: () => Unit, collapsed: () => Boolean) {
  /** Whether it chooses all as it takes the keyboard. */
  var on: Boolean = false
  // a press that is giving it the keyboard, whose click chooses
  private var pressing = false
  // it has the keyboard, as far as it was told: a focus with no blur since is the keyboard given back
  private var holding = false
  // the keyboard lost only for now: the window or the tab left, or the field taken off the page
  private var kept = false

  el.addEventListener("pointerdown", (_: dom.Event) => pressing = !(dom.document.activeElement eq el))
  el.addEventListener("focus", (_: dom.Event) => {
    if (on && !pressing && !holding && !kept) select()
    holding = true
    kept = false
  })
  el.addEventListener("click", (_: dom.Event) => if (pressing) {
    pressing = false
    if (on && collapsed()) select()
  })
  el.addEventListener("blur", (_: dom.Event) => {
    pressing = false
    holding = false
    kept = (dom.document.activeElement eq el) || !el.isConnected
  })
}
