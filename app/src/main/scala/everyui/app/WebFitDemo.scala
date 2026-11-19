package everyui.app

import everyui.component.{ScrollContainer, TextView, WebView}
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui

/** A page of the web fitted to its box, as an email or an article is shown: what the page measures and says, read
  * against the app's own words. Every page posts what it says with `parent.postMessage`, as a page framed on the web
  * does, and the app logs what it hears (`[ui] webfit heard …`).
  *
  *   (default)   a 16 px "Hello" in a page beside a 16-unit TextView, the page saying its innerWidth, its
  *               devicePixelRatio and how wide its box is in CSS pixels: on a phone at 2.625 they match
  *   `fit=height` a page that says how tall it is, its box sized to that: the whole page shows, its last line "END"
  *   `fit=scroll` a header over a page of 9,000 CSS pixels that cannot scroll (`html{overflow-y:hidden}`), sized to its
  *               page in a ScrollContainer: a drag or a flick on the page scrolls the container to the end
  *               (`[ui] webfit scroll at …`); with `own=1` the page scrolls itself in a box 600 tall instead,
  *               keeping the drags along it until it is at its end
  *
  * `via=bridge` has the pages say it through `webfitBridge` instead, and `sandbox=1` sandboxes them with scripts
  * alone, heard all the same (`hearSandboxed`). */
object WebFitDemo {
  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val fit = Platform.launchArg("fit")
    val page = new WebView("webfitBridge")
    page.clear @= true
    if (Platform.launchArg("sandbox") == "1") page.sandbox @= Some(Set("allow-scripts"))
    page.hearSandboxed { message =>
      Platform.log("webfit heard " + message)
      height(message).foreach(h => if (fit != "") page.size.height @= h)
    }
    if (fit == "scroll") scroll(page) else if (fit == "height") tall(page) else hello(page)
  }

  // {"height":123.4} as a page says it
  private def height(message: String): Option[Double] =
    """"height":([0-9.]+)""".r.findFirstMatchIn(message).map(_.group(1).toDouble)

  // a page as its address: what would end it or be read as an escape (`#`, `%`) escaped
  private def data(html: String): String =
    "data:text/html;charset=utf-8," + html.replace("%", "%25").replace("#", "%23")

  // to its parent, or through the bridge with `via=bridge`
  private def says: String = {
    val m = "{innerWidth:innerWidth,dpr:devicePixelRatio,height:document.documentElement.scrollHeight}"
    val post = if (Platform.launchArg("via") == "bridge") "webfitBridge.postMessage(JSON.stringify(" + m + "))"
      else "parent.postMessage(" + m + ",'*')"
    "<script>function say(){" + post + "}addEventListener('load',say);addEventListener('resize',say)</script>"
  }

  private def hello(page: WebView): Unit = {
    val words = new TextView {
      content @= "Hello"
      font.size @= 16.0
      color := Theme.text()
      position.left @= 16.0
      position.top @= 40.0
    }
    page.position.left @= 120.0
    page.position.top @= 24.0
    page.size.width @= 240.0
    page.size.height @= 120.0
    page.source @= data("<html><body style='margin:16px 0 0 0;color:#888;font:16px sans-serif'>" +
      "<p style='font-size:16px;margin:0'>Hello</p><p id='m' style='font-size:12px'></p>" + says +
      "<script>m.textContent=innerWidth+' x '+innerHeight+' @ '+devicePixelRatio</script></body></html>")
    ui.children ++= List(words, page)
  }

  private def tall(page: WebView): Unit = {
    page.position.left @= 16.0
    page.position.top @= 16.0
    page.size.width := ui.size.width() - 32.0
    page.size.height @= 50.0
    val lines = (1 to 30).map(i => s"<p>Line $i of a page that says how tall it is.</p>").mkString
    page.source @= data("<html><body style='margin:0;color:#888;font:16px sans-serif'>" + lines +
      "<p style='background:#c62828;color:white;margin:0;padding:8px'>END</p>" + says + "</body></html>")
    ui.children += page
  }

  private def scroll(page: WebView): Unit = {
    val own = Platform.launchArg("own") == "1"
    val view = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
      content.layout @= Some(VerticalLayout(0.0))
    }
    val header = new TextView {
      content @= "A header over the page"
      font.size @= 24.0
      color := Theme.text()
      size.height @= 200.0
    }
    page.size.width := ui.size.width()
    page.size.height @= (if (own) 600.0 else 50.0)
    val lines = (1 to 300).map(i => s"<p style='margin:0;height:30px'>Line $i</p>").mkString
    val html = if (own) "<html style='overflow-y:auto'>" else "<html style='overflow-y:hidden'>"
    page.source @= data(html + "<body style='margin:0;color:#888;font:16px sans-serif'>" + lines +
      "<p style='background:#c62828;color:white;margin:0;height:30px'>END</p>" +
      (if (own) "" else says) + "</body></html>")
    val footer = new TextView {
      content @= "The end of the scroll"
      font.size @= 24.0
      color := Theme.text()
      size.height @= 200.0
    }
    view.content.children ++= List(header, page, footer)
    var moves = 0
    view.offset.attach { at =>
      moves += 1
      val mine = moves
      Platform.after(0.4, () => if (moves == mine) Platform.log("webfit scroll at " + math.round(at).toString +
        " of " + math.round(view.content.size.height() - view.size.height()).toString))
    }
    ui.children += view
  }
}
