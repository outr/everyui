package everyui.render

import everyui.component.{EmbedGuest, EmbedSite}
import javafx.concurrent.Worker
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import netscape.javascript.JSObject

/** A web guest in a JavaFX web view (WebKit) filling its embed's pane: `site`'s page, from EmbedServer. The guest
  * talks through `EveryUIEmbedHost` (FxEmbedHost), set on the page's window as it finishes loading and connected to
  * `everyuiEmbed`; what it says before then waits in the page, and what the app says waits here.
  *
  * Found by name (WebGuest): this file is compiled only where javafx-web is on the classpath. */
class FxWebGuest(pane: Pane, name: String, site: EmbedSite) extends WebGuest {
  val id: String = FxWebGuest.next()
  private val view = new WebView()
  private val engine = view.getEngine
  private var loaded = false
  private var waiting: List[String] = Nil
  private var listeners: List[String => Unit] = Nil
  private var released = false
  private var reloads = 0
  // held here: the page's window holds what it is handed weakly, and a host collected is a guest that goes quiet
  private val host = new FxEmbedHost(message => if (!released) listeners.foreach(f => f(message)),
    line => Platform.log("[embed " + name + "] " + line))

  locally {
    view.setContextMenuEnabled(false)
    view.setPageFill(Color.TRANSPARENT)
    view.getProperties.put(Host.embedKey, name)
    view.prefWidthProperty.bind(pane.widthProperty)
    view.prefHeightProperty.bind(pane.heightProperty)
    view.setMinSize(0.0, 0.0)
    pane.getChildren.add(view)
    engine.getLoadWorker.stateProperty.addListener((_, _, state) => {
      // a page whose channel did not run is loaded again: of two web views loading the page at once, WebKit has been
      // seen to finish one with its parsing stopped at the channel's script
      if (state == Worker.State.SUCCEEDED && !released && reloads < 3 && engine.executeScript("typeof everyuiEmbed") == "undefined") {
        reloads += 1
        Platform.log("[embed " + name + "] the page loaded without its channel; loading it again")
        engine.reload()
      }
      else if (state == Worker.State.SUCCEEDED && !released) {
        engine.executeScript("window").asInstanceOf[JSObject].setMember("EveryUIEmbedHost", host)
        engine.executeScript(FxWebGuest.connect)
        loaded = true
        val said = waiting.reverse
        waiting = Nil
        said.foreach(deliver)
      }
      else if (state == Worker.State.FAILED) Platform.log("[embed " + name + "] the page did not load: " + engine.getLoadWorker.getException)
    })
    engine.load(EmbedServer.url(site) + site.page.stripPrefix("/") + "?embed=" + id)
  }

  def send(message: String): Unit = if (!released) {
    if (loaded) deliver(message) else waiting = message :: waiting
  }

  def hear(f: String => Unit): Unit = listeners = listeners :+ f

  def focus(on: Boolean): Unit =
    if (on) { if (!view.isFocused) view.requestFocus() }
    else if (view.isFocused && Host.root != null) Host.root.requestFocus()

  def onFocus(f: Boolean => Unit): Unit = view.focusedProperty.addListener((_, _, now) => f(now))

  def release(): Unit = if (!released) {
    released = true
    listeners = Nil
    engine.load(null)
    pane.getChildren.remove(view)
  }

  private def deliver(message: String): Unit =
    engine.executeScript("everyuiEmbed.receive(" + EmbedGuest.quote(message) + ")")
}

object FxWebGuest {
  private var made = 0

  private def next(): String = {
    made += 1
    "everyui-embed-" + made.toString
  }

  // the channel connected to the host object, and the page's errors and console to the app's log: a web view shows
  // neither anywhere else
  private val connect: String =
    """(function () {
      |  var host = window.EveryUIEmbedHost;
      |  everyuiEmbed.connect(function (text) { host.postMessage(text); });
      |  ['error', 'warn', 'log'].forEach(function (level) {
      |    var was = console[level];
      |    console[level] = function () {
      |      try { host.log(level + ': ' + Array.prototype.map.call(arguments, String).join(' ')); } catch (e) {}
      |      if (was) was.apply(console, arguments);
      |    };
      |  });
      |  window.addEventListener('error', function (e) { host.log('uncaught: ' + e.message + ' (' + e.filename + ':' + e.lineno + ')'); });
      |  window.addEventListener('unhandledrejection', function (e) { host.log('unhandled rejection: ' + (e.reason && e.reason.stack || e.reason)); });
      |})();
      |""".stripMargin
}
