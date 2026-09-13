package everyui.network

import fabric.*
import fabric.rw.*
import rapid.Task
import spice.http.WebSocket
import spice.http.durable.{DurableSocketClient, DurableSocketConfig, InMemoryEventLog}

/** EveryUI's network module: HTTP and DurableSocket on every backend, with Spice as the core.
  *
  * Spice already has the protocol — `spice.http.WebSocket`, `DurableSocketClient` (sequenced, replayed events,
  * RPC, reconnect with backoff), the same code a Spice server runs — so this module supplies only the piece that
  * differs per platform, behind one object with a fixed name (`NetworkBackend`, one per backend source root, the
  * way `everyui.render.Platform` works):
  *
  *   - web, desktop, Android: Spice's own client (`spice-client` JS / JVM / OkHttp), as published
  *   - Roku, tvOS: the platform's socket and HTTP stack behind the same trait (`network/native`), with Spice's
  *     durable client compiled through the emitter — rapid's Task, fabric's Json and scribe as runtime shims
  *
  * Optional: nothing in `shared/` depends on it. The example app's `DurableDemo` is its check on every backend.
  */
object Network:
  /** A WebSocket to `url` — `ws://` or `wss://` where the platform has TLS; the Roku's `roStreamSocket` has none. */
  def webSocket(url: String): WebSocket = NetworkBackend.webSocket(url)

  /** A durable client to `url` on `channel`: `Event`s sequenced and replayed across a dropped connection, typed RPC
    * (`ask[Request, Response]`), reconnect per `config`. `info` identifies this client to the server. The types are
    * fabric case classes with their codecs (`given rw: RW[T] = RW.gen`). */
  def durable[Event: RW, Info: RW](url: String, channel: String, info: Info, config: DurableSocketConfig): DurableSocketClient[String, Event, Info] =
    new DurableSocketClient[String, Event, Info](
      createWebSocket = () => webSocket(url),
      config = config,
      outboundLog = new InMemoryEventLog[String, Event],
      initialChannelId = channel,
      info = info
    )

  /** Basic HTTP: one request, the whole response as text. Spice's HttpClient where it runs; the platform's stack
    * (roUrlTransfer, URLSession) elsewhere. */
  def http(call: HttpCall): Task[HttpResult] = NetworkBackend.http(call)

  def get(url: String): Task[HttpResult] = http(HttpCall("GET", url))
  def post(url: String, body: String, contentType: String = "application/json"): Task[HttpResult] =
    http(HttpCall("POST", url, Nil, Some(body), contentType))

/** An HTTP request: a method, a URL, headers, and a text body with its content type. */
case class HttpCall(method: String, url: String, headers: List[(String, String)] = Nil, body: Option[String] = None,
                    contentType: String = "application/json")

/** An HTTP response: the status, the headers (as the backend surfaces them) and the body as text. */
case class HttpResult(status: Int, headers: List[(String, String)], body: String):
  def ok: Boolean = status >= 200 && status < 300
