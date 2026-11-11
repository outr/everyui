package everyui.app

import everyui.app.durable.{DemoRequest, DemoResponse}
import everyui.component.{Column, TextView}
import everyui.i18n.{Language, Strings}
import everyui.network.{HttpCall, HttpFailure, HttpResult, Network}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import fabric.io.JsonParser
import rapid.Task
import reactify.*
import spice.http.ConnectionStatus

import scala.concurrent.duration.DurationInt

/** The app's own files and the network, checked on a device: `demo=net`. The words are the example's translations
  * (assets/l10n, read by `Strings` from `/l10n/app_<code>.arb`; `lang=fr` for the French), then a file of the app's
  * read by its path and one it does not have. With `server=http://host:8731` (EveryUI's `server/`, its `/check` and
  * `/echo`) it makes requests as an app does: a GET with a header of its own, a JSON POST, a 404, a 500 through
  * `postJson`, one past its `timeout`, one to a port nothing listens on, and one to `public=` (https://example.com
  * unless named, `public=0` for none); then a WebSocket to `/echo` that is answered, dropped by the server and opened
  * again. Each check is shown and logged, and the last line counts them:
  *
  *   [ui] net words ok Files and the network
  *   [ui] net missing ok "" for /l10n/app_xx.arb
  *   [ui] net get ok 200 X-EveryUI-Check=yes x-asked=yes
  *   [ui] net done ok=12 fail=0
  */
