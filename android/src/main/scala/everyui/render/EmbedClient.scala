package everyui.render

import android.webkit.{WebResourceRequest, WebResourceResponse, WebView, WebViewClient}
import everyui.component.{EmbedGuest, EmbedSite}

/** Serves a web guest's files to its EmbedView from the APK's assets, on a private https origin (`origin`) that never
  * reaches the network: `site.root` under the assets, and the guest's end of the channel (EmbedGuest) at
  * `everyui-embed.js`. A real origin, and a secure one, is what lets the guest start its workers from blobs and use
  * the clipboard, which a `file:` page may not. Everything is served as immutable but the page itself, since a
  * versioned root never changes under its name. `loaded` is called as the page finishes loading. */
class EmbedClient(site: EmbedSite, loaded: () => Unit) extends WebViewClient {
  override def shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = {
    val url = request.getUrl
    if (url.getHost != EmbedClient.host) null
    else {
      val path = Option(url.getPath).getOrElse("/").stripPrefix("/")
      val page = path == site.page.stripPrefix("/")
      val headers = new java.util.HashMap[String, String]()
      headers.put("Cache-Control", if (page || path == "everyui-embed.js") "no-cache" else "public, max-age=31536000, immutable")
      if (path == "everyui-embed.js")
        new WebResourceResponse("text/javascript", "UTF-8", 200, "OK", headers,
          new java.io.ByteArrayInputStream(EmbedGuest.script.getBytes("UTF-8")))
      else
        try new WebResourceResponse(EmbedClient.mime(path), "UTF-8", 200, "OK", headers,
          Host.activity.getAssets.open(site.resolve(path)))
        catch {
          case _: java.io.IOException =>
            new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", headers, new java.io.ByteArrayInputStream(Array.emptyByteArray))
        }
    }
  }

  override def onPageFinished(view: WebView, url: String): Unit =
    if (url != null && url.startsWith(EmbedClient.origin)) loaded()
}

object EmbedClient {
  /** A name no network answers to (`.invalid` is reserved for that), so nothing but this client ever serves it. */
  val host: String = "everyui-embed.invalid"
  val origin: String = "https://" + host + "/"

  /** The type a file is served as, by its extension. */
  def mime(path: String): String = {
    val dot = path.lastIndexOf('.')
    val ext = if (dot < 0) "" else path.substring(dot + 1).toLowerCase
    if (ext == "js" || ext == "mjs") "text/javascript"
    else if (ext == "css") "text/css"
    else if (ext == "html" || ext == "htm") "text/html"
    else if (ext == "json" || ext == "map") "application/json"
    else if (ext == "svg") "image/svg+xml"
    else if (ext == "png") "image/png"
    else if (ext == "ttf") "font/ttf"
    else if (ext == "woff") "font/woff"
    else if (ext == "woff2") "font/woff2"
    else if (ext == "wasm") "application/wasm"
    else "application/octet-stream"
  }
}
