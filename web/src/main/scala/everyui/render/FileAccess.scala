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
    val blob = blobOf(bytes, mime)
    toPicked(name, mime, () => download(objectUrl(blob), name, done), done)(file => written(file, blob))
  }

  /** Fetched into the file the picker chose, as it arrives; without a picker, the browser downloads it itself. */
  def saveUrl(url: String, name: String, mime: String, done: SaveResult => Unit): Unit =
    toPicked(name, mime, () => download(url, name, done), done)(file => dom.fetch(url).`then`[Any] { (r: dom.Response) =>
      if (!r.ok) js.Promise.reject(r.status.toString + " " + r.statusText)
      else r.body.asInstanceOf[js.Dynamic].pipeTo(file).asInstanceOf[js.Promise[Any]]
    })

  /** The picker first, while the click that asked is still fresh; then the bytes made and written into the file chosen.
    * Without a picker they are made first, and downloaded. */
  def saveLater(name: String, mime: String, bytes: Task[Either[String, Array[Byte]]], done: SaveResult => Unit): Unit = {
    val downloaded = () => bytes.map {
      case Right(made) => download(objectUrl(blobOf(made, mime)), name, done)
      case Left(why) => done(SaveResult.SaveFailed(why))
    }.start()
    toPicked(name, mime, downloaded, done) { file =>
      new js.Promise[Array[Byte]]((resolve, reject) => bytes.map {
        case Right(made) => resolve(made)
        case Left(why) => reject(why)
      }.start()).`then`[Any]((made: Array[Byte]) => written(file, blobOf(made, mime)))
    }
  }

  private def blobOf(bytes: Array[Byte], mime: String): dom.Blob =
    new dom.Blob(js.Array(bytes.toTypedArray.buffer), new dom.BlobPropertyBag { `type` = mime })

  /** `blob` written into the file chosen, which is then closed. */
  private def written(file: js.Dynamic, blob: dom.Blob): js.Promise[Any] =
    file.write(blob).asInstanceOf[js.Promise[Any]].`then`[Any]((_: Any) => file.close().asInstanceOf[js.Promise[Any]])

  /** An object URL for `blob`, kept until the browser has started its download from it. */
  private def objectUrl(blob: dom.Blob): String = {
    val url = dom.URL.createObjectURL(blob)
    dom.window.setTimeout(() => dom.URL.revokeObjectURL(url), 60000)
    url
  }

  /** `href` downloaded as `name`, which the browser says no more about. */
  private def download(href: String, name: String, done: SaveResult => Unit): Unit = {
    val a = dom.document.createElement("a").asInstanceOf[html.Anchor]
    a.href = href
    a.setAttribute("download", name)
    a.style.display = "none"
    dom.document.body.appendChild(a)
    a.click()
    dom.document.body.removeChild(a)
    done(SaveResult.Saved(None))
  }

  /** The save picker, suggesting `name`, and `write` into the file chosen (which closes it). Without a picker, or
    * where it may not open now (no click to open it from, a type it will not take), `download` instead. */
  private def toPicked(name: String, mime: String, download: () => Unit, done: SaveResult => Unit)
                      (write: js.Dynamic => js.Promise[Any]): Unit = {
    val window = dom.window.asInstanceOf[js.Dynamic]
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
