package everyui.network

import org.scalajs.dom
import rapid.Task

import scala.scalajs.js
import scala.scalajs.js.typedarray.*

/** A multipart form on the web, as the browser sends one: a FormData of the parts, each file a Blob of its bytes and
  * type, sent with `fetch`, so the browser writes the body and its boundary. The answer is read as Spice's client reads
  * one: any status is a result, and only a request that got none (refused, timed out, blocked by CORS) fails. */
object WebForm {
  def send(call: HttpCall): Task[HttpResult] = {
    val form = new dom.FormData()
    call.parts.foreach {
      case FormPart.FieldPart(name, value) => form.append(name, value)
      case FormPart.FilePart(name, fileName, bytes, contentType) =>
        val blob = new dom.Blob(js.Array(bytes.toTypedArray.buffer), new dom.BlobPropertyBag { `type` = contentType })
        form.asInstanceOf[js.Dynamic].append(name, blob, fileName)
    }
    val headers = new dom.Headers()
    // the browser names the body's type itself, with its boundary
    call.headers.filter(_._1.toLowerCase != "content-type").foreach((k, v) => headers.append(k, v))
    val abort = new dom.AbortController()
    val seconds = if (call.timeout > 0.0) call.timeout else 60.0
    val timer = dom.window.setTimeout(() => abort.abort(), seconds * 1000.0)
    val init = js.Dynamic.literal(method = call.method, body = form, headers = headers, signal = abort.signal,
      credentials = if (call.credentials) "include" else "same-origin")
    val done = Task.completable[HttpResult]
    dom.fetch(call.url, init.asInstanceOf[dom.RequestInit]).`then`[Unit] { (r: dom.Response) =>
      r.text().`then`[Unit] { (text: String) =>
        dom.window.clearTimeout(timer)
        done.success(HttpResult(r.status, headersOf(r), text))
      }
      ()
    }.`catch`[Unit] { (e: Any) =>
      dom.window.clearTimeout(timer)
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
