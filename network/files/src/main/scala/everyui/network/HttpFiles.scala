package everyui.network

import everyui.async.Task
import everyui.chat.Attachment
import everyui.file.{FileTypes, Files, PickedFile, SaveResult}
import everyui.render.Platform

/** What a server answers with, saved as a file where the person chooses (`everyui.file.Files`): an export a POST
  * writes, a report, a picture. The request is made with `binary`, so the file is its bytes as they came.
  *
  * {{{
  *   val export = HttpCall("POST", "/entity/download-export", body = Some(JsonFormatter.Compact(request.json)))
  *   HttpFiles.save(export).map {
  *     case SaveResult.Saved(name) => Toast.show("Saved " + name.getOrElse("the export"))
  *     case SaveResult.Cancelled => ()
  *     case SaveResult.SaveFailed(why) => Toast.show("Not saved: " + why)
  *   }.start()
  * }}}
  *
  * With a `name`, the picker opens first, from the click that asked (a browser opens its picker only for one), and the
  * request is made once the person has chosen where. Without one, the request is made first, and the file is named as
  * the server's Content-Disposition names it (else after the URL's last segment): a browser then opens its picker only
  * if the click is still recent (Chromium allows about five seconds), and downloads the file otherwise. `mime` is its
  * type, else the server's Content-Type, else by the name's extension.
  *
  * A status that is not a success saves nothing and answers `SaveFailed("HTTP <status>: <body>")`, and so does a
  * request that fails. Where there is nowhere to save (a Roku, an Apple TV: `Files.savable` false) the request is not
  * made. On the web a server of another origin must expose Content-Disposition (Access-Control-Expose-Headers) for
  * its name to be read.
  *
  * The other way, a file the person picked, or a message's attachment, is a part of a multipart upload (`part`,
  * `attached`). */
object HttpFiles {
  def save(call: HttpCall, name: Option[String] = None, mime: Option[String] = None): Task[SaveResult] =
    if (!Files.savable) Task.pure(SaveResult.SaveFailed("Nowhere to save a file on this platform"))
    else name match {
      case Some(named) => Files.saveLater(named, mime)(fetched(call).map(_.map(_._1)))
      case None => fetched(call).flatMap {
        case Left(why) => Task.pure(SaveResult.SaveFailed(why))
        case Right((bytes, result)) =>
          val named = result.fileName.getOrElse(fromUrl(call.url))
          Files.save(bytes, named, mime.orElse(result.mime).orElse(Some(FileTypes.of(named))))
      }
    }

  /** The call made for its bytes, answered on the thread that draws: the bytes and the result, or why there are none. */
  def fetched(call: HttpCall): Task[Either[String, (Array[Byte], HttpResult)]] =
    Task.async[Either[String, (Array[Byte], HttpResult)]] { done =>
      Network.http(call.copy(binary = true)).map { result =>
        val answer: Either[String, (Array[Byte], HttpResult)] =
          if (result.ok) Right((result.bytes.getOrElse(new Array[Byte](0)), result))
          else Left(failure(result))
        Platform.later(() => done(answer))
      }.handleError { t =>
        Platform.later(() => done(Left(Option(t.getMessage).getOrElse(t.toString))))
        rapid.Task.unit
      }.start()
    }

  /** Why a response is not a file: its status, and the start of what the server said. */
  private def failure(result: HttpResult): String = {
    val said = result.body.trim
    if (result.status <= 0) (if (said.isEmpty) "No response" else said)
    else if (said.isEmpty) "HTTP " + result.status.toString
    else "HTTP " + result.status.toString + ": " + (if (said.length > 200) said.substring(0, 200) + "…" else said)
  }

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

  /** A file's name from the URL it came from: its path's last segment, without the query; "download" where it has none. */
  def fromUrl(url: String): String = {
    val cut = List(url.indexOf("?"), url.indexOf("#")).filter(_ >= 0)
    val whole = if (cut.isEmpty) url else url.substring(0, cut.min)
    val scheme = whole.indexOf("://")
    val slash = if (scheme >= 0) whole.indexOf("/", scheme + 3) else 0
    val path = if (slash < 0) "" else whole.substring(slash)
    val last = path.substring(path.lastIndexOf("/") + 1)
    if (last.isEmpty) "download" else last
  }
}
