package everyui.render

import org.scalajs.dom

import scala.scalajs.js

/** A mouse's back and forward buttons (`MouseEvent.button` 3 and 4) as keys, "BrowserBack" and "BrowserForward", told
  * to the key hook (`Platform.onKey`), where a keymap's chord can take them (`Chord("BrowserBack")`); never a press on
  * what is under the pointer.
  *
  * Heard on a window before anything in it (the capture phase), so an editor in the page (Monaco, a terminal) never
  * keeps them: the page's own, and each frame of the app's own origin (WebFrame), which has a window of its own. A
  * browser goes back or forward as the button is let go unless the page prevents the release (its `mouseup` or
  * `pointerup`: Chromium goes all the same for a press or an `auxclick` prevented), so where the app took the press
  * its release is prevented too, and the page stays. A frame of another origin, or a sandboxed one, cannot be listened
  * in: over one, the button is the browser's. */
object NavButtons {
  private var hook: (String, Boolean, dom.MouseEvent) => Boolean = (_, _, _) => false
  // whether the app took the last press, so its release is kept from the browser too
  private var taken = false

  /** Where the buttons go: their key's name, whether pressed, and the event (its modifiers). Whether it was taken. */
  def hear(f: (String, Boolean, dom.MouseEvent) => Boolean): Unit = hook = f

  /** Whether `e` is the back or the forward button's. */
  def side(e: dom.MouseEvent): Boolean = e.button == 3 || e.button == 4

  /** The key `e`'s button is. */
  def name(e: dom.MouseEvent): String = if (e.button == 3) "BrowserBack" else "BrowserForward"

  /** Listen on `window`, once however often asked. */
  def listen(window: dom.Window): Unit = {
    val marked = window.asInstanceOf[js.Dynamic]
    if (js.isUndefined(marked.everyuiNavButtons)) {
      marked.everyuiNavButtons = true
      def on(kind: String)(f: dom.MouseEvent => Unit): Unit =
        window.addEventListener(kind, (e: dom.MouseEvent) => if (side(e)) {
          e.stopPropagation()
          f(e)
        }, true)
      on("mousedown") { e =>
        taken = hook(name(e), true, e)
        if (taken) e.preventDefault()
      }
      on("mouseup") { e =>
        hook(name(e), false, e)
        if (taken) e.preventDefault()
      }
      on("auxclick")(e => if (taken) e.preventDefault())
      on("pointerup")(e => if (taken) e.preventDefault())
      // (a press prevented would lose the mouse's events, which are what is heard)
      on("pointerdown")(_ => ())
    }
  }
}
