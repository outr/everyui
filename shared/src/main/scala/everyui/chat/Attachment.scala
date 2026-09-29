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

  /** Words as a text file going with a message (a paste too long for the field, say): its bytes their UTF-8, of type
    * text/plain, named after their first line (`textName`: "stack trace.txt"). */
  def pasted(text: String): Attachment = {
    val bytes = text.getBytes("UTF-8")
    Attachment(textName(text), bytes.length.toLong, "text/plain", Task.pure(bytes))
  }

  /** A file's name for words: their first line with anything on it, without what a file's name cannot hold, cut to 40
    * characters (at a space, where there is one past the first 20), and ".txt"; "pasted.txt" where no line has
    * anything on it. */
  def textName(text: String): String = {
    var line = ""
    var from = 0
    while (line.isEmpty && from < text.length) {
      val end = text.indexOf("\n", from)
      val until = if (end < 0) text.length else end
      line = clean(text.substring(from, until))
      from = until + 1
    }
    if (line.length > 40) {
      val space = line.lastIndexOf(" ", 40)
      line = (if (space > 20) line.substring(0, space) else line.substring(0, 40)).trim
    }
    while (line.endsWith(".")) line = line.substring(0, line.length - 1).trim
    (if (line.isEmpty) "pasted" else line) + ".txt"
  }

  // a line with what a file's name cannot hold (a slash, a colon, a control character) as spaces, a run of them one
  private def clean(line: String): String = {
    var out = ""
    var i = 0
    while (i < line.length) {
      val c = line.charAt(i)
      val space = c <= ' ' || c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' ||
        c == '>' || c == '|'
      if (!space) out = out + c.toString
      else if (out.nonEmpty && !out.endsWith(" ")) out = out + " "
      i += 1
    }
    out.trim
  }

  /** A size in bytes as a person reads it: "812 B", "14 KB", "3.2 MB". */
  def sizeText(bytes: Long): String =
    if (bytes < 1024L) bytes.toString + " B"
    else if (bytes < 1024L * 1024L) math.round(bytes / 1024.0).toString + " KB"
    else if (bytes < 1024L * 1024L * 1024L) (math.round(bytes * 10.0 / (1024.0 * 1024.0)) / 10.0).toString + " MB"
    else (math.round(bytes * 10.0 / (1024.0 * 1024.0 * 1024.0)) / 10.0).toString + " GB"
}
