package everyui.render

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Int8Array}

/** Parts of a file fetched by a byte range (a BIF's index, and each of its frames: `everyui.trickplay.TrickPlay`):
  * `fetch` with a `Range` header, which a browser sends to another origin as a simple request (a single range is
  * CORS-safelisted), so the server need only allow the page to read its answer. A frame is a blob of its own, shown by
  * its `blob:` address and let go when it is forgotten. */
object ByteRanges {
  /** `length` bytes of `url` from `from`, `headers` with the request: Some once a 206 brings them (or a 200 from the
    * start, cut to the length asked), else None. */
  def read(url: String, headers: Map[String, String], from: Int, length: Int, done: Option[Array[Byte]] => Unit): Unit =
    ranged(url, headers, from, length, done(None)) { r =>
      val status = r.status.asInstanceOf[Int]
      if (status == 206 || (status == 200 && from == 0)) {
        val answered: js.Function1[ArrayBuffer, Unit] = buffer => {
          val view = new Int8Array(buffer, 0, math.min(length, buffer.byteLength))
          done(Some(Array.tabulate[Byte](view.length)(i => view(i))))
        }
        val failed: js.Function1[js.Any, Unit] = _ => done(None)
        r.arrayBuffer().`then`(answered, failed)
      } else done(None)
    }

  /** The same range as a picture: a `blob:` address of a 206 of exactly that length, or None. */
  def picture(url: String, headers: Map[String, String], from: Int, length: Int, done: Option[String] => Unit): Unit =
    ranged(url, headers, from, length, done(None)) { r =>
      if (r.status.asInstanceOf[Int] == 206) {
        val answered: js.Function1[dom.Blob, Unit] = blob =>
          done(if (blob.size.toInt == length) Some(dom.URL.createObjectURL(blob)) else None)
        val failed: js.Function1[js.Any, Unit] = _ => done(None)
        r.blob().`then`(answered, failed)
      } else done(None)
    }

  /** A picture from `picture` no longer wanted: its blob let go. */
  def forget(address: String): Unit = if (address.startsWith("blob:")) dom.URL.revokeObjectURL(address)

  // the request, its answer to `answered`, and `failed` where none came (no connection, another origin refusing)
  private def ranged(url: String, headers: Map[String, String], from: Int, length: Int, failed: => Unit)(
      answered: js.Dynamic => Unit): Unit = {
    val sent = headers + ("Range" -> ("bytes=" + from.toString + "-" + (from + length - 1).toString))
    val init = js.Dynamic.literal(mode = "cors", credentials = "omit", headers = js.Dictionary(sent.toSeq*))
    val ok: js.Function1[js.Dynamic, Unit] = r => answered(r)
    val not: js.Function1[js.Any, Unit] = _ => failed
    js.Dynamic.global.fetch(url, init).`then`(ok, not)
  }
}
