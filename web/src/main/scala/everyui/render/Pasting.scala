package everyui.render

import everyui.component.types.PasteAction
import org.scalajs.dom

import scala.scalajs.js

/** A field's pastes put to the app before they land (`InputView.pasted`), by the `paste` event, which the keyboard's
  * paste, the context menu's and a phone's Paste all fire with the clipboard's words. A paste with no words (a
  * picture) goes on as it would. Words put in its place are inserted as typing is (`insertText`), so the browser's undo
  * takes them back as it would the paste; where a browser will not, they are set in place of the selection and the
  * edit is said as typing says it. A phone keyboard's own clipboard suggestion is typed rather than pasted, and is
  * never asked about. Nor is a paste into a read-only field, which takes none. */
object Pasting {
  def listen(el: dom.Element, f: String => PasteAction): Unit = el.addEventListener("paste", (e: dom.ClipboardEvent) => {
    val data = e.clipboardData
    val words = if (data == null || js.isUndefined(data)) "" else data.getData("text/plain")
    // a read-only field takes no paste, and its app is not asked about one
    val fixed = el.asInstanceOf[js.Dynamic].readOnly.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false)
    if (words.nonEmpty && !fixed) f(words) match {
      case PasteAction.Insert => ()
      case PasteAction.Ignore => e.preventDefault()
      case PasteAction.InsertText(text) =>
        e.preventDefault()
        insert(el, text)
    }
  })

  // `text` in place of the selection, on the field's undo stack where the browser lets it be
  private def insert(el: dom.Element, text: String): Unit = {
    val typed = dom.document.activeElement == el &&
      dom.document.asInstanceOf[js.Dynamic].execCommand("insertText", false, text).asInstanceOf[Boolean]
    if (!typed) {
      val field = el.asInstanceOf[js.Dynamic]
      field.setRangeText(text, field.selectionStart, field.selectionEnd, "end")
      el.dispatchEvent(new dom.Event("input", new dom.EventInit { bubbles = true }))
    }
  }
}
