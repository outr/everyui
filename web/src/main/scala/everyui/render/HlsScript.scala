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

  /** Where its files are: relative to the page, or a whole address. */
  var root: String = "everyui-hls/" + version

  private var loaded = false
  private var loading = false
  private var waiting: List[Boolean => Unit] = Nil

  /** The `Hls` class, once the script has loaded. */
  def hls: js.Dynamic = dom.window.asInstanceOf[js.Dynamic].Hls

  /** The script its worker runs, for hls.js's `workerPath`. */
  def worker: String = file("hls.worker.js")

  /** `f` with whether hls.js is in the page: at once where it is (loaded here before, or by the page itself), else
    * once its script has loaded or failed to. A failure is tried again by the next source that needs it. */
  def load(f: Boolean => Unit): Unit =
    if (loaded || !js.isUndefined(hls)) {
      loaded = true
      f(true)
    }
    else {
      waiting = f :: waiting
      if (!loading) {
        loading = true
        val tag = dom.document.createElement("script").asInstanceOf[dom.html.Script]
        tag.src = file("hls.min.js")
        tag.addEventListener("load", (_: dom.Event) => done(!js.isUndefined(hls)))
        tag.addEventListener("error", (_: dom.Event) => {
          Platform.log("[video] could not load " + tag.src)
          tag.parentNode.removeChild(tag)
          done(false)
        })
        dom.document.head.appendChild(tag)
      }
    }

  private def done(ok: Boolean): Unit = {
    loaded = ok
    loading = false
    val told = waiting.reverse
    waiting = Nil
    told.foreach(f => f(ok))
  }

  private def file(name: String): String = (if (root.endsWith("/")) root else root + "/") + name
}
