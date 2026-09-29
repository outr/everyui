package everyui.network

import everyui.async.Task
import everyui.chat.Attachment
import everyui.file.PickedFile

/** Files and a server (`everyui.file` and the network module together): a file the person picked, or a message's
  * attachment, as a part of a multipart upload. */
object HttpFiles {
  /** A file the person picked (`Files.pick`) as a form part named `name`: its name and type as picked, and its bytes
    * once read. For `HttpCall.multipart`, whose requests are rapid Tasks:
    *
    * {{{
    *   Files.pick(List("image/\*"), multiple = false).map(_.headOption.foreach { picked =>
    *     HttpFiles.part("image", picked).flatMap { image =>
    *       Network.http(HttpCall.multipart("/user/profile/picture/upload", List(FormPart.json("auth", auth), image)))
    *     }.map(shown).start()
    *   }).start()
    * }}} */
  def part(name: String, file: PickedFile): rapid.Task[FormPart] = read(name, file.name, file.mime, file.bytes)

  /** A message's attachment (a picked, dropped or pasted file, `everyui.chat`) as a form part named `name`. */
  def attached(name: String, file: Attachment): rapid.Task[FormPart] = read(name, file.name, file.mime, file.bytes)

  private def read(name: String, fileName: String, mime: String, bytes: Task[Array[Byte]]): rapid.Task[FormPart] = {
    val read = rapid.Task.completable[FormPart]
    bytes.map(b => read.success(FormPart.file(name, fileName, b, mime))).start()
    read
  }
}
