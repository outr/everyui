package everyui.render

import everyui.async.Task
import everyui.file.{PickedFile, SaveResult}

/** Files from the person (`everyui.file.Files`). A remote has no picker and nothing is dragged onto a television, so
  * this platform offers neither: a pick answers at once with nothing, and no drop ever comes. Nor is there anywhere
  * for the person to save a file to, so a save fails at once. */
object FileAccess {
  def pickable(): Boolean = false

  def droppable(): Boolean = false

  def pick(types: List[String], multiple: Boolean, done: List[PickedFile] => Unit): Unit = done(Nil)

  def onDrop(node: RenderNode, hover: Boolean => Unit, dropped: List[PickedFile] => Unit): Unit = ()

  /** Done with a picked file's `preview`: nothing is held for one here. */
  def release(preview: String): Unit = ()

  /** Nothing is shown from bytes here. */
  def source(bytes: Array[Byte], mime: String): String = ""

  def savable(): Boolean = false

  def save(bytes: Array[Byte], name: String, mime: String, done: SaveResult => Unit): Unit =
    done(SaveResult.SaveFailed("Nowhere to save a file on this platform"))

  /** A download is a save here: there are no downloads apart from it. */
  def download(bytes: Array[Byte], name: String, mime: String, done: SaveResult => Unit): Unit =
    save(bytes, name, mime, done)

  def saveUrl(url: String, name: String, mime: String, done: SaveResult => Unit): Unit =
    done(SaveResult.SaveFailed("Nowhere to save a file on this platform"))

  /** The bytes are never made: there is nowhere for them to go. */
  def saveLater(name: String, mime: String, bytes: Task[Either[String, Array[Byte]]], done: SaveResult => Unit): Unit =
    done(SaveResult.SaveFailed("Nowhere to save a file on this platform"))

  def saveStream(name: String, mime: String, write: everyui.file.FileSink => Task[Either[String, Unit]],
                 done: SaveResult => Unit): Unit =
    done(SaveResult.SaveFailed("Nowhere to save a file on this platform"))
}
