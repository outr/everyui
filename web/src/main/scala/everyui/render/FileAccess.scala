package everyui.render

import everyui.async.Task
import everyui.file.{FileTypes, PickedFile}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Int8Array}

/** Files from the person (`everyui.file.Files`): the browser's file input for a pick, and the page's drag and drop.
  * A picture's `preview` is an object URL for the file itself, which the page keeps while it is open. */
object FileAccess {
  def pickable(): Boolean = true

  def droppable(): Boolean = true

  /** A hidden `<input type=file>` clicked: its `change` answers with what was chosen, its `cancel` with nothing. */
  def pick(types: List[String], multiple: Boolean, done: List[PickedFile] => Unit): Unit = {
    val input = dom.document.createElement("input").asInstanceOf[html.Input]
    input.`type` = "file"
    if (types.nonEmpty) input.accept = types.mkString(",")
    input.multiple = multiple
    input.style.display = "none"
    var answered = false
    def answer(files: List[PickedFile]): Unit = if (!answered) {
      answered = true
      if (input.parentNode != null) input.parentNode.removeChild(input)
      done(files)
    }
    input.addEventListener("change", (_: dom.Event) => answer(list(input.files)))
    input.addEventListener("cancel", (_: dom.Event) => answer(Nil))
    dom.document.body.appendChild(input)
    input.click()
  }

  /** Files dragged over the node from outside the page. Anything else dragged (words, a link, a list's own rows) is
    * not a drop here. Once a page has somewhere to drop files, a file let go anywhere else is kept from the browser,
    * which would otherwise leave the app to show it. */
  def onDrop(node: RenderNode, hover: Boolean => Unit, dropped: List[PickedFile] => Unit): Unit = {
    guard()
    val el = node.el
    // entering a child is leaving the parent to the browser: counted, so the hover ends only when the drag has left
    var depth = 0
    el.addEventListener("dragenter", (e: dom.DragEvent) => if (carriesFiles(e)) {
      e.preventDefault()
      depth += 1
      if (depth == 1) hover(true)
    })
    el.addEventListener("dragover", (e: dom.DragEvent) => if (carriesFiles(e)) {
      e.preventDefault()
      e.dataTransfer.dropEffect = dom.DataTransferDropEffectKind.copy
    })
    el.addEventListener("dragleave", (e: dom.DragEvent) => if (carriesFiles(e)) {
      depth -= 1
      if (depth <= 0) {
        depth = 0
        hover(false)
      }
    })
    el.addEventListener("drop", (e: dom.DragEvent) => if (carriesFiles(e)) {
      e.preventDefault()
      e.stopPropagation()
      depth = 0
      hover(false)
      dropped(list(e.dataTransfer.files))
    })
  }

  private var guarded = false

  private def guard(): Unit = if (!guarded) {
    guarded = true
    dom.document.addEventListener("dragover", (e: dom.DragEvent) => if (carriesFiles(e)) e.preventDefault())
    dom.document.addEventListener("drop", (e: dom.DragEvent) => if (carriesFiles(e)) e.preventDefault())
  }

  private def carriesFiles(e: dom.DragEvent): Boolean = e.dataTransfer != null && {
    val types = e.dataTransfer.types
    (0 until types.length).exists(i => types(i) == "Files")
  }

  private def list(files: dom.FileList): List[PickedFile] =
    if (files == null) Nil else (0 until files.length).toList.map(i => picked(files(i)))

  private def picked(f: dom.File): PickedFile = {
    val mime = if (f.`type`.isEmpty) FileTypes.of(f.name) else f.`type`
    val bytes = Task.async[Array[Byte]] { done =>
      f.asInstanceOf[js.Dynamic].arrayBuffer().asInstanceOf[js.Promise[ArrayBuffer]]
        .`then`[Unit]((b: ArrayBuffer) => done(new Int8Array(b).toArray))
      ()
    }
    val preview = if (mime.startsWith("image/")) Some(dom.URL.createObjectURL(f)) else None
    PickedFile(f.name, f.size.toLong, mime, bytes, preview)
  }
}
