package everyui.render

import android.webkit.{WebResourceRequest, WebResourceResponse, WebView, WebViewClient}

/** What a sandboxed page may load (WebSandbox): links stay in the view and go only to pages (`http:`, `https:`,
  * `data:`, `blob:`, `about:`), never out to whatever the device would hand the rest to; and, without `forms`, a page
  * posted in place of this one (a form sent by POST) is answered with nothing, so what it held goes nowhere. */
class SandboxClient(forms: Boolean) extends WebViewClient {
  override def shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
    !SandboxClient.pages.contains(Option(request.getUrl.getScheme).getOrElse("").toLowerCase)

  override def shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
    if (forms || !request.isForMainFrame || request.getMethod != "POST") null
    else new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", new java.util.HashMap[String, String](),
      new java.io.ByteArrayInputStream(Array.emptyByteArray))
}

object SandboxClient {
  /** The schemes a sandboxed page's links may go to. */
  val pages: Set[String] = Set("http", "https", "data", "blob", "about")
}
