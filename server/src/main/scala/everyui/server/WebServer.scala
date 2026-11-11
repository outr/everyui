package everyui.server

import everyui.app.durable.{ClientInfo, DemoRequest, DemoResponse, LogSample, Tick}
import everyui.log.{LogBacklog, LogEvent, LogLevel, LogSubscriber}
import everyui.log.LogWire.given
import fabric.rw.*
import rapid.Task
import spice.http.durable.{DurableSocketServer, InMemoryEventLog, RpcException}
import spice.http.server.StaticHttpServer
import spice.http.server.config.HttpServerListener
import spice.http.server.dsl.*
import spice.http.server.dsl.given

import java.io.File
import scala.concurrent.duration.*

/** Serves the web backend's build output, and hosts the network module's check endpoint.
  *
  * Replaces the `python3 -m http.server` stopgap: this is Spice (the same stack youi's own example server used),
  * so the backend is part of the system rather than a dev-only prop — the place API endpoints, WebSocket
  * hot-reload, or an OpenAPI surface go when the framework needs them.
  *
  * `/durable` is a Spice `DurableSocketServer` every backend's `DurableDemo` connects to: a ticker broadcasts an
  * event four times a second on channel "ticker"; RPC `{"op":"echo","say":…}` answers `{"echo":…}`; `{"op":"drop"}`
  * closes the caller's socket a moment after answering, so the client must resume with no gap.
  *
  * `/logs` is a pipeline's log (`LogSample`) every backend's demo=logs can stream (`socket=ws://host:8731/logs`): a
  * line every 400 ms on channel "demo", kept by a `LogBacklog` of 5,000, so a client that drops and resumes has what it
  * missed replayed and a new one is sent what it does not have. `--log-drops=<seconds>` closes every log socket that
  * often, for a check of the resume.
  *
  * `/check` answers an HTTP request with what it was, and `/echo` is a plain WebSocket that echoes, for demo=net.
  *
  * Run with `sbt "server/run"`, optionally `sbt "server/run 9000 web/out-js --host=0.0.0.0"` (devices on the LAN
  * need the bind on every interface).
  */
object WebServer extends StaticHttpServer {
  private val DefaultPort: Int = 8731
  private val DefaultRoot: String = "web/out-js"

  private var root: File = File(DefaultRoot)

  private lazy val durable: DurableSocketServer[String, Tick, ClientInfo] = new DurableSocketServer[String, Tick, ClientInfo](
    eventLog = new InMemoryEventLog[String, Tick],
    resolveChannel = (_, _) => Task.pure("ticker"),
    onRequest = (info, request) => request.as[DemoRequest] match {
      case DemoRequest("echo", say) => Task.pure(DemoResponse(echo = say).json)
      case DemoRequest("drop", _) =>
        // answer first, then close this caller's socket: the client must reconnect, resume, and replay the gap
        Task.sleep(100.millis).map { _ =>
          durable.activeSessions.filter(_.info == info).foreach(s => s.listener().disconnect())
        }.start()
        Task.pure(DemoResponse(dropping = true).json)
      case DemoRequest(other, _) => Task.error(new RpcException("unknown_op", other))
    }
  )

  private val logs = new LogBacklog(5000)

  private lazy val logSockets: DurableSocketServer[String, LogEvent, LogSubscriber] =
    new DurableSocketServer[String, LogEvent, LogSubscriber](
      eventLog = logs,
      resolveChannel = (_, who) => Task.pure(who.channel),
      onRequest = (who, request) => logs.answer(who.channel, request))

  private def logLines(n: Int): Task[Unit] =
    Task.sleep(400.millis)
      .flatMap(_ => logSockets.broadcast("demo", LogEvent.of(LogSample.line(n), LogLevel.parse(LogSample.level(n)),
        if (n % 3 == 0) "loader" else "upload")))
      .flatMap(_ => logLines(n + 1))

  // every log socket closed each `seconds`: the clients resume
  private def logDrops(seconds: Int): Task[Unit] =
    Task.sleep(seconds.seconds).map { _ =>
      logSockets.activeSessions.foreach(s => s.listener().disconnect())
    }.flatMap(_ => logDrops(seconds))

