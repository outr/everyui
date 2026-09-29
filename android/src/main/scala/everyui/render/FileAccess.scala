package everyui.render

import android.content.{ActivityNotFoundException, ClipDescription, Intent}
import android.net.Uri
import android.provider.OpenableColumns
import android.view.{DragEvent, View}
import android.webkit.MimeTypeMap
import everyui.async.Task
import everyui.file.{FileTypes, PickedFile, SaveResult}

/** Files from the person (`everyui.file.Files`): the system's document picker for a pick, answered through the
  * activity's result (`EveryUIActivity.onActivityResult` hands it to [[answered]]), and files dragged onto a view from
  * another app (a tablet's split screen, a desktop window). A save goes to the system's document creator
  * (ACTION_CREATE_DOCUMENT), answered the same way. A file is read or written through the content resolver on a thread
  * of its own and answered on the one that draws; a picture's `preview` is its content address, which pictures load
  * scaled down. A television has no document picker to speak of, so it offers none. */
object FileAccess {
  /** The request code the picker's answer comes back under. */
  val request: Int = 7300
  /** The request code the document creator's answer comes back under. */
  val saveRequest: Int = 7301
  private var waiting: Option[List[PickedFile] => Unit] = None
  private var saving: Option[(java.io.OutputStream => Unit, SaveResult => Unit)] = None

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

  /** The activity's result for `code`: whether it was the picker's or the document creator's. */
  def answered(code: Int, result: Int, data: Intent): Boolean =
    if (code == request) {
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
    else if (code == saveRequest) {
      val save = saving
      saving = None
      val created = if (result == android.app.Activity.RESULT_OK && data != null) Option(data.getData) else None
      save.foreach { case (write, done) =>
        created match {
          case None => done(SaveResult.Cancelled)
          case Some(uri) => written(uri, write, done)
        }
      }
      true
    }
    else false

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

  /** Done with a picked file's `preview`: nothing is held for one here. */
  def release(preview: String): Unit = ()

  def savable(): Boolean = !Platform.remoteNavigation()

  def save(bytes: Array[Byte], name: String, mime: String, done: SaveResult => Unit): Unit =
    create(name, mime, done)(out => out.write(bytes))

  /** Fetched straight into the document created, as it arrives. */
  def saveUrl(url: String, name: String, mime: String, done: SaveResult => Unit): Unit =
    create(name, mime, done) { out =>
      val in = new java.net.URI(url).toURL.openStream()
      try {
        val buffer = new Array[Byte](64 * 1024)
        var read = in.read(buffer)
        while (read >= 0) {
          out.write(buffer, 0, read)
          read = in.read(buffer)
        }
      } finally in.close()
    }

  /** The bytes made once the document is created, and written into it (waited for off the thread that draws); a
    * document they fail for is deleted again. */
  def saveLater(name: String, mime: String, bytes: Task[Either[String, Array[Byte]]], done: SaveResult => Unit): Unit =
    create(name, mime, done) { out =>
      val made = new java.util.concurrent.CountDownLatch(1)
      var answer: Either[String, Array[Byte]] = Left("No bytes")
      bytes.map { b =>
        answer = b
        made.countDown()
      }.start()
      made.await()
      answer match {
        case Left(why) => throw new java.io.IOException(why)
        case Right(b) => out.write(b)
      }
    }

  /** The system's document creator, suggesting `name` of type `mime`; what the person creates is written by `write`. */
  private def create(name: String, mime: String, done: SaveResult => Unit)(write: java.io.OutputStream => Unit): Unit = {
    saving.foreach(s => s._2(SaveResult.Cancelled))
    val intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.setType(mime)
    intent.putExtra(Intent.EXTRA_TITLE, name)
    saving = Some((write, done))
    try Host.activity.startActivityForResult(intent, saveRequest)
    catch {
      case t: ActivityNotFoundException =>
        Platform.logError("No document creator", t)
        saving = None
        done(SaveResult.SaveFailed("No document creator on this device"))
    }
  }

  /** `write` into the document at `uri`, off the thread that draws; a document it could not be written into is
    * deleted again rather than left empty. */
  private def written(uri: Uri, write: java.io.OutputStream => Unit, done: SaveResult => Unit): Unit =
    Platform.offThread(() => {
      val resolver = Host.activity.getContentResolver
      val result = try {
        val out = resolver.openOutputStream(uri, "wt")
        try write(out) finally out.close()
        SaveResult.Saved(Some(displayName(uri)))
      } catch {
        case t: Exception =>
          try android.provider.DocumentsContract.deleteDocument(resolver, uri) catch { case _: Exception => () }
          SaveResult.SaveFailed(Option(t.getMessage).getOrElse(t.getClass.getSimpleName))
      }
      Platform.later(() => done(result))
    })

  /** The name the provider gives the document at `uri`. */
  private def displayName(uri: Uri): String = {
    var name = Option(uri.getLastPathSegment).getOrElse("file")
    try {
      val cursor = Host.activity.getContentResolver.query(uri, Array(OpenableColumns.DISPLAY_NAME), null, null, null)
      if (cursor != null) try {
        if (cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0)
      } finally cursor.close()
    } catch { case t: Exception => Platform.logError("Reading about " + uri, t) }
    name
  }
}
