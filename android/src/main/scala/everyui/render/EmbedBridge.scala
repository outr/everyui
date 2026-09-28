package everyui.render

/** What a web guest calls to talk to the app (`EveryUIEmbed.postMessage(text)`, EmbedView): called on the web view's
  * own thread, and handed on to the UI thread. */
class EmbedBridge(view: EmbedView) {
  @android.webkit.JavascriptInterface
  def postMessage(message: String): Unit =
    Host.activity.runOnUiThread(new Runnable {
      override def run(): Unit = view.said(message)
    })
}
