package everyui.network

import fabric.*
import fabric.io.JsonFormatter
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
    val headers = JsonFormatter.Compact(obj(call.headers.map((k, v) => k -> str(v))*))
    uiHttp(call.method, call.url, headers, call.body.getOrElse(""), call.body.isDefined,
      (status, headersJson, body) => done.success(HttpResult(status, Nil, body)))
    done
  }
}
