package everyui.app

import everyui.component.TextView
import everyui.network.{HttpCall, Network}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import rapid.Task
import reactify.*

/** How long the network takes a request on this device: `n=<count>` requests (8 when not given) to `url=` (https://
  * example.com unless named), one after another, then all at once, each timed from the moment it was asked to its
  * answer, and the whole of each run. `method=POST` posts `body=` (`{}` unless named) as JSON, as an app's API calls
  * do. Each request and each run is logged; on a Roku the runtime says too where each one's time went (`[http]` lines,
  * UniversalRuntime.brs's uiOnHttpResult):
  *
  *   [ui] httpperf one 3: 212 ms (200)
  *   [ui] httpperf one after another: 8 in 1690 ms, 211 ms each
  *   [ui] httpperf all at once: 8 in 640 ms
  */
object HttpPerfDemo {
  def run(): Unit = {
    def arg(name: String, otherwise: String): String =
      if (Platform.launchArg(name).nonEmpty) Platform.launchArg(name) else otherwise
    val url = arg("url", "https://example.com")
    val n = arg("n", "8").toIntOption.getOrElse(8)
    val method = arg("method", "GET").toUpperCase
    val body = if (method == "GET") None else Some(arg("body", "{}"))
    val said = TextView("httpperf: " + n.toString + " " + method + " to " + url, 16.0, Theme.text())
    said.position.left @= 24.0
    said.position.top @= 24.0
    ui.backgroundColor := Theme.background()
    ui.children += said

    def one(label: String): Task[Unit] = {
      val at = Platform.ticks()
      Network.http(HttpCall(method, url, Nil, body)).attempt.map { r =>
        val ms = math.round(Platform.ticks() - at).toString
        Platform.log("httpperf " + label + ": " + ms + " ms (" +
          r.fold(t => "failed: " + t.toString, result => result.status.toString) + ")")
      }
    }
    def inTurn(i: Int): Task[Unit] = if (i >= n) Task.unit else one("one " + (i + 1).toString).flatMap(_ => inTurn(i + 1))

    val start = Platform.ticks()
    inTurn(0).map { _ =>
      val took = Platform.ticks() - start
      Platform.log("httpperf one after another: " + n.toString + " in " + math.round(took).toString + " ms, " +
        math.round(took / n).toString + " ms each")
      val all = Platform.ticks()
      var left = n
      (0 until n).foreach { i =>
        one("together " + (i + 1).toString).map { _ =>
          left -= 1
          if (left == 0) {
            val line = "httpperf all at once: " + n.toString + " in " + math.round(Platform.ticks() - all).toString + " ms"
            Platform.log(line)
            said.content @= line
          }
        }.start()
      }
    }.start()
  }
}
