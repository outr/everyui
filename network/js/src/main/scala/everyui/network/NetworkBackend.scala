package everyui.network

import rapid.Task
import spice.http.WebSocket
import spice.http.client.{HttpClient, JSHttpClientImplementation}

/** The web: Spice's client over the browser's `WebSocket` and `fetch`. */
object NetworkBackend {
  /** Where relative URLs go when the app names nowhere (`Network.origin`): the page's origin. */
  def origin(): String = {
    val location = scala.scalajs.js.Dynamic.global.selectDynamic("location")
    if (scala.scalajs.js.isUndefined(location)) "" else location.origin.asInstanceOf[String]
  }
  private lazy val client: HttpClient = SpiceHttp.client(JSHttpClientImplementation)
  def webSocket(url: String): WebSocket = SpiceHttp.webSocket(client, url)
  /** A multipart form goes as the browser's own FormData (`WebForm`); anything else through Spice's client. */
  def http(call: HttpCall): Task[HttpResult] = if (call.parts.nonEmpty) WebForm.send(call) else SpiceHttp.call(client, call)

  /** `text` as the bytes of its UTF-8, and back. */
  def utf8(text: String): Array[Byte] = text.getBytes("UTF-8")
  def text(bytes: Array[Byte]): String = new String(bytes, "UTF-8")
}
