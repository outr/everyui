package everyui.network

import org.scalajs.dom
import rapid.Task

import scala.scalajs.js
import scala.scalajs.js.typedarray.*

/** A multipart form on the web, as the browser sends one: a FormData of the parts, each file a Blob of its bytes and
  * type, sent with `fetch` (`WebFetch`: an XMLHttpRequest where there is none), so the browser writes the body and
  * its boundary. The answer is read as Spice's client reads one: any status is a result, and only a request that got
  * none (refused, timed out, blocked by CORS) fails. */
object WebForm {
  def send(call: HttpCall): Task[HttpResult] = {
    val form = new dom.FormData()
    call.parts.foreach {
      case FormPart.FieldPart(name, value) => form.append(name, value)
      case FormPart.FilePart(name, fileName, bytes, contentType) =>
        val blob = new dom.Blob(js.Array(bytes.toTypedArray.buffer), new dom.BlobPropertyBag { `type` = contentType })
        form.asInstanceOf[js.Dynamic].append(name, blob, fileName)
    }
    // the browser names the body's type itself, with its boundary
    val headers = js.Dictionary(call.headers.filter(_._1.toLowerCase != "content-type")*)
    val init = js.Dynamic.literal(method = call.method, body = form, headers = headers,
      credentials = if (call.credentials) "include" else "same-origin")
    val done = Task.completable[HttpResult]
    // ended at its time by an abort (AbortController, Chromium 66), else failed then and its late answer let go
    var over = false
    val abort: () => Unit =
      if (js.typeOf(js.Dynamic.global.AbortController) == "undefined") { () =>
        if (!over) {
          over = true
          done.failure(new RuntimeException("No response from " + call.method + " " + call.url + ": timed out"))
        }
      }
      else {
        val controller = new dom.AbortController()
        init.updateDynamic("signal")(controller.signal)
        () => controller.abort()
      }
    val seconds = if (call.timeout > 0.0) call.timeout else 60.0
    val timer = dom.window.setTimeout(() => abort(), seconds * 1000.0)
    everyui.render.WebFetch.response(call.url, init).`then`[Unit] { (r: dom.Response) =>
      if (call.binary) r.arrayBuffer().`then`[Unit] { (buffer: ArrayBuffer) =>
        dom.window.clearTimeout(timer)
        val bytes = new Int8Array(buffer).toArray
        if (!over) done.success(HttpResult(r.status, headersOf(r), new String(bytes, "UTF-8"), Some(bytes)))
      }
      else r.text().`then`[Unit] { (text: String) =>
        dom.window.clearTimeout(timer)
        if (!over) done.success(HttpResult(r.status, headersOf(r), text))
      }
      ()
    }.`catch`[Unit] { (e: Any) =>
      dom.window.clearTimeout(timer)
      if (!over)
        done.failure(new RuntimeException("No response from " + call.method + " " + call.url + ": " + String.valueOf(e)))
    }
    done
  }

  private def headersOf(r: dom.Response): List[(String, String)] = {
    var out: List[(String, String)] = Nil
    r.headers.asInstanceOf[js.Dynamic].forEach((value: String, name: String) => out = (name -> value) :: out)
    out.reverse
  }
}
