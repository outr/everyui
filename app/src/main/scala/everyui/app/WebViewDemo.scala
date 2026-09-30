package everyui.app

import everyui.component.{Button, Row, TextView, WebView}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui

/** A page of the web in a WebView: `webview.html`, served beside the web build (or `src=<url>`, the same page on another
  * origin), with a bridge named `demoBridge`. A ping is run before the page has loaded and another as it says it is
  * ready, and a script that is not one of its commands after that; Ping runs another. Logs what the page says:
  *
  *   [ui] webview heard {"event":"pong","n":1,...}
  *   [ui] webview heard {"event":"refused","script":"..."}   (another origin: the script is not run)
  *
  * Where the page is of the app's own origin every script runs in it, the last one setting `window.evaled`; on
  * another it is posted, and the page refuses what is not a ping. `dispose=1` takes the view apart after three
  * seconds, after which nothing the page says is heard. An empty box where there is no engine to lend. */
object WebViewDemo {
  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val page = new WebView("demoBridge")
    page.position.left @= 16.0
    page.position.top @= 64.0
    page.size.width := ui.size.width() - 32.0
    page.size.height := ui.size.height() - 80.0
    val heard = new TextView {
      content @= "webViews: " + Platform.webViews().toString
      color := Theme.text()
    }
    var pings = 1
    val ping = new Button("Ping", () => {
      pings += 1
      page.run("ping(" + pings.toString + ")")
    })
    val bar = Row(spacing = 16.0)(ping, heard)
    bar.position.left @= 16.0
    bar.position.top @= 12.0
    page.hear { message =>
      Platform.log("webview heard " + message)
      heard.content @= message
      if (message.contains("\"ready\"")) {
        page.run("ping(1)")
        page.run("window.evaled = true; ping(99)")
      }
    }
    val src = Platform.launchArg("src")
    page.source @= (if (src.nonEmpty) src else "webview.html")
    page.run("ping(0)")
    ui.children ++= List(bar, page)
    if (Platform.launchArg("dispose") == "1") Platform.after(3.0, () => {
      page.dispose()
      Platform.log("webview disposed")
    })
  }
}
