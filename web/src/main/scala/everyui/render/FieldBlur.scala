package everyui.render

import everyui.focus.Focus
import org.scalajs.dom

/** A field's focus lost to nothing on the page by the person's doing, told to the focus layer (`Focus.dropped`): a
  * click on the page's empty space, which the browser answers by focusing nothing. Only a blur that leaves the page
  * focused with nothing in it is that, once it has settled: not one to another field or into a frame (which has a
  * related target, or is the page's active element after it), nor the window or the tab left (the page no longer has
  * the focus, and gives it back to the field as it comes back), nor one the app made (`blurring`), nor a field taken
  * off the page and put back, whose focus its container puts back as it places it (Component.refocus). A blur by a
  * press is looked at once the press has been let go and its click heard: the press may be a button's, whose click
  * focuses it, and a menu dropped from it goes back to the field it took the focus from (FocusMoves.before). */
object FieldBlur {
  /** Whether the app is taking the focus from a field itself (`RenderNode.focusInput`). */
  var blurring: Boolean = false
  private var pressed: Boolean = false
  private var waiting: List[() => Unit] = Nil
  private var listening: Boolean = false

  private def listen(): Unit = if (!listening) {
    listening = true
    dom.window.addEventListener("pointerdown", (_: dom.Event) => pressed = true, true)
    List("pointerup", "pointercancel").foreach(name => dom.window.addEventListener(name, (_: dom.Event) => {
      pressed = false
      val now = waiting
      waiting = Nil
      // after the press's click, which comes in the same turn as its release
      now.foreach(f => dom.window.setTimeout(() => f(), 0))
    }, true))
  }

  /** `el`, a field, watched for its focus lost by the person's doing. */
  def watch(el: dom.html.Element): Unit = {
    listen()
    el.addEventListener("blur", (e: dom.FocusEvent) => if (!blurring && e.relatedTarget == null) {
      val settled = () => {
        val active = dom.document.activeElement
        if (el.isConnected && dom.document.hasFocus() && (active == null || active == dom.document.body))
          Focus.focused().foreach(c => if (c.node.el.contains(el)) Focus.dropped(c))
      }
      if (pressed) waiting = settled :: waiting else dom.window.setTimeout(() => settled(), 0)
    })
  }
}