object NetDemo {
  private var passed = 0
  private var failed = 0
  private val lines = Column(spacing = 6.0)()

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val title = TextView("", 20.0, Theme.text())
    title.content := Strings("netTitle")
    val greeting = TextView("", 14.0, Theme.text())
    greeting.content := Strings.format("netGreeting", "name" -> Platform.deviceName())
    val column = Column(spacing = 12.0)(title, greeting, lines)
    column.position.left @= 16.0
    column.position.top @= 8.0
    ui.children += column
    val code = if (Platform.launchArg("lang").isEmpty) "en" else Platform.launchArg("lang")
    Strings.start(Language("en", "English", "English"), Language(code, code, code)).map { _ =>
      val words = Strings("netTitle")
      if (words == "netTitle") fail("words", "the translations were not read") else ok("words", words)
      files()
    }.start()
  }

  private def files(): Unit = Platform.readText(Platform.resolvePath("/images/svg-tile.svg"), text => {
    if (text.contains("<svg")) ok("file", text.length.toString + " characters of /images/svg-tile.svg")
    else fail("file", "/images/svg-tile.svg read as " + text.length.toString + " characters")
    Platform.readText(Platform.resolvePath("/l10n/app_xx.arb"), missing => {
      if (missing.isEmpty) ok("missing", "\"\" for /l10n/app_xx.arb")
      else fail("missing", "/l10n/app_xx.arb read as " + missing.length.toString + " characters")
      network().start()
    })
  })

  private def network(): Task[Unit] = {
    val server = Platform.launchArg("server").stripSuffix("/")
    val public = if (Platform.launchArg("public").isEmpty) "https://example.com" else Platform.launchArg("public")
    val http =
      if (server.isEmpty) Task.unit
      else answered("get", HttpCall("GET", server + "/check", List("X-Asked" -> "yes")), r => {
        val sent = JsonParser(r.body)
        r.status == 200 && r.header("x-everyui-check").contains("yes") && sent("method").asString == "GET" &&
          sent("headers")("x-asked").asString == "yes"
      }, r => r.status.toString + " X-EveryUI-Check=" + r.header("X-EveryUI-Check").getOrElse("") + " x-asked=" +
        JsonParser(r.body)("headers")("x-asked").asString)
        .flatMap(_ => answered("post", HttpCall("POST", server + "/check", Nil, Some("{\"n\":1}")), r => {
          val sent = JsonParser(r.body)
          r.status == 200 && sent("method").asString == "POST" && sent("body").asString == "{\"n\":1}" &&
            sent("headers")("content-type").asString.startsWith("application/json")
        }, r => r.status.toString + " " + r.body))
        .flatMap(_ => answered("status", HttpCall("GET", server + "/check?status=404"), _.status == 404,
          _.status.toString))
        .flatMap(_ => Network.postJson[DemoRequest, DemoResponse](server + "/check?status=500", DemoRequest("echo"))
          .attempt.map { r =>
            r.fold(t => t match {
              case HttpFailure(500, body) => ok("failure", "HttpFailure(500), " + body.length.toString + " characters")
              case other => fail("failure", other.toString)
            }, answer => fail("failure", "answered " + answer.toString))
          })
        .flatMap(_ => refused("timeout", HttpCall("GET", server + "/check?ms=4000", timeout = 1.0)))
        .flatMap(_ => refused("unreachable", HttpCall("GET", "http://127.0.0.1:9/")))
    val https =
      if (public == "0") Task.unit
      else answered("https", HttpCall("GET", public), _.status == 200, r => r.status.toString + " " +
        r.body.length.toString + " characters")
    http.flatMap(_ => https).flatMap(_ => if (server.isEmpty) Task.unit else socket(server)).map { _ =>
      Platform.log("net done ok=" + passed.toString + " fail=" + failed.toString)
      show("done: " + passed.toString + " ok, " + failed.toString + " failed")
    }
  }

  // a request that must be answered, and what of the answer says it was right
  private def answered(name: String, call: HttpCall, right: HttpResult => Boolean,
                       said: HttpResult => String): Task[Unit] =
    Network.http(call).attempt.map { r =>
      r.fold(t => fail(name, t.toString), result =>
        try if (right(result)) ok(name, said(result)) else fail(name, result.status.toString + " " + result.body)
        catch { case t: Throwable => fail(name, t.toString + " in " + result.body) })
    }

  // a request that must fail the task, as one with no answer does
  private def refused(name: String, call: HttpCall): Task[Unit] =
    Network.http(call).attempt.map { r =>
      r.fold(t => ok(name, "failed: " + t.toString), result => fail(name, "answered " + result.status.toString))
    }

  // the echo answers, the server drops the line, and a socket opened again is answered
  private def socket(server: String): Task[Unit] = {
    val url = server + "/echo"
    echo("socket", url, "hello", drop = true).flatMap(_ => echo("reconnect", url, "again", drop = false))
  }

  private def echo(name: String, url: String, word: String, drop: Boolean): Task[Unit] = {
    val done = Task.completable[Unit]
    val socket = Network.webSocket(url)
    val heard = Var("")
    socket.receive.text.attach { text =>
      heard @= text
      if (text == word) {
        if (drop) socket.send.text @= "drop"
        else {
          ok(name, "echoed " + text)
          socket.disconnect()
          done.success(())
        }
      }
    }
    // a socket that never opened is said once, by its connect
    socket.receive.close.on {
      if (drop && heard() == word) {
        ok(name, "echoed " + word + ", then dropped by the server")
        done.success(())
      }
    }
    socket.connect().map { status =>
      if (status == ConnectionStatus.Open) socket.send.text @= word
      else {
        fail(name, "did not open: " + status.toString)
        done.success(())
      }
    }.flatMap(_ => done).timeout(10.seconds).handleError { t =>
      fail(name, t.toString)
      Task.unit
    }
  }

  private def ok(name: String, detail: String): Unit = {
    passed = passed + 1
    Platform.log("net " + name + " ok " + detail)
    show(name + ": ok, " + detail)
  }

  private def fail(name: String, detail: String): Unit = {
    failed = failed + 1
    Platform.log("net " + name + " FAIL " + detail)
    show(name + ": FAILED, " + detail)
  }

  // on the thread that draws, where a socket answers on another (Android, the desktop)
  private def show(line: String): Unit = Platform.later(() =>
    lines.children += TextView(if (line.length > 90) line.substring(0, 90) + "…" else line, 13.0, Theme.text()))
}
