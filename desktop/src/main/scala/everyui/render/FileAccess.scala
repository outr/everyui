package everyui.render

import everyui.async.Task
import everyui.file.{FileTypes, PickedFile, SaveResult}
import javafx.scene.input.{DragEvent, TransferMode}
import javafx.stage.FileChooser

import scala.jdk.CollectionConverters.*

/** Files from the person (`everyui.file.Files`): JavaFX's file dialog for a pick, and files dragged onto the window
  * from the system's file manager, and its save dialog for a save. A file is read or written on a thread of its own and
  * answered on the one that draws; a picture's `preview` is the file's own address. */
object FileAccess {
  def pickable(): Boolean = true

  def droppable(): Boolean = true

  /** The system's open dialog over the window, filtered to `types` where they name extensions it knows. */
  def pick(types: List[String], multiple: Boolean, done: List[PickedFile] => Unit): Unit = {
    val chooser = new FileChooser()
    val extensions = FileTypes.extensions(types)
    if (extensions.nonEmpty) chooser.getExtensionFilters.add(
      new FileChooser.ExtensionFilter(extensions.map("*." + _).mkString(", "), extensions.map("*." + _).asJava))
    val window = if (Host.scene == null) null else Host.scene.getWindow
    val chosen =
      if (multiple) Option(chooser.showOpenMultipleDialog(window)).map(_.asScala.toList).getOrElse(Nil)
      else Option(chooser.showOpenDialog(window)).toList
    done(chosen.filter(_.isFile).map(picked))
  }

  /** Files dragged over the node from outside the window; anything else dragged is not a drop here. */
  def onDrop(node: RenderNode, hover: Boolean => Unit, dropped: List[PickedFile] => Unit): Unit = {
    val fx = node.fx
    fx.addEventHandler(DragEvent.DRAG_OVER, (e: DragEvent) => if (e.getDragboard.hasFiles) {
      e.acceptTransferModes(TransferMode.COPY)
      e.consume()
    })
    fx.addEventHandler(DragEvent.DRAG_ENTERED, (e: DragEvent) => if (e.getDragboard.hasFiles) hover(true))
    fx.addEventHandler(DragEvent.DRAG_EXITED, (e: DragEvent) => if (e.getDragboard.hasFiles) hover(false))
    fx.addEventHandler(DragEvent.DRAG_DROPPED, (e: DragEvent) => if (e.getDragboard.hasFiles) {
      val files = e.getDragboard.getFiles.asScala.toList.filter(_.isFile)
      e.setDropCompleted(true)
      e.consume()
      hover(false)
      dropped(files.map(picked))
    })
  }

  /** A file on the disk as the shared layer takes one. */
  def picked(file: java.io.File): PickedFile = {
    val probed = try Option(java.nio.file.Files.probeContentType(file.toPath)) catch { case _: Exception => None }
    val mime = probed.getOrElse(FileTypes.of(file.getName))
    val bytes = Task.async[Array[Byte]] { done =>
      Platform.offThread(() => {
        val read = java.nio.file.Files.readAllBytes(file.toPath)
        Platform.later(() => done(read))
      })
    }
    PickedFile(file.getName, file.length(), mime, bytes,
      if (mime.startsWith("image/")) Some(file.toPath.toUri.toString) else None)
  }

  /** Done with a picked file's `preview`: nothing is held for one here. */
  def release(preview: String): Unit = ()

  def savable(): Boolean = true

  def save(bytes: Array[Byte], name: String, mime: String, done: SaveResult => Unit): Unit =
    chooseAndWrite(name, done)(file => java.nio.file.Files.write(file.toPath, bytes))

  /** A download is a save here: there are no downloads apart from it. */
  def download(bytes: Array[Byte], name: String, mime: String, done: SaveResult => Unit): Unit =
    save(bytes, name, mime, done)

  /** Fetched straight into the file chosen, as it arrives. */
  def saveUrl(url: String, name: String, mime: String, done: SaveResult => Unit): Unit =
    chooseAndWrite(name, done) { file =>
      val connection = new java.net.URI(url).toURL.openConnection()
      val in = connection.getInputStream
      try java.nio.file.Files.copy(in, file.toPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      finally in.close()
    }

  /** The bytes made once the file is chosen, and written into it; nothing is written where they fail. */
  def saveLater(name: String, mime: String, bytes: Task[Either[String, Array[Byte]]], done: SaveResult => Unit): Unit =
    chooseSave(name) match {
      case None => done(SaveResult.Cancelled)
      case Some(file) => bytes.map {
        case Left(why) => Platform.later(() => done(SaveResult.SaveFailed(why)))
        case Right(made) => written(file, done)(f => java.nio.file.Files.write(f.toPath, made))
      }.start()
    }

  /** Where a save goes: the system's save dialog over the window, suggesting `name` (with its extension offered beside
    * any file), or None when the person closes it. A spec puts a file of its own here. */
  private[everyui] var chooseSave: String => Option[java.io.File] = name => {
    val chooser = new FileChooser()
    chooser.setInitialFileName(name)
    val extension = FileTypes.extension(name)
    if (extension.nonEmpty) chooser.getExtensionFilters.addAll(
      new FileChooser.ExtensionFilter("*." + extension, "*." + extension),
      new FileChooser.ExtensionFilter("All files", "*"))
    Option(chooser.showSaveDialog(if (Host.scene == null) null else Host.scene.getWindow))
  }

  /** `write` into the file chosen for `name`, off the thread that draws. */
  private def chooseAndWrite(name: String, done: SaveResult => Unit)(write: java.io.File => Unit): Unit =
    chooseSave(name) match {
      case None => done(SaveResult.Cancelled)
      case Some(file) => written(file, done)(write)
    }

  private def written(file: java.io.File, done: SaveResult => Unit)(write: java.io.File => Unit): Unit =
    Platform.offThread(() => {
      val result = try {
        write(file)
        SaveResult.Saved(Some(file.getPath))
      } catch { case t: Exception => SaveResult.SaveFailed(Option(t.getMessage).getOrElse(t.getClass.getSimpleName)) }
      Platform.later(() => done(result))
    })
}
