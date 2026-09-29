package everyui.network

import fabric.*
import fabric.io.{JsonFormatter, JsonParser}
import rapid.Task
import spice.http.{ConnectionStatus, WebSocket}

/** Spice's `WebSocket` over the platform's socket (the Roku's `roStreamSocket` in a Task node, tvOS's
  * `URLSessionWebSocketTask`). Text frames; the platform reports open, each message and the close. */
class PlatformWebSocket(url: String) extends WebSocket {
  private var handle: Any = null
  private var opened: Boolean = false

  override def connect(): Task[ConnectionStatus] = {
    _status @= ConnectionStatus.Connecting
    val connected = Task.completable[ConnectionStatus]
    handle = uiSocketOpen(url,
      () => {
        opened = true
        _status @= ConnectionStatus.Open
        connected.success(ConnectionStatus.Open)
      },
      text => receive.text @= text,
      (code, reason) => {
        _status @= ConnectionStatus.Closed
        receive.close @= ()
        if (!opened) connected.success(ConnectionStatus.Closed)
      }
    )
    send.text.attach(text => uiSocketSend(handle, text))
    send.close.on(uiSocketClose(handle))
    connected
  }

  override def disconnect(): Unit = if (handle != null) uiSocketClose(handle)
}

/** Roku and tvOS: the platform's socket and HTTP stack behind the module's API. */
object NetworkBackend {
  /** Where relative URLs go when the app names nowhere (`Network.origin`): nowhere, off the web. */
  def origin(): String = ""
  def webSocket(url: String): WebSocket = new PlatformWebSocket(url)

  def http(call: HttpCall): Task[HttpResult] = {
    val done = Task.completable[HttpResult]
    if (call.parts.nonEmpty) {
      // a multipart body goes as bytes, under the boundary its Content-Type names
      val boundary = Multipart.boundary()
      val typed = call.headers.filter(_._1.toLowerCase != "content-type") :+
        ("Content-Type" -> Multipart.contentType(boundary))
      uiHttpBytes(call.method, call.url, json(typed), Multipart.encode(call.parts, boundary), true, false,
        (status, headersJson, text, _) => done.success(HttpResult(status, headers(headersJson), text)))
    }
    else uiHttp(call.method, call.url, json(call.headers), call.body.getOrElse(""), call.body.isDefined,
      (status, headersJson, body) => done.success(HttpResult(status, headers(headersJson), body)))
    done
  }

  /** `text` as the bytes of its UTF-8, and back: the platform's own, since a Roku's strings have neither. */
  def utf8(text: String): Array[Byte] = uiUtf8(text)
  def text(bytes: Array[Byte]): String = uiUtf8Text(bytes)

  private def json(headers: List[(String, String)]): String = JsonFormatter.Compact(obj(headers.map((k, v) => k -> str(v))*))

  /** The response's headers as the platform gave them (a JSON object of names to values), none where it gave none. */
  private def headers(json: String): List[(String, String)] =
    try JsonParser(json).asObj.value.toList.map((k, v) => k -> (if (v.isStr) v.asString else v.toString))
    catch { case _: Throwable => Nil }
}
