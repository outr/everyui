package everyui.render

import everyui.async.Task
import everyui.file.{FileTypes, PickedFile}
import javafx.scene.input.{DragEvent, TransferMode}
import javafx.stage.FileChooser

import scala.jdk.CollectionConverters.*

/** Files from the person (`everyui.file.Files`): JavaFX's file dialog for a pick, and files dragged onto the window
  * from the system's file manager. A file is read on a thread of its own and answered on the one that draws; a
  * picture's `preview` is the file's own address. */
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
}
