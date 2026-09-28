package everyui.render

import android.content.Context
import android.view.{MotionEvent, View}
import android.webkit.WebView
import everyui.component.{EmbedGuest, EmbedSite}

/** A web guest in a web view of its own (Nodes.embed): `site`'s page, served from the APK's assets on a private https
  * origin (EmbedClient), with no server and no network permission. The guest talks through `EveryUIEmbed`, a
  * JavaScript interface (EmbedBridge); the app talks back by script, once the page has loaded.
  *
  * The render root is scaled by the screen's density, and a web view in it would be scaled a second time by its own
  * CSS pixels: so it is laid out that much larger and shrunk back by the same factor (`scaleOf`), which leaves one
  * CSS pixel for one layout unit, drawn sharp.
  *
  * It takes the platform's focus only while the app's focus is on its embed (`focus`): a focusable web view would
  * otherwise take the first focus Android hands out, and the remote with it. A touch in it asks for the app's focus
  * first (`onFocus`). */
class EmbedView(context: Context, val name: String, site: EmbedSite) extends WebView(context) {
  val id: String = EmbedView.next()
  private var loaded = false
  private var waiting: List[String] = Nil
  private var listeners: List[String => Unit] = Nil
  private var focusListeners: List[Boolean => Unit] = Nil
  private var released = false
  // held here as well as by the web view, which keeps only a weak hold on what it is handed
  private val bridge = new EmbedBridge(this)

  locally {
    // a debug build's guest can be inspected from a desktop browser (chrome://inspect)
    if ((context.getApplicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
      WebView.setWebContentsDebuggingEnabled(true)
    val settings = getSettings
    settings.setJavaScriptEnabled(true)
    settings.setDomStorageEnabled(true)
    settings.setAllowFileAccess(false)
    settings.setAllowContentAccess(false)
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    setFocusable(false)
    setFocusableInTouchMode(false)
    setPivotX(0f)
    setPivotY(0f)
    setScaleX((1.0 / Host.scale).toFloat)
    setScaleY((1.0 / Host.scale).toFloat)
    addJavascriptInterface(bridge, "EveryUIEmbed")
    setWebViewClient(new EmbedClient(site, () => {
      loaded = true
      val said = waiting.reverse
      waiting = Nil
      said.foreach(deliver)
    }))
    setWebChromeClient(new android.webkit.WebChromeClient {
      override def onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean = {
        android.util.Log.i("everyui", "[embed " + name + "] " + m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")")
        true
      }
    })
    loadUrl(EmbedClient.origin + site.page.stripPrefix("/") + "?embed=" + id)
  }

  /** `message` to the guest, once its page has loaded. */
  def send(message: String): Unit = if (!released) {
    if (loaded) deliver(message) else waiting = message :: waiting
  }

  def hear(f: String => Unit): Unit = listeners = listeners :+ f

  /** What the guest said, on the UI thread. */
  private[render] def said(message: String): Unit = if (!released) listeners.foreach(f => f(message))

  /** The platform's focus into the view as the app's arrives at its embed, and out as it leaves. */
  def focus(on: Boolean): Unit = {
    setFocusable(on)
    setFocusableInTouchMode(on)
    if (on) requestFocus() else if (hasFocus) clearFocus()
  }

  def onFocus(f: Boolean => Unit): Unit = focusListeners = focusListeners :+ f

  override def onTouchEvent(e: MotionEvent): Boolean = {
    if (e.getActionMasked == MotionEvent.ACTION_DOWN && !hasFocus) focusListeners.foreach(f => f(true))
    super.onTouchEvent(e)
  }

  override def onFocusChanged(gained: Boolean, direction: Int, previous: android.graphics.Rect): Unit = {
    super.onFocusChanged(gained, direction, previous)
    if (!gained) focusListeners.foreach(f => f(false))
  }

  /** Done with: the page let go of. */
  def release(): Unit = if (!released) {
    released = true
    listeners = Nil
    focusListeners = Nil
    removeJavascriptInterface("EveryUIEmbed")
    loadUrl("about:blank")
    destroy()
  }

  private def deliver(message: String): Unit =
    evaluateJavascript("everyuiEmbed.receive(" + EmbedGuest.quote(message) + ")", null)
}

object EmbedView {
  private var made = 0

  private def next(): String = {
    made += 1
    "everyui-embed-" + made.toString
  }

  /** How much larger than its layout units `view` is laid out: the screen's density for an embed's web view (see
    * above), 1 for anything else. */
  def scaleOf(view: View): Double = view match {
    case _: EmbedView => Host.scale
    case _ => 1.0
  }
}
