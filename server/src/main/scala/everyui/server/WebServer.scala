package everyui.server

import rapid.Task
import spice.http.server.StaticHttpServer
import spice.http.server.config.HttpServerListener
import spice.http.server.dsl.*
import spice.http.server.dsl.given

import java.io.File

/** Serves the web backend's build output.
  *
  * Replaces the `python3 -m http.server` stopgap: this is Spice (the same
  * stack youi's own example server used), so the backend is part of the system
  * rather than a dev-only prop — the place API endpoints, WebSocket
  * hot-reload, or an OpenAPI surface go when the framework needs them.
  *
  * Run with `sbt "server/run"`, optionally `sbt "server/run 9000 web/out-js"`.
  */
object WebServer extends StaticHttpServer:
  private val DefaultPort: Int = 8731
  private val DefaultRoot: String = "web/out-js"

  private var root: File = File(DefaultRoot)

  override protected val handler = new spice.http.server.handler.HttpHandler:
    override def handle(exchange: spice.http.HttpExchange)
                       (using mdc: scribe.mdc.MDC): Task[spice.http.HttpExchange] =
      DirectoryPath(root).apply(exchange).map(_.exchange)

  @main def serve(args: String*): Unit =
    val port = args.headOption.map(_.toInt).getOrElse(DefaultPort)
    root = File(args.drop(1).headOption.getOrElse(DefaultRoot)).getCanonicalFile
    if !root.isDirectory then
      sys.error(s"web root not found: $root — build it first with " +
        "`scala-cli --power package web -o web/out-js/main.js -f`")
    // Replace the default listener pair (8080 http + a disabled https) with a
    // single bound port, rather than adding to them.
    WebServer.config.clearListeners()
    WebServer.config.addListeners(HttpServerListener(host = "127.0.0.1", port = Some(port)))
    scribe.info(s"everyui: serving $root on http://localhost:$port/")
    WebServer.start().sync()
    // StaticHttpServer runs on non-daemon listener threads; park the main
    // thread so `sbt run` does not return and tear the JVM down underneath it.
    Thread.currentThread().join()
