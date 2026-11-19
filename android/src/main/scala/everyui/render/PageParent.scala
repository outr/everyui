package everyui.render

import android.webkit.WebView
import androidx.webkit.{WebViewCompat, WebViewFeature}

/** A page's web view (Nodes.web) given everyui.component.WebViewParent's script at the start of every document it
  * shows, so a page that posts to its parent (`parent.postMessage`) is heard through `bridge` as on the web.
  *
  * Through androidx.webkit (EveryUILibraries.webkit), whose document-start script runs before anything of the page's
  * own. Where the app's build leaves it out, or the device's web view is too old to run one (before Chromium 87), the
  * page is heard through `<bridge>` alone, as before, and that is logged once. */
object PageParent {
  private var said = false

  def lend(view: WebView, bridge: String): Unit =
    try {
      if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        WebViewCompat.addDocumentStartJavaScript(view, everyui.component.WebViewParent.script(bridge),
          java.util.Collections.singleton("*"))
      else unlent("this web view runs no script at a document's start")
    }
    catch { case _: NoClassDefFoundError => unlent("androidx.webkit is not in the app (EveryUILibraries.webkit)") }

  private def unlent(why: String): Unit = if (!said) {
    said = true
    Platform.log("[web] a page's parent.postMessage is not heard, only its bridge: " + why)
  }
}
