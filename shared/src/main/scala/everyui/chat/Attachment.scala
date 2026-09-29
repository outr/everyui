package everyui.chat

import everyui.async.Task
import everyui.file.PickedFile

/** A file going with a message: its name, its size in bytes, its type ("image/png"), a Task that reads its bytes
  * (each time it is started), and where a picture of it can be shown from, if anywhere. The composer makes one of
  * each file picked or dropped (`Attachment.of`); an app can make its own (a pasted picture, a file from its server). */
case class Attachment(name: String, size: Long, mime: String, bytes: Task[Array[Byte]], preview: Option[String] = None) {
  /** Whether it is a picture, by its type. */
  def image: Boolean = mime.startsWith("image/")
}

object Attachment {
  /** A file the person picked or dropped, as an attachment. */
  def of(file: PickedFile): Attachment = Attachment(file.name, file.size, file.mime, file.bytes, file.preview)

  /** A size in bytes as a person reads it: "812 B", "14 KB", "3.2 MB". */
  def sizeText(bytes: Long): String =
    if (bytes < 1024L) bytes.toString + " B"
    else if (bytes < 1024L * 1024L) math.round(bytes / 1024.0).toString + " KB"
    else if (bytes < 1024L * 1024L * 1024L) (math.round(bytes * 10.0 / (1024.0 * 1024.0)) / 10.0).toString + " MB"
    else (math.round(bytes * 10.0 / (1024.0 * 1024.0 * 1024.0)) / 10.0).toString + " GB"
}
