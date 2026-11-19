package everyui.render

import android.os.{Handler, Looper}
import android.webkit.WebView
import androidx.webkit.{WebViewCompat, WebViewFeature}
import org.json.JSONTokener

/** What a page's script made to download (a `blob:` address) read out of the page, as DownloadManager cannot fetch
  * one: the page fetches it into its own memory (`begin`), its size is heard, and its bytes are handed over a piece at
  * a time (BlobReading) through the view's own script calls (`evaluateJavascript`), so nothing is lent the page for it
  * and a sandboxed page with no bridge downloads too. Up to `limit` bytes; a larger one is refused, as the whole of it
  * is held in the page and passed through script. Only the page's own frame: a blob of a frame inside it is another
  * document's, which the page may not be able to fetch.
  *
  * `script` is given each document at its start (beside WebViewParent's, through androidx.webkit): it notes the
  * `download` name of each link clicked (`<a download="x">`, in the page or not, clicked by a person or by script), so
  * a Blob is saved by its name, and keeps a noted Blob's address working for a minute after the page revokes it, as a
  * page often does at once after its click, before this has had the bytes. Where the document-start script cannot run
  * (no androidx.webkit, a web view before Chromium 87) a Blob is saved by a name made from its type, and one revoked at
  * once is not saved. */
object PageBlob {
  /** The most a Blob saved from a page may be. */
  val limit: Long = 256L * 1024 * 1024
  private[render] val handler = new Handler(Looper.getMainLooper)
  private[render] val writer = java.util.concurrent.Executors.newSingleThreadExecutor(r => {
    val thread = new Thread(r, "everyui-page-downloads")
    thread.setDaemon(true)
    thread
  })
  private var made = 0

  val script: String =
    "(function () { var w = window; if (w.__everyuiDownloads) return; var named = []; " +
      "w.__everyuiDownloads = { named: named, saves: {} }; function note(a) { try { if (a && a.hasAttribute('download') " +
      "&& a.href) { named.push([a.href, a.getAttribute('download')]); if (named.length > 20) named.shift(); } } " +
      "catch (e) {} } w.addEventListener('click', function (e) { var t = e.target; while (t && t.tagName !== 'A') " +
      "t = t.parentElement; note(t); }, true); var click = HTMLAnchorElement.prototype.click; " +
      "HTMLAnchorElement.prototype.click = function () { note(this); return click.apply(this, arguments); }; " +
      "var send = EventTarget.prototype.dispatchEvent; EventTarget.prototype.dispatchEvent = function (e) { " +
      "if (this instanceof HTMLAnchorElement && e && e.type === 'click') note(this); return send.apply(this, arguments); }; " +
      "var revoke = URL.revokeObjectURL; URL.revokeObjectURL = function (u) { if (named.some(function (n) { " +
      "return n[0] === u; })) setTimeout(function () { revoke.call(URL, u); }, 60000); else revoke.call(URL, u); }; })();"

  /** The page's documents given `script` at their start, where the web view can run one. */
  def lend(view: WebView): Unit =
    try {
      if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        WebViewCompat.addDocumentStartJavaScript(view, script, java.util.Collections.singleton("*"))
    }
    catch { case _: NoClassDefFoundError => () }

  /** The `download` name the page's link to `url` was clicked with, or "" (none noted, or no script in the page). */
  def named(view: WebView, url: String)(answer: String => Unit): Unit =
    if (!view.getSettings.getJavaScriptEnabled) answer("")
    else
      // matched by its length and ends, as a `data:` address may be megabytes long
      ask(view, "(function () { var d = window.__everyuiDownloads; if (!d) return ''; for (var i = d.named.length - 1; " +
        "i >= 0; i--) { var u = d.named[i][0]; if (u.length === " + url.length + " && u.slice(0, 256) === " +
        quote(url.take(256)) + " && u.slice(-64) === " + quote(url.takeRight(64)) + ") return d.named[i][1] || ''; } " +
        "return ''; })()", 5.0) {
        case s: String => answer(s)
        case _ => answer("")
      }

  /** A Blob at `url` read into the page: `sized` hears it once the page holds it, `failed` why it could not be. The
    * page lets it go after five minutes unless it is being saved by then. */
  def begin(view: WebView, url: String)(sized: BlobReading => Unit, failed: String => Unit): Unit =
    if (!view.getSettings.getJavaScriptEnabled) failed("the page runs no script")
    else {
      made += 1
      val reading = new BlobReading(view, quote("r" + made))
      ask(view, "(function () { var w = window, d = w.__everyuiDownloads = w.__everyuiDownloads || " +
        "{ named: [], saves: {} }, k = " + reading.key + ", s = d.saves[k] = {}; fetch(" + quote(url) + ")" +
        ".then(function (r) { s.type = r.headers.get('content-type') || ''; return r.arrayBuffer(); }).then(function (b) { if (b.byteLength > " + limit + ") " +
        "s.error = 'larger than " + limit + " bytes (' + b.byteLength + ')'; else s.bytes = new Uint8Array(b); })" +
        ".catch(function (e) { s.error = String(e); }); setTimeout(function () { if (!s.taken) delete d.saves[k]; }, " +
        "300000); return 1; })()", 10.0) {
        case null => failed("the page did not answer")
        case _ => reading.poll(System.currentTimeMillis + 120000, sized, failed)
      }
    }

  /** Bytes written into `file` and finished on the writing thread, then `done` on the main one. */
  def write(file: DownloadFile, bytes: Array[Byte], done: Boolean => Unit): Unit = writer.execute(() => {
    val ok =
      try { file.write(bytes); file.finish(); true }
      catch { case t: Throwable => Platform.log("[web] a download could not be written: " + t); file.abandon(); false }
    handler.post(() => done(ok))
  })

  /** `script` run in the page and its value handed to `answer`: null where none came in `seconds`, or the view is gone. */
  private[render] def ask(view: WebView, script: String, seconds: Double)(answer: Any => Unit): Unit = {
    var answered = false
    def once(value: Any): Unit = if (!answered) {
      answered = true
      answer(value)
    }
    handler.postDelayed(() => once(null), (seconds * 1000).toLong)
    try view.evaluateJavascript(script, r => once(if (r == null || r == "null") null else new JSONTokener(r).nextValue()))
    catch { case _: Throwable => once(null) }
  }

  private[render] def quote(s: String): String = org.json.JSONObject.quote(s)
}
