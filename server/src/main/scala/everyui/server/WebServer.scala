package everyui.server

import everyui.app.durable.{ClientInfo, DemoRequest, DemoResponse, Tick}
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
  * Run with `sbt "server/run"`, optionally `sbt "server/run 9000 web/out-js --host=0.0.0.0"` (devices on the LAN
  * need the bind on every interface).
  */
object WebServer extends StaticHttpServer:
  private val DefaultPort: Int = 8731
  private val DefaultRoot: String = "web/out-js"

  private var root: File = File(DefaultRoot)

  private lazy val durable: DurableSocketServer[String, Tick, ClientInfo] = new DurableSocketServer[String, Tick, ClientInfo](
    eventLog = new InMemoryEventLog[String, Tick],
    resolveChannel = (_, _) => Task.pure("ticker"),
    onRequest = (info, request) => request.as[DemoRequest] match
      case DemoRequest("echo", say) => Task.pure(DemoResponse(echo = say).json)
      case DemoRequest("drop", _) =>
        // answer first, then close this caller's socket: the client must reconnect, resume, and replay the gap
        Task.sleep(100.millis).map { _ =>
          durable.activeSessions.filter(_.info == info).foreach(s => s.listener().disconnect())
        }.start()
        Task.pure(DemoResponse(dropping = true).json)
      case DemoRequest(other, _) => Task.error(new RpcException("unknown_op", other))
  )

  private def ticker(n: Long): Task[Unit] =
    Task.sleep(250.millis)
      .flatMap(_ => durable.broadcast("ticker", Tick(n, System.currentTimeMillis())))
      .flatMap(_ => ticker(n + 1L))

  override protected val handler = new spice.http.server.handler.HttpHandler:
    override def handle(exchange: spice.http.HttpExchange)
                       (using mdc: scribe.mdc.MDC): Task[spice.http.HttpExchange] =
      if exchange.request.url.path.decoded == "/durable" then durable.handle(exchange)
      else DirectoryPath(root).apply(exchange).map(_.exchange)

  @main def serve(args: String*): Unit =
    val (flags, positional) = args.partition(_.startsWith("--"))
    def flag(name: String): Option[String] = flags.collectFirst { case f if f.startsWith(s"--$name=") => f.drop(name.length + 3) }
    val port = positional.headOption.map(_.toInt).getOrElse(DefaultPort)
    val host = flag("host").getOrElse("127.0.0.1")
    root = File(positional.drop(1).headOption.getOrElse(DefaultRoot)).getCanonicalFile
    if !root.isDirectory then
      sys.error(s"web root not found: $root — build it first with " +
        "`scala-cli --power package web -o web/out-js/main.js -f`")
    // Replace the default listener pair (8080 http + a disabled https) with a
    // single bound port, rather than adding to them.
    WebServer.config.clearListeners()
    WebServer.config.addListeners(HttpServerListener(host = host, port = Some(port)))
    scribe.info(s"everyui: serving $root on http://$host:$port/ — durable sockets at ws://$host:$port/durable")
    WebServer.start().sync()
    ticker(1L).start()
    // StaticHttpServer runs on non-daemon listener threads; park the main
    // thread so `sbt run` does not return and tear the JVM down underneath it.
    Thread.currentThread().join()
