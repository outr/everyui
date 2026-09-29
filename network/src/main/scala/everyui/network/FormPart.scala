package everyui.network

import fabric.io.JsonFormatter
import fabric.rw.*

/** One part of a multipart/form-data body (`HttpCall.multipart`): a field of text, or a file's bytes with the name and
  * type it goes as, as an HTML form sends them. Its cases' names are their own (not "Text", "File"), since a Roku
  * channel's type names are global. A file the person picked (`Files.pick`) is a part through `HttpFiles.part`.
  *
  * {{{
  *   HttpCall.multipart("/user/profile/picture/upload", List(
  *     FormPart.json("auth", auth),
  *     FormPart.file("image", "me.png", png, "image/png")))
  * }}} */
sealed trait FormPart {
  /** The form's name for it. */
  def name: String
}

object FormPart {
  /** A field: `value` as UTF-8 text, with no type of its own, as a form's input goes. */
  case class FieldPart(name: String, value: String) extends FormPart

  /** A file: its bytes as they are, the name it goes as and its type. */
  case class FilePart(name: String, fileName: String, bytes: Array[Byte], contentType: String = "application/octet-stream")
    extends FormPart

  /** A field of text. */
  def text(name: String, value: String): FormPart = FieldPart(name, value)

  /** `value` as a field of compact JSON, which a server reads the field as. */
  def json[T: RW](name: String, value: T): FormPart = FieldPart(name, JsonFormatter.Compact(value.json))

  /** A file part. */
  def file(name: String, fileName: String, bytes: Array[Byte], contentType: String = "application/octet-stream"): FormPart =
    FilePart(name, fileName, bytes, contentType)
}
