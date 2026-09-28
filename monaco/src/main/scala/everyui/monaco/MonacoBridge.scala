package everyui.monaco

import everyui.PlatformKind
import everyui.component.Embed
import everyui.render.Platform
import fabric.*
import fabric.filter.RemoveNullsFilter

import scala.concurrent.{Future, Promise}

/** The app's end of one copy of Monaco: calls to it, their answers, and what it says. One per web page (every editor
  * on the web shares the page's Monaco), one per editor elsewhere (each a web view of its own).
  *
  * A call is `{callId, method, args}` as JSON; its answer is the event `_return`, or it fails after
  * `Monaco.callTimeout` seconds, or when the bridge closes. Calls made before Monaco has loaded wait for it; as it
  * loads, the themes and providers already given (`MonacoThemes`, `MonacoLanguages`) are given to it first. */
class MonacoBridge(val runtime: String) {
  private case class Pending(method: String, promise: Promise[Json])

  private var hosts: List[Embed] = Nil
  private var voice: Option[Embed] = None
  private var pending: Map[String, Pending] = Map.empty
  private var waiting: List[() => Unit] = Nil
  private var listeners: List[BridgeEvent => Unit] = Nil
  private var started = false
  private var readyNow = false
  private var closed = false
  private val loaded = Promise[Unit]()

  /** The text of each model an attached editor shows, by its uri: what a provider is asked about. */
  private[monaco] val texts = scala.collection.mutable.Map.empty[String, TextMirror]

  /** Completes once Monaco has loaded here. */
  def ready: Future[Unit] = loaded.future
  def isReady: Boolean = readyNow
  def isClosed: Boolean = closed

  /** An embed showing this copy of Monaco: it carries the calls, and the first of them hears the answers. */
  def attach(host: Embed): Unit = if (!closed) {
    hosts = hosts :+ host
    voice = Some(host)
    host.hear(text => if (hosts.headOption.exists(_ eq host)) receive(text))
    if (!started) start()
  }

  /** `host` gone. The last host gone closes the bridge, unless Monaco lives on in the page (the web). */
  def detach(host: Embed): Unit = {
    hosts = hosts.filterNot(_ eq host)
    if (hosts.nonEmpty) voice = hosts.headOption
    if (hosts.isEmpty && Platform.kind != PlatformKind.Web) close()
  }

  /** Call `method` with `args`; the answer, once Monaco has loaded and answered. */
  def call(method: String, args: Json = obj()): Future[Json] =
    if (closed) Future.failed(new MonacoBridgeException("the editor is gone", method))
    else if (readyNow) send(method, args, Monaco.callTimeout)
    else {
      val answer = Promise[Json]()
      waiting = (() => answer.completeWith(send(method, args, Monaco.callTimeout))) :: waiting
      answer.future
    }

  /** Hear what the guest says, until the function handed back is called. */
  def listen(f: BridgeEvent => Unit): () => Unit = {
    listeners = listeners :+ f
    () => listeners = listeners.filterNot(_ eq f)
  }

  /** Done with: every call still waiting fails, and nothing more is heard. */
  def close(): Unit = if (!closed) {
    closed = true
    val left = pending.values.toList
    pending = Map.empty
    left.foreach(p => p.promise.tryFailure(new MonacoBridgeException("the editor went away", p.method)))
    val calls = waiting
    waiting = Nil
    calls.foreach(f => f())
    listeners = Nil
    loaded.tryFailure(new MonacoBridgeException("the editor went away before it loaded", "bridge.init"))
    Monaco.forget(this)
  }

  private def start(): Unit = {
    started = true
    import Monaco.uiThread
    send("bridge.init", obj("base" -> str(Monaco.site.root), "version" -> str(Monaco.version)), Monaco.loadTimeout)
      .flatMap(_ => Future.sequence(MonacoThemes.replay(this) ++ MonacoLanguages.replay(this)))
      .onComplete { result =>
        result.failed.foreach(t => Platform.log("[monaco] did not load: " + t.getMessage))
        readyNow = result.isSuccess
        if (result.isSuccess) loaded.trySuccess(()) else loaded.tryFailure(result.failed.get)
        val calls = waiting.reverse
        waiting = Nil
        if (result.isSuccess) calls.foreach(f => f()) else close()
      }
  }

  /** Straight to the guest, ready or not: what loading it needs. */
  private[monaco] def send(method: String, args: Json, timeout: Double): Future[Json] =
    if (closed || voice.isEmpty) Future.failed(new MonacoBridgeException("the editor is gone", method))
    else {
      val id = Monaco.nextCallId()
      val promise = Promise[Json]()
      pending = pending + (id -> Pending(method, promise))
      Platform.after(timeout, () => pending.get(id).foreach { p =>
        pending = pending - id
        p.promise.tryFailure(new MonacoBridgeException(s"no answer in ${timeout.toInt} seconds", method))
      })
      val call = obj("callId" -> str(id), "method" -> str(method), "args" -> args.filterOne(RemoveNullsFilter))
      voice.foreach(_.send(Wire.text(call)))
      promise.future
    }

  private[monaco] def received(text: String): Unit = receive(text)

  private def receive(text: String): Unit = if (!closed) {
    val message = try Some(Wire.parse(text)) catch {
      case t: Throwable =>
        Platform.log("[monaco] unreadable message: " + t.getMessage)
        None
    }
    message.foreach { m =>
      val kind = Wire.string(m, "type")
      val payload = m.get("payload").getOrElse(obj())
      if (kind == "_return") {
        val id = Wire.string(payload, "callId")
        pending.get(id).foreach { p =>
          pending = pending - id
          payload.get("error").filter(_.isStr) match {
            case Some(error) => p.promise.tryFailure(new MonacoBridgeException(error.asString, p.method))
            case None => p.promise.trySuccess(payload.get("value").getOrElse(Null))
          }
        }
      }
      else if (kind.startsWith("language.") && kind.endsWith(".request")) MonacoLanguages.answer(this, kind, payload)
      // a copy the engine could not make itself (see the bridge): the system's clipboard, through the platform
      else if (kind == "clipboard.copy") Platform.copyText(Wire.string(payload, "text"))
      else {
        val event = BridgeEvent(kind, payload)
        listeners.foreach(f => f(event))
      }
    }
  }
}
