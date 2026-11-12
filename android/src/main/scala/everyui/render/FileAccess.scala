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

  /** A pasted document (a clipboard's `content:` address, a keyboard's picture) read whole on a thread of its own, its
    * permission let go of once it is (`release`), and handed to `done` on the one that draws: its name as the provider
    * gives it (none where it gives none, for the field to number), its type `mime` where the provider says none, and a
    * picture's preview a file of the app's own (`source`), as the address may not be readable after. Nothing is handed
    * over where it cannot be read. */
  private[render] def pasted(uri: Uri, mime: String, release: () => Unit)(done: PickedFile => Unit): Unit =
    Platform.offThread(() => {
      val resolver = Host.activity.getContentResolver
      val read = try {
        var name = ""
        try {
          val cursor = resolver.query(uri, Array(OpenableColumns.DISPLAY_NAME), null, null, null)
          if (cursor != null) try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0)
          } finally cursor.close()
        } catch { case _: Exception => () }
        val kind = Option(resolver.getType(uri)).getOrElse(mime)
        val in = resolver.openInputStream(uri)
        val out = new java.io.ByteArrayOutputStream()
        try {
          val buffer = new Array[Byte](64 * 1024)
          var n = in.read(buffer)
          while (n >= 0) {
            out.write(buffer, 0, n)
            n = in.read(buffer)
          }
        } finally in.close()
        Some((name, kind, out.toByteArray))
      } catch { case t: Exception => Platform.logError("Reading the pasted " + uri, t); None }
      finally try release() catch { case _: Exception => () }
      read.foreach { case (name, kind, bytes) => Platform.later(() => done(PickedFile(name, bytes.length.toLong, kind,
        Task.pure(bytes), if (kind.startsWith("image/")) Some(source(bytes, kind)) else None)))
      }
    })

  /** Done with a picked file's `preview` (the content's own address, which holds nothing), or a `source`, whose file
    * is deleted. */
  def release(preview: String): Unit = sources.get(preview).foreach { file =>
    sources = sources - preview
    mimes = mimes - preview
    file.delete()
  }

  /** `bytes` written to a file in the app's cache (deleted by `release`, and with the rest as the app starts again),
    * as its address: a picture and a player read it as a file; a WebView is handed what it holds (WebSandbox.load), as
    * it reads no file sandboxed. */
  def source(bytes: Array[Byte], mime: String): String = {
    val file = java.io.File.createTempFile("everyui-", "." + FileTypes.extensionOf(mime), sourceDir)
    Host.writeFile(file, bytes)
    val address = android.net.Uri.fromFile(file).toString
    sources = sources + (address -> file)
    mimes = mimes + (address -> mime)
    address
  }

  /** What a `source` holds, and its type, where `address` is one. */
  private[render] def held(address: String): Option[(Array[Byte], String)] = sources.get(address).flatMap { file =>
    try Some((Host.fileBytes(file), mimes.getOrElse(address, "text/html")))
    catch { case _: java.io.IOException => None }
  }

  // the files of the last run gone as this one makes its first
  private lazy val sourceDir: java.io.File = {
    val dir = new java.io.File(Host.activity.getCacheDir, "everyui-sources")
    Option(dir.listFiles()).foreach(_.foreach(_.delete()))
    dir.mkdirs()
    dir
  }
  private var sources: Map[String, java.io.File] = Map.empty
  private var mimes: Map[String, String] = Map.empty

  def savable(): Boolean = !Platform.remoteNavigation()

  def save(bytes: Array[Byte], name: String, mime: String, done: SaveResult => Unit): Unit =
    create(name, mime, done)(out => out.write(bytes))

  /** A download is a save here: there are no downloads apart from it. */
  def download(bytes: Array[Byte], name: String, mime: String, done: SaveResult => Unit): Unit =
    save(bytes, name, mime, done)

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

  /** The document created, written a piece at a time: the pieces made on the thread that draws are handed, one by one,
    * to the writing off it, each told once it is written; what `write` answers at the end keeps the document, or
    * deletes it. */
  def saveStream(name: String, mime: String, write: everyui.file.FileSink => Task[Either[String, Unit]],
                 done: SaveResult => Unit): Unit =
    create(name, mime, done) { out =>
      // a piece to write and who to tell, or the end and what it answered
      val handed = new java.util.concurrent.LinkedBlockingQueue[Either[Either[String, Unit], (Array[Byte], Boolean => Unit)]]()
       var broken = false
      val sink = new everyui.file.FileSink {
        def write(bytes: Array[Byte]): Task[Boolean] =
          if (broken) Task.pure(false) else Task.async[Boolean](told => handed.put(Right((bytes, told))))
      }
      Platform.later(() => write(sink).map(answer => handed.put(Left(answer))).start())
      var end: Option[Either[String, Unit]] = None
      while (end.isEmpty) handed.take() match {
        case Left(answer) => end = Some(answer)
        case Right((bytes, told)) =>
          val ok = !broken && (try {
            out.write(bytes)
            true
          } catch { case _: java.io.IOException => false })
          if (!ok) broken = true
          Platform.later(() => told(ok))
      }
      end match {
        case Some(Left(why)) => throw new java.io.IOException(why)
        case _ => if (broken) throw new java.io.IOException("The file could not be written")
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
