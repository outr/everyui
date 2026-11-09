package everyui.render

import everyui.component.types.PasteAction
import everyui.file.{FileTypes, PickedFile}
import org.scalajs.dom

import scala.scalajs.js

/** A field's pastes put to the app before they land (`InputView.pasted`), by the `paste` event, which the keyboard's
  * paste, the context menu's and a phone's Paste all fire with the clipboard's words. A paste with no words (a
  * picture) goes on as it would, its pictures and files handed over where the field takes them (`files`). Words put in
  * its place are inserted as typing is (`insertText`), so the browser's undo takes them back as it would the paste;
  * where a browser will not, they are set in place of the selection and the edit is said as typing says it. A phone
  * keyboard's own clipboard suggestion is typed rather than pasted, and is never asked about. Nor is a paste into a
  * read-only field, which takes none. */
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

  /** The pictures and files of a paste into `el`, handed to `f` where `wanted` says the field takes them: the paste
    * event's files (a picture a screenshot tool or an image viewer copied, which Chrome and Firefox on Linux offer as
    * image/png, and a file a file manager copied, where the browser hands it over), else its items that are files. A
    * picture the browser names only by its type ("image.png", as Chrome and Firefox name a copied bitmap) is handed
    * with no name, for the field to number. The paste's own landing is kept from happening only where there are no
    * words beside them, which land (or not) as `listen` has it. A read-only field takes none. */
  def files(el: dom.Element, wanted: () => Boolean, f: List[PickedFile] => Unit): Unit =
    el.addEventListener("paste", (e: dom.ClipboardEvent) => {
      val data = e.clipboardData
      val fixed = el.asInstanceOf[js.Dynamic].readOnly.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false)
      if (data != null && !js.isUndefined(data) && !fixed && wanted()) {
        val taken = carried(data.asInstanceOf[js.Dynamic])
        if (taken.nonEmpty) {
          if (data.getData("text/plain").isEmpty) e.preventDefault()
          f(taken.map(FileAccess.picked).map(p => if (generic(p)) p.copy(name = "") else p))
        }
      }
    })

  // the paste's files, else its items that are files
  private def carried(data: js.Dynamic): List[dom.File] = {
    val files = data.files
    val listed =
      if (files == null || js.isUndefined(files)) Nil
      else (0 until files.length.asInstanceOf[Int]).toList.map(i => files.item(i).asInstanceOf[dom.File])
    if (listed.nonEmpty) listed
    else {
      val items = data.items
      if (items == null || js.isUndefined(items)) Nil
      else (0 until items.length.asInstanceOf[Int]).toList.map(i => items.asInstanceOf[js.Array[js.Dynamic]](i))
        .filter(_.kind.asInstanceOf[String] == "file").flatMap(i => Option(i.getAsFile().asInstanceOf[dom.File]))
    }
  }

  // a picture named only by its type, as a browser names a copied bitmap
  private def generic(p: PickedFile): Boolean =
    p.mime.startsWith("image/") && (p.name.isEmpty || p.name == "image." + FileTypes.extension(p.name))

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
