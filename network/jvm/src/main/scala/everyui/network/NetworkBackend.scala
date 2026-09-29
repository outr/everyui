package everyui.network

import rapid.Task
import spice.http.WebSocket
import spice.http.client.{HttpClient, JVMHttpClientImplementation}

/** The desktop: Spice's client over the JDK's `java.net.http` (HTTP/2, WebSocket, TLS). */
object NetworkBackend {
  /** Where relative URLs go when the app names nowhere (`Network.origin`): nowhere, off the web. */
  def origin(): String = ""
  // a native image has no resources for moduload to scan, so the implementation is named (a no-op where it already is)
  SpiceHttp.use(JVMHttpClientImplementation)
  private lazy val client: HttpClient = SpiceHttp.client(JVMHttpClientImplementation)
  def webSocket(url: String): WebSocket = SpiceHttp.webSocket(client, url)
  def http(call: HttpCall): Task[HttpResult] = SpiceHttp.call(client, call)

  /** `text` as the bytes of its UTF-8, and back. */
  def utf8(text: String): Array[Byte] = text.getBytes("UTF-8")
  def text(bytes: Array[Byte]): String = new String(bytes, "UTF-8")
}
