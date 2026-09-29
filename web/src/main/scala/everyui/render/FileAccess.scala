package everyui.render

import everyui.async.Task
import everyui.file.{FileTypes, PickedFile, SaveResult}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.typedarray.*

/** Files from the person (`everyui.file.Files`): the browser's file input for a pick, and the page's drag and drop.
  * A picture's `preview` is an object URL for the file itself, which the page keeps while it is open. A save goes to
  * the File System Access API's save picker where the browser has one (Chromium's), and otherwise to its downloads. */
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

  /** Done with a picked file's `preview`: its object URL let go, and the file with it. */
  def release(preview: String): Unit = if (preview.startsWith("blob:")) dom.URL.revokeObjectURL(preview)

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

  def savable(): Boolean = true

  /** The bytes as a Blob: written to the file the picker chose, or downloaded. */
  def save(bytes: Array[Byte], name: String, mime: String, done: SaveResult => Unit): Unit = {
    val blob = new dom.Blob(js.Array(bytes.toTypedArray.buffer), new dom.BlobPropertyBag { `type` = mime })
    val href = () => {
      val url = dom.URL.createObjectURL(blob)
      // kept until the browser has started its download from it
      dom.window.setTimeout(() => dom.URL.revokeObjectURL(url), 60000)
      url
    }
    toPicked(name, mime, href, done)(file => file.write(blob).asInstanceOf[js.Promise[Any]]
      .`then`[Any]((_: Any) => file.close().asInstanceOf[js.Promise[Any]]))
  }

  /** Fetched into the file the picker chose, as it arrives; without a picker, the browser downloads it itself. */
  def saveUrl(url: String, name: String, mime: String, done: SaveResult => Unit): Unit =
    toPicked(name, mime, () => url, done)(file => dom.fetch(url).`then`[Any] { (r: dom.Response) =>
      if (!r.ok) js.Promise.reject(r.status.toString + " " + r.statusText)
      else r.body.asInstanceOf[js.Dynamic].pipeTo(file).asInstanceOf[js.Promise[Any]]
    })

  /** The save picker, suggesting `name`, and `write` into the file chosen (which closes it). Without a picker, or
    * where it may not open now (no click to open it from, a type it will not take), the file is downloaded from
    * `href` instead, which the browser says no more about. */
  private def toPicked(name: String, mime: String, href: () => String, done: SaveResult => Unit)
                      (write: js.Dynamic => js.Promise[Any]): Unit = {
    val window = dom.window.asInstanceOf[js.Dynamic]
    val download = () => {
      val a = dom.document.createElement("a").asInstanceOf[html.Anchor]
      a.href = href()
      a.setAttribute("download", name)
      a.style.display = "none"
      dom.document.body.appendChild(a)
      a.click()
      dom.document.body.removeChild(a)
      done(SaveResult.Saved(None))
    }
    if (js.typeOf(window.showSaveFilePicker) != "function") download()
    else {
      val options = js.Dynamic.literal(suggestedName = name)
      val extension = FileTypes.extension(name)
      if (extension.nonEmpty && mime.contains("/"))
        options.types = js.Array(js.Dynamic.literal(accept = js.Dictionary(mime -> js.Array("." + extension))))
      var chosen: Option[js.Dynamic] = None
      var writing: Option[js.Dynamic] = None
      window.showSaveFilePicker(options).asInstanceOf[js.Promise[js.Dynamic]]
        .`then`[js.Dynamic] { (handle: js.Dynamic) =>
          chosen = Some(handle)
          handle.createWritable().asInstanceOf[js.Promise[js.Dynamic]]
        }
        .`then`[Any] { (file: js.Dynamic) =>
          writing = Some(file)
          write(file)
        }
        .`then`[Unit]((_: Any) => done(SaveResult.Saved(chosen.map(h => String.valueOf(h.name)))))
        .`catch`[Unit] { (e: Any) =>
          val kind = WebErrors.name(e)
          // what was written so far is let go: the file keeps what it had
          writing.foreach(w => w.abort())
          if (chosen.isEmpty && kind == "AbortError") done(SaveResult.Cancelled)
          else if (chosen.isEmpty && (kind == "SecurityError" || kind == "NotAllowedError" || kind == "TypeError")) download()
          else done(SaveResult.SaveFailed(WebErrors.message(e)))
        }
    }
  }
}
