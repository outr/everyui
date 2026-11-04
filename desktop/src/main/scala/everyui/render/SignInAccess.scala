package everyui.render

import everyui.async.Task
import everyui.auth.SignInCallback

import java.io.{BufferedReader, InputStreamReader}
import java.net.{InetAddress, ServerSocket, SocketTimeoutException}
import java.nio.charset.StandardCharsets.UTF_8

/** Signing in with another site on the desktop (`everyui.auth.SignIn`), as RFC 8252 has a native app do it: the
  * system's browser sent to the provider, and the redirect to 127.0.0.1 at a port taken for this sign-in alone, which
  * the app listens on, on a thread of its own, until the redirect comes (answered with a page saying the browser may
  * be closed) or `minutes` pass. A browser the person closes says nothing, so only the time running out is None. */
object SignInAccess {
  def supported(): Boolean = true

  /** What sends the browser to the provider: the system's (`Platform.openUrl`), or a spec's in its place. */
  private[everyui] var opener: String => Unit = url => Platform.openUrl(url)

  def start(callback: SignInCallback, minutes: Double, authorize: String => Task[String],
            done: Option[String] => Unit): Unit = {
    val server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress)
    val base = "http://127.0.0.1:" + server.getLocalPort.toString
    val redirect = base + callback.loopback
    var finished = false
    def end(result: Option[String]): Unit = if (!finished) {
      finished = true
      try server.close() catch { case _: Throwable => () }
      done(result)
    }
    authorize(redirect).map { url =>
      if (url.isEmpty) end(None)
      else {
        val listening = new Thread(() => Platform.later(() => end(listen(server, base, callback.loopback, minutes))),
          "everyui-signin")
        listening.setDaemon(true)
        listening.start()
        try opener(url)
        catch {
          case t: Throwable =>
            Platform.logError("SignIn: the browser would not open", t)
            end(None)
        }
      }
    }.start()
  }

  // the redirect's whole address, once a request for `path` comes; None where the time ran out first
  private def listen(server: ServerSocket, base: String, path: String, minutes: Double): Option[String] = {
    val until = System.currentTimeMillis() + (minutes * 60000.0).toLong
    var found: Option[String] = None
    while (found.isEmpty && !server.isClosed && System.currentTimeMillis() < until) {
      try {
        server.setSoTimeout(math.max(1L, until - System.currentTimeMillis()).min(Int.MaxValue.toLong).toInt)
        val socket = server.accept()
        try {
          socket.setSoTimeout(5000)
          val line = Option(new BufferedReader(new InputStreamReader(socket.getInputStream, UTF_8)).readLine())
            .getOrElse("")
          val target = line.split(" ").lift(1).getOrElse("")
          val ours = target == path || target.startsWith(path + "?") || target.startsWith(path + "#")
          val body = if (ours) page else "Not found"
          val status = if (ours) "200 OK" else "404 Not Found"
          val bytes = body.getBytes(UTF_8)
          val out = socket.getOutputStream
          out.write(("HTTP/1.1 " + status + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " +
            bytes.length.toString + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(UTF_8))
          out.write(bytes)
          out.flush()
          if (ours) found = Some(base + target)
        }
        finally socket.close()
      }
      catch {
        case _: SocketTimeoutException => ()
        case _: java.io.IOException => ()
      }
    }
    try server.close() catch { case _: Throwable => () }
    found
  }

  private val page =
    "<!doctype html><meta charset=\"utf-8\"><title>Signed in</title>" +
      "<body style=\"font-family:system-ui,sans-serif;margin:3em;text-align:center\">" +
      "<p>You can close this window and go back to the app.</p><script>window.close()</script></body>"

  /** The desktop has no callback page: the redirect is its own port's. */
  def finish(callback: SignInCallback): Boolean = false
  def resumed(callback: SignInCallback): Option[String] = None
}
