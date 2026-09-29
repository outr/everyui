package everyui.render

import android.content.{ActivityNotFoundException, ClipDescription, Intent}
import android.net.Uri
import android.provider.OpenableColumns
import android.view.{DragEvent, View}
import android.webkit.MimeTypeMap
import everyui.async.Task
import everyui.file.{FileTypes, PickedFile}

/** Files from the person (`everyui.file.Files`): the system's document picker for a pick, answered through the
  * activity's result (`EveryUIActivity.onActivityResult` hands it to [[answered]]), and files dragged onto a view from
  * another app (a tablet's split screen, a desktop window). A file is read through the content resolver on a thread of
  * its own and answered on the one that draws; a picture's `preview` is its content address, which pictures load
  * scaled down. A television has no document picker to speak of, so it offers none. */
object FileAccess {
  /** The request code the picker's answer comes back under. */
  val request: Int = 7300
  private var waiting: Option[List[PickedFile] => Unit] = None

  def pickable(): Boolean = !Platform.remoteNavigation()

  def droppable(): Boolean = !Platform.remoteNavigation()

  /** The system's picker (ACTION_OPEN_DOCUMENT), offering files of `types`. */
  def pick(types: List[String], multiple: Boolean, done: List[PickedFile] => Unit): Unit = {
    waiting.foreach(w => w(Nil))
    val mimes = types.map(t => if (t.startsWith(".")) mimeOf(t.substring(1)) else t).distinct
    val intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.setType(if (mimes.length == 1) mimes.head else "*/*")
    if (mimes.length > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray)
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
    waiting = Some(done)
    try Host.activity.startActivityForResult(intent, request)
    catch {
      case t: ActivityNotFoundException =>
        Platform.logError("No document picker", t)
        answered(request, android.app.Activity.RESULT_CANCELED, null)
    }
  }

  /** The activity's result for `code`: whether it was the picker's. */
  def answered(code: Int, result: Int, data: Intent): Boolean = code == request && {
    val chosen =
      if (result != android.app.Activity.RESULT_OK || data == null) Nil
      else if (data.getClipData != null) {
        val clip = data.getClipData
        (0 until clip.getItemCount).toList.flatMap(i => Option(clip.getItemAt(i).getUri))
      }
      else Option(data.getData).toList
    val done = waiting
    waiting = None
    done.foreach(d => d(chosen.map(picked)))
    true
  }

  /** Files dragged over the view from another app; anything else dragged is not a drop here. */
  def onDrop(node: RenderNode, hover: Boolean => Unit, dropped: List[PickedFile] => Unit): Unit =
    node.view.setOnDragListener((_: View, e: DragEvent) => e.getAction match {
      case DragEvent.ACTION_DRAG_STARTED => carriesFiles(e.getClipDescription)
      case DragEvent.ACTION_DRAG_ENTERED =>
        hover(true)
        true
      case DragEvent.ACTION_DRAG_EXITED =>
        hover(false)
        true
      case DragEvent.ACTION_DROP =>
        hover(false)
        // the other app's files, readable for as long as this drop is being handled and what it starts
        if (android.os.Build.VERSION.SDK_INT >= 24) Host.activity.requestDragAndDropPermissions(e)
        val clip = e.getClipData
        val uris = if (clip == null) Nil else (0 until clip.getItemCount).toList.flatMap(i => Option(clip.getItemAt(i).getUri))
        dropped(uris.map(picked))
        uris.nonEmpty
      case _ => true
    })

  private def carriesFiles(d: ClipDescription): Boolean = d != null &&
    (0 until d.getMimeTypeCount).exists(i => d.getMimeType(i) != ClipDescription.MIMETYPE_TEXT_PLAIN &&
      d.getMimeType(i) != ClipDescription.MIMETYPE_TEXT_HTML && d.getMimeType(i) != ClipDescription.MIMETYPE_TEXT_INTENT)

  private def mimeOf(extension: String): String =
    Option(MimeTypeMap.getSingleton.getMimeTypeFromExtension(extension)).getOrElse(FileTypes.of("x." + extension))

  /** A document as the shared layer takes one: its name and size as the provider gives them. */
  private def picked(uri: Uri): PickedFile = {
    val resolver = Host.activity.getContentResolver
    var name = Option(uri.getLastPathSegment).getOrElse("file")
    var size = 0L
    try {
      val cursor = resolver.query(uri, Array(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
      if (cursor != null) try {
        if (cursor.moveToFirst()) {
          if (!cursor.isNull(0)) name = cursor.getString(0)
          if (!cursor.isNull(1)) size = cursor.getLong(1)
        }
      } finally cursor.close()
    } catch { case t: Exception => Platform.logError("Reading about " + uri, t) }
    val mime = Option(resolver.getType(uri)).getOrElse(FileTypes.of(name))
    val bytes = Task.async[Array[Byte]] { done =>
      Platform.offThread(() => {
        val in = resolver.openInputStream(uri)
        val out = new java.io.ByteArrayOutputStream()
        try {
          val buffer = new Array[Byte](64 * 1024)
          var read = in.read(buffer)
          while (read >= 0) {
            out.write(buffer, 0, read)
            read = in.read(buffer)
          }
        } finally in.close()
        Platform.later(() => done(out.toByteArray))
      })
    }
    PickedFile(name, size, mime, bytes, if (mime.startsWith("image/")) Some(uri.toString) else None)
  }
}
