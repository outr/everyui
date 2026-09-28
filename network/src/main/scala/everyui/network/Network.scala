package everyui.network

import fabric.*
import fabric.io.{JsonFormatter, JsonParser}
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
object Network {
  /** Where this app's server is (`"https://example.com"`), which a relative URL is resolved against; "" is the
    * platform's own: the page's origin on the web, and none elsewhere, where an app talking to its server by relative
    * URLs sets it first (`Network.origin = Platform.origin()`, which reads the setting "everyui.origin"). A relative
    * URL with no origin to go on throws as it is asked for. */
  var origin: String = ""

  /** `url` as it is requested: one with a scheme as it is; `//host/path` in the origin's scheme; `/path` on the
    * origin; and any other path under the origin's root (`api/x` is `<origin>/api/x`: never under the page's own
    * path). A relative URL with no origin to go on throws. */
  def resolve(url: String): String = if (url.indexOf("://") >= 0) url else {
    val named = if (origin.nonEmpty) origin else NetworkBackend.origin()
    val base = if (named.endsWith("/")) named.substring(0, named.length - 1) else named
    if (base.isEmpty)
      throw new IllegalArgumentException("A relative URL (" + url + ") with no origin to go on: set Network.origin")
    if (url.startsWith("//")) base.substring(0, math.max(0, base.indexOf(":"))) + ":" + url
    else if (url.startsWith("/")) base + url
    else base + "/" + url
  }

  /** A WebSocket to `url` — `ws://` or `wss://` where the platform has TLS; the Roku's `roStreamSocket` has none. A
    * relative `url` is on the origin (`resolve`), `http` made `ws` and `https` `wss`. */
  def webSocket(url: String): WebSocket = NetworkBackend.webSocket(socket(url))

  private def socket(url: String): String = {
    val full = resolve(url)
    if (full.startsWith("https://")) "wss://" + full.substring(8)
    else if (full.startsWith("http://")) "ws://" + full.substring(7)
    else full
  }

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

  /** The same, under a `clientId` of the app's choosing rather than a fresh one: what the server knows this client
    * by across reconnects. An app that signs different accounts in on one device names the account in it, so a new
    * sign-in is never taken for the old one's connection coming back. */
  def durableAs[Event: RW, Info: RW](url: String, channel: String, info: Info, config: DurableSocketConfig,
                                     clientId: String): DurableSocketClient[String, Event, Info] =
    new DurableSocketClient[String, Event, Info](
      createWebSocket = () => webSocket(url),
      config = config,
      outboundLog = new InMemoryEventLog[String, Event],
      initialChannelId = channel,
      info = info,
      clientId = clientId
    )

  /** Notified after each request settles: the call, its outcome (a result, or the error that failed the task) and
    * how long it took in milliseconds. The app installs this to ship request timing and failures to a server — it
    * is where a call that took 15s reveals itself. It must never throw (a throw here is swallowed) and must not
    * itself log through a path that loops back. Default: nothing. */
  var observe: (HttpCall, Either[Throwable, HttpResult], Long) => Unit = (_, _, _) => ()

  /** Basic HTTP: one request, the whole response as text; a relative URL is on the origin (`resolve`). Spice's HttpClient where it runs; the platform's stack
    * (roUrlTransfer, URLSession) elsewhere. Each request is timed and its outcome handed to [[observe]]. */
  def http(call: HttpCall): Task[HttpResult] = {
    val started = System.currentTimeMillis()
    NetworkBackend.http(call.copy(url = resolve(call.url))).attempt.map { result =>
      val outcome: Either[Throwable, HttpResult] = result.fold(t => Left(t), value => Right(value))
      try observe(call, outcome, System.currentTimeMillis() - started) catch { case _: Throwable => () }
      result.fold(t => throw t, value => value)
    }
  }

  def get(url: String): Task[HttpResult] = http(HttpCall("GET", url))

  /** POST `request` as JSON and read the reply as a `Response`: the whole of a JSON-over-HTTP API call. A status that
    * is not a success fails the task with an `HttpFailure` carrying the status and body, for the app to read its own
    * error format from. */
  def postJson[Request: RW, Response: RW](url: String, request: Request, headers: List[(String, String)] = Nil,
                                          timeout: Double = 0.0): Task[Response] =
    http(HttpCall("POST", url, ("Accept" -> "application/json") :: headers, Some(JsonFormatter.Compact(request.json)),
      timeout = timeout)).map { result =>
      if (result.ok) JsonParser(result.body).as[Response] else throw HttpFailure(result.status, result.body)
    }
  def post(url: String, body: String, contentType: String = "application/json"): Task[HttpResult] =
    http(HttpCall("POST", url, Nil, Some(body), contentType))
}

/** An HTTP request: a method, a URL, headers, and a text body with its content type. `timeout` is how many seconds it
  * may take, where a request is known to be slow (a server writing something new); 0 is the backend's own limit. Only
  * the backends on Spice's client honour it: a Roku's and tvOS's requests keep their own. */
case class HttpCall(method: String, url: String, headers: List[(String, String)] = Nil, body: Option[String] = None,
                    contentType: String = "application/json", timeout: Double = 0.0)

/** An HTTP response: the status, the headers (as the backend surfaces them) and the body as text. */
case class HttpResult(status: Int, headers: List[(String, String)], body: String) {
  def ok: Boolean = status >= 200 && status < 300
}
