package everyui.render

import android.webkit.{CookieManager, WebView, WebViewClient}

/** A page of the web's sandbox here (`everyui.component.WebView.sandbox`): Android's WebView has no `sandbox`, so the
  * nearest its settings come to an iframe's, put on as the view loads its page (and the page loaded again as they
  * change, since a setting goes with the loads after it).
  *
  * Sandboxed at all, the page has no bridge (`bridge` taken off the window, and `run` does nothing) unless the app
  * asked to hear it (`hear`), when the bridge is all it is lent; no files or content of the device (file and content
  * access off), no geolocation and no third-party cookies, and links that would hand the device something other than
  * a page (`intent:`, `market:`, `file:`, `content:`…) go nowhere (SandboxClient). Then by its flags: script runs
  * only with "allow-scripts"; DOM storage (localStorage, sessionStorage) is there only with "allow-same-origin"; a
  * form posted (the page's own POST) is refused without "allow-forms"; and a new window (`window.open`,
  * `target="_blank"`) opens nowhere without "allow-popups", where with it it replaces the page, as it does
  * unsandboxed.
  *
  * What it cannot do: the page keeps its address's origin, as Android has no opaque one, so the cookies of that origin
  * (the store is the app's, one for every view) still go with its requests, and with "allow-scripts" its script can
  * read those not marked HttpOnly and reach that origin as itself; a form sent by GET is a link to the WebView, and
  * goes. Show a sandboxed page from an origin that holds nothing of the app's, or as a `data:` address. */
class WebSandbox(view: WebView, bridge: String, bridgeObject: AnyRef) {
  private val settings = view.getSettings
  // as the view was made, to go back to unsandboxed
  private val fileAccess = settings.getAllowFileAccess
  private val contentAccess = settings.getAllowContentAccess
  private val thirdParty = CookieManager.getInstance().acceptThirdPartyCookies(view)
  private var source: String = ""
  private var flags: Option[Set[String]] = None
  private var hearing: Boolean = false

  /** Whether the page is sandboxed: told nothing, and heard only where the app asked (`hear`). */
  def sandboxed: Boolean = flags.nonEmpty

  /** Whether what the page says is heard: unsandboxed, or sandboxed and heard all the same. */
  def heard: Boolean = !sandboxed || hearing

  /** A sandboxed page lent the bridge (`on`), and nothing else, so that what it says is heard; the page loaded again
    * where it shows one and this changes what it has, as the bridge goes with the loads after it. */
  def hear(on: Boolean): Unit = if (on != hearing) {
    hearing = on
    if (sandboxed) {
      if (on) view.addJavascriptInterface(bridgeObject, bridge) else view.removeJavascriptInterface(bridge)
      if (source.nonEmpty) show()
    }
  }

  /** Show this address: a `Files.source` as what it holds, since a sandboxed page reads no file. */
  def load(url: String): Unit = if (url.trim.nonEmpty) {
    source = url.trim
    show()
  }

  private def show(): Unit = FileAccess.held(source) match {
    case Some((bytes, mime)) =>
      view.loadDataWithBaseURL(null, new String(bytes, "UTF-8"), mime.takeWhile(_ != ';').trim, "UTF-8", null)
    case None => view.loadUrl(source)
  }

  /** Sandboxed with `allowed` (None: not at all), and the page loaded again where it shows one. */
  def set(allowed: Option[Set[String]]): Unit = if (allowed != flags) {
    flags = allowed
    val on = allowed.nonEmpty
    def allows(flag: String): Boolean = !on || allowed.exists(_.contains(flag))
    settings.setJavaScriptEnabled(allows("allow-scripts"))
    settings.setDomStorageEnabled(allows("allow-same-origin"))
    settings.setAllowFileAccess(!on && fileAccess)
    settings.setAllowContentAccess(!on && contentAccess)
    settings.setGeolocationEnabled(!on)
    // a new window with no client to make it is a window refused; with none of them it replaces the page
    settings.setSupportMultipleWindows(!allows("allow-popups"))
    settings.setJavaScriptCanOpenWindowsAutomatically(on && allows("allow-popups"))
    CookieManager.getInstance().setAcceptThirdPartyCookies(view, !on && thirdParty)
    if (on) {
      if (hearing) view.addJavascriptInterface(bridgeObject, bridge) else view.removeJavascriptInterface(bridge)
      view.setWebViewClient(new SandboxClient(allows("allow-forms")))
    }
    else {
      view.addJavascriptInterface(bridgeObject, bridge)
      view.setWebViewClient(new WebViewClient)
    }
    if (source.nonEmpty) show()
  }
}