  private def ticker(n: Long): Task[Unit] =
    Task.sleep(250.millis)
      .flatMap(_ => durable.broadcast("ticker", Tick(n, System.currentTimeMillis())))
      .flatMap(_ => ticker(n + 1L))

  // `/check`: an HTTP request answered with what it was (`{"method", "headers", "body"}`), with the status `?status=`
  // asks for (200 otherwise) after the wait `?ms=` asks for, and a header of the server's own (X-EveryUI-Check), so a
  // backend's requests (demo=net) are seen to send and read what Android's do
  private def check(exchange: spice.http.HttpExchange): Task[spice.http.HttpExchange] = {
    val url = exchange.request.url
    val status = url.param("status").flatMap(_.toIntOption).getOrElse(200)
    val wait = url.param("ms").flatMap(_.toLongOption).getOrElse(0L)
    val sent = exchange.request.content.map(_.asString).getOrElse(Task.pure(""))
    Task.sleep(wait.millis).flatMap(_ => sent).flatMap { body =>
      val headers = exchange.request.headers.map.toList.map((k, vs) => k.toLowerCase -> fabric.str(vs.mkString(", ")))
      val answer = fabric.obj("method" -> fabric.str(exchange.request.method.value), "headers" -> fabric.obj(headers*),
        "body" -> fabric.str(body))
      exchange.modify(response => Task(response.withStatus(spice.http.HttpStatus.byCode(status))
        .withHeader("X-EveryUI-Check", "yes").withContent(spice.http.content.Content.json(answer))))
    }
  }

  // `/echo`: a plain WebSocket that sends back each text it is sent, and closes the line on "drop", for a backend's
  // socket to be seen to connect, send, receive and come back after a drop (demo=net)
  private object echo extends spice.http.server.handler.WebSocketHandler {
    override def connect(exchange: spice.http.HttpExchange, listener: spice.http.WebSocketListener): Task[Unit] = {
      listener.receive.text.attach { text =>
        if (text == "drop") Task.sleep(50.millis).map(_ => listener.disconnect()).start()
        else listener.send.text @= text
      }
      Task.unit
    }
  }

  override protected val handler = new spice.http.server.handler.HttpHandler {
    override def handle(exchange: spice.http.HttpExchange)
                       (using mdc: scribe.mdc.MDC): Task[spice.http.HttpExchange] =
      if (exchange.request.url.path.decoded == "/durable") durable.handle(exchange)
      else if (exchange.request.url.path.decoded == "/logs") logSockets.handle(exchange)
      else if (exchange.request.url.path.decoded == "/check") check(exchange)
      else if (exchange.request.url.path.decoded == "/echo") echo.handle(exchange)
      else DirectoryPath(root).apply(exchange).map(_.exchange)
  }

  @main def serve(args: String*): Unit = {
    val (flags, positional) = args.partition(_.startsWith("--"))
    def flag(name: String): Option[String] = flags.collectFirst { case f if f.startsWith(s"--$name=") => f.drop(name.length + 3) }
    val port = positional.headOption.map(_.toInt).getOrElse(DefaultPort)
    val host = flag("host").getOrElse("127.0.0.1")
    root = File(positional.drop(1).headOption.getOrElse(DefaultRoot)).getCanonicalFile
    if (!root.isDirectory)
      sys.error(s"web root not found: $root — build it first with " +
        "`scala-cli --power package web -o web/out-js/main.js -f`")
    // Replace the default listener pair (8080 http + a disabled https) with a
    // single bound port, rather than adding to them.
    WebServer.config.clearListeners()
    WebServer.config.addListeners(HttpServerListener(host = host, port = Some(port)))
    scribe.info(s"everyui: serving $root on http://$host:$port/ — durable sockets at ws://$host:$port/durable")
    WebServer.start().sync()
    ticker(1L).start()
    logLines(1).start()
    flag("log-drops").map(_.toInt).foreach(s => logDrops(s).start())
    // StaticHttpServer runs on non-daemon listener threads; park the main
    // thread so `sbt run` does not return and tear the JVM down underneath it.
    Thread.currentThread().join()
  }
}
