package everyui.render

import android.webkit.WebView

/** A Blob a page holds (PageBlob.begin), its bytes read out of the page and written into a file by `save`: `piece`
  * raw bytes at a time, as base64 through the view's script calls, each written on the writing thread before the next
  * is asked for, so no more than a piece is held here at once. */
class BlobReading(view: WebView, val key: String) {
  private val piece = 512 * 1024
  private val at = "(window.__everyuiDownloads || { saves: {} }).saves[" + key + "]"
  var size: Long = 0
  /** The Blob's own type, as the page made it ("" where it gave none). */
  var mime: String = ""

  private[render] def poll(until: Long, sized: BlobReading => Unit, failed: String => Unit): Unit =
    PageBlob.ask(view, "(function () { var s = " + at + "; return !s ? 'gone' : s.error ? 'error: ' + s.error : " +
      "s.bytes ? 'size:' + s.bytes.length + ';' + s.type : 'wait'; })()", 10.0) {
      case "wait" if System.currentTimeMillis < until => PageBlob.handler.postDelayed(() => poll(until, sized, failed), 50)
      case s: String if s.startsWith("size:") =>
        size = s.drop(5).takeWhile(_ != ';').toLong
        mime = s.dropWhile(_ != ';').drop(1).takeWhile(_ != ';').trim
        sized(this)
      case s: String => failed(if (s == "wait") "the page did not read it in two minutes" else s)
      case _ => failed("the page did not answer")
    }

  /** The bytes written into `file`, then `done(true)` on the main thread; `done(false)` where the page stopped
    * answering or the file could not be written, the file abandoned. */
  def save(file: DownloadFile, done: Boolean => Unit): Unit = {
    PageBlob.ask(view, "(function () { var s = " + at + "; if (s) s.taken = true; return 1; })()", 10.0)(_ => ())
    next(file, 0L, done)
  }

  private def next(file: DownloadFile, from: Long, done: Boolean => Unit): Unit =
    if (from >= size) {
      PageBlob.ask(view, "(function () { var d = window.__everyuiDownloads; if (d) delete d.saves[" + key + "]; " +
        "return 1; })()", 10.0)(_ => ())
      PageBlob.writer.execute(() => {
        val ok =
          try { file.finish(); true }
          catch { case t: Throwable => Platform.log("[web] a download could not be finished: " + t); file.abandon(); false }
        PageBlob.handler.post(() => done(ok))
      })
    }
    else PageBlob.ask(view, "(function () { var b = " + at + ".bytes.subarray(" + from + ", " + (from + piece) +
      "), t = ''; for (var i = 0; i < b.length; i += 32768) t += String.fromCharCode.apply(null, " +
      "b.subarray(i, i + 32768)); return btoa(t); })()", 30.0) {
      case s: String =>
        PageBlob.writer.execute(() => {
          val ok =
            try { file.write(android.util.Base64.decode(s, android.util.Base64.DEFAULT)); true }
            catch { case t: Throwable => Platform.log("[web] a download could not be written: " + t); false }
          if (ok) PageBlob.handler.post(() => next(file, from + piece, done))
          else {
            file.abandon()
            PageBlob.handler.post(() => done(false))
          }
        })
      case _ =>
        PageBlob.writer.execute(() => file.abandon())
        done(false)
    }
}
