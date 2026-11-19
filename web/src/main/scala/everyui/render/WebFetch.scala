package everyui.render

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer

/** `fetch` (Chromium 42), else the same over an XMLHttpRequest, for an engine that has none (webOS 3's 38): what
  * EveryUI's own requests read of an answer is there alike. It is answered, as `fetch` is, once the headers are in,
  * `ok` and `status` (a file's 0 read as 200), `statusText`, `url`, `headers.get` and `forEach`, and its body as
  * `text()`, `json()`, `arrayBuffer()` and `blob()`; `body.cancel()` stops what is still coming. A request that gets
  * no answer fails. Of what it is sent: `method`, `headers` (an object of names), `body` and `credentials`
  * ("include" sends cookies to another origin). */
object WebFetch {
  def apply(url: String, init: js.Dynamic = js.Dynamic.literal()): js.Dynamic = WebFeatures.global("fetch") match {
    case Some(fetch) => fetch(url, init)
    case None => request(url, init).asInstanceOf[js.Dynamic]
  }

  /** The same, its answer read as the DOM's `Response`. */
  def response(url: String, init: js.Dynamic = js.Dynamic.literal()): js.Promise[dom.Response] =
    apply(url, init).asInstanceOf[js.Promise[dom.Response]]

  private def defined(v: js.Dynamic): Boolean = !js.isUndefined(v) && v != null

  private def request(url: String, init: js.Dynamic): js.Promise[js.Dynamic] =
    new js.Promise[js.Dynamic]((resolve, reject) => {
    val xhr = new dom.XMLHttpRequest()
    xhr.open(if (defined(init.method)) init.method.asInstanceOf[String] else "GET", url)
    xhr.responseType = "arraybuffer"
    xhr.withCredentials = defined(init.credentials) && init.credentials.asInstanceOf[String] == "include"
    if (defined(init.headers)) js.Object.keys(init.headers.asInstanceOf[js.Object])
      .foreach(name => xhr.setRequestHeader(name, init.headers.selectDynamic(name).asInstanceOf[String]))
    var answered = false
    var loaded = false
    var failed = false
    var waiting: List[() => Unit] = Nil
    def over(): Unit = { val told = waiting.reverse; waiting = Nil; told.foreach(_()) }
    // the body once all of it is in, or a failure where it never comes
    def whole[A](read: ArrayBuffer => A): js.Promise[A] = new js.Promise[A]((done, refused) => {
      def give(): Unit =
        if (loaded) done(read(xhr.response.asInstanceOf[ArrayBuffer])) else refused(new js.Error("aborted"))
      if (loaded || failed) give() else waiting = (() => give()) :: waiting
    })
    def text(buffer: ArrayBuffer): String = {
      val decoder = js.Dynamic.newInstance(js.Dynamic.global.TextDecoder)("utf-8")
      decoder.decode(js.Dynamic.newInstance(js.Dynamic.global.Uint8Array)(buffer)).asInstanceOf[String]
    }
    def answer(): Unit = if (!answered) {
      answered = true
      // a file's answer (from `file://`, as a TV's package is loaded) has no status
      val status = if (xhr.status == 0) 200 else xhr.status
      val headers = js.Dynamic.literal(
        get = ((name: String) => xhr.getResponseHeader(name)): js.Function1[String, String],
        forEach = ((f: js.Function2[String, String, Any]) => xhr.getAllResponseHeaders().split("\r\n")
          .filter(_.contains(':')).foreach { line =>
            val at = line.indexOf(':')
            f(line.substring(at + 1).trim, line.substring(0, at).trim.toLowerCase)
          }): js.Function1[js.Function2[String, String, Any], Unit])
      resolve(js.Dynamic.literal(
        ok = status >= 200 && status < 300, status = status, statusText = xhr.statusText, url = url, headers = headers,
        body = js.Dynamic.literal(cancel = (() => if (!loaded) {
          failed = true
          xhr.abort()
          over()
        }): js.Function0[Unit]),
        text = (() => whole(text)): js.Function0[js.Promise[String]],
        json = (() => whole(b => js.JSON.parse(text(b)))): js.Function0[js.Promise[js.Any]],
        arrayBuffer = (() => whole(b => b)): js.Function0[js.Promise[ArrayBuffer]],
        blob = (() => whole(b => new dom.Blob(js.Array(b),
          new dom.BlobPropertyBag { `type` = Option(xhr.getResponseHeader("content-type")).getOrElse("") })))
          : js.Function0[js.Promise[dom.Blob]]))
    }
    xhr.onreadystatechange = (_: dom.Event) =>
      if (xhr.readyState >= 2 && xhr.status != 0) answer()
    xhr.onload = (_: dom.Event) => { loaded = true; answer(); over() }
    def fail(why: String): Unit = {
      failed = true
      if (answered) over() else reject(new js.TypeError("Failed to fetch " + url + ": " + why))
    }
    xhr.onerror = (_: dom.ProgressEvent) => fail("no answer")
    xhr.ontimeout = (_: dom.Event) => fail("timed out")
    xhr.onabort = (_: dom.Event) => if (!failed) fail("aborted")
    try xhr.send(if (defined(init.body)) init.body.asInstanceOf[js.Any] else null)
    catch { case e: Throwable => fail(String.valueOf(e)) }
  })
}
