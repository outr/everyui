package everyui.render

import org.scalajs.dom

import scala.scalajs.js

/** A library's script put in the page by `<script src>` (hls.js, LiveKit's client): once, the first time something
  * needs it, and never before. `present` says whether it is in the page (its global defined), `src` where it is, and
  * `what` names it in the log where it cannot be loaded. */
class PageScript(what: String, present: () => Boolean, src: () => String) {
  private var loaded = false
  private var loading = false
  private var waiting: List[Boolean => Unit] = Nil

  /** `f` with whether the script is in the page: at once where it is (loaded here before, or by the page itself),
    * else once it has loaded or failed to. A failure is tried again by the next that needs it. */
  def load(f: Boolean => Unit): Unit =
    if (loaded || present()) {
      loaded = true
      f(true)
    }
    else {
      waiting = f :: waiting
      if (!loading) {
        loading = true
        val tag = dom.document.createElement("script").asInstanceOf[dom.html.Script]
        tag.src = src()
        tag.addEventListener("load", (_: dom.Event) => done(present()))
        tag.addEventListener("error", (_: dom.Event) => {
          Platform.log("[" + what + "] could not load " + tag.src)
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
}

object PageScript {
  /** `name` under `root`, a whole address resolved against the page's base (its `<base href>`) rather than left to
    * each user: a library resolves what it loads itself against the page's own address, which on a deep route
    * (/play/show/1973) a single-page host answers with the page itself. */
  def file(root: String, name: String): String =
    new dom.URL(WebAssets.url((if (root.endsWith("/")) root else root + "/") + name), dom.document.baseURI).href
}
