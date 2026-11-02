package everyui.render

import org.scalajs.dom

import scala.scalajs.js

/** hls.js in the page, for an HLS stream in a browser with no HLS of its own (WebMedia): loaded by `<script src>` from
  * the files served next to the page, once, the first time a source needs it, and never before. The files come in
  * everyui-web (`everyui-hls/<version>/`), and sbt-everyui's `everyuiWebGuests` copies them out beside the page, so a
  * Content-Security-Policy of `script-src 'self'` holds.
  *
  * {{{
  *   HlsScript.root = "/static/everyui-hls/" + HlsScript.version   // served somewhere else
  * }}}
  */
object HlsScript {
  /** The version of hls.js bundled (project/HlsAssets.scala). */
  val version: String = "1.7.3"

  /** Where its files are: relative to the page's base (its `<base href>`, else its address), under a sealed bundle's
    * (WebAssets), or a whole address. */
  var root: String = "everyui-hls/" + version

  /** The `Hls` class, once the script has loaded. */
  def hls: js.Dynamic = dom.window.asInstanceOf[js.Dynamic].Hls

  /** The script its worker runs, for hls.js's `workerPath`. */
  def worker: String = file("hls.worker.js")

  /** `f` with whether hls.js is in the page: at once where it is (loaded here before, or by the page itself), else
    * once its script has loaded or failed to. A failure is tried again by the next source that needs it. */
  def load(f: Boolean => Unit): Unit = script.load(f)

  private val script = new PageScript("video", () => !js.isUndefined(hls), () => file("hls.min.js"))

  // a whole address (PageScript.file): hls.js resolves its workerPath against the page's own address
  private def file(name: String): String = PageScript.file(root, name)
}
