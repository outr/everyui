package everyui.render

import android.webkit.{WebSettings, WebView}

/** The browser engine started before a page needs it (everyui.component.WebView.prewarm). The first web view of a
  * process loads Chromium as it is made, on the main thread, and holds it up for a second or more on a television
  * (two and a half on a SHIELD), every frame of the app waiting. Warmed, the engine's classes and native library are
  * loaded on a thread of its own, its start (which Android runs on the main thread whatever asks) comes at once
  * after, and one web view is made with its page process started, kept for the next `Nodes.web` to take: so the
  * page, when it comes, costs what any later one does. One is kept at most, and one made for an activity since gone
  * is destroyed rather than taken. */
object WebWarm {
  private var spare: WebView = null
  private var warming = false

  /** Warm the engine and make a web view ready, unless one is ready or on its way. */
  def prewarm(): Unit = if (!warming && spare == null && Host.activity != null) {
    warming = true
    val activity = Host.activity
    val app = activity.getApplicationContext
    val thread = new Thread(() => {
      val began = System.nanoTime()
      // the provider loaded here, off the main thread: the user agent is the engine's to say, so asking loads it
      try WebSettings.getDefaultUserAgent(app)
      catch { case t: Throwable => Platform.log("[web] the engine could not be warmed: " + t.toString) }
      val loaded = (System.nanoTime() - began) / 1000000L
      activity.runOnUiThread(() => made(activity, loaded))
    }, "everyui-web-warm")
    thread.setDaemon(true)
    thread.start()
  }

  // on the main thread: the view, and its page process started by a blank page
  private def made(activity: android.app.Activity, loaded: Long): Unit = {
    warming = false
    if (activity eq Host.activity) {
      val began = System.nanoTime()
      try {
        spare = new WebView(activity)
        spare.loadUrl("about:blank")
        Platform.log("[web] warmed: the engine loaded in " + loaded.toString + " ms off the main thread, a view made " +
          "in " + ((System.nanoTime() - began) / 1000000L).toString + " ms")
      }
      catch { case t: Throwable => Platform.log("[web] no view could be made ready: " + t.toString) }
    }
  }

  /** The web view made ready, taken (none is kept after), or None: none made, or made for an activity since gone. */
  def take(): Option[WebView] = {
    val view = spare
    spare = null
    if (view == null) None
    else if (view.getContext eq Host.activity) Some(view)
    else {
      view.destroy()
      None
    }
  }
}
