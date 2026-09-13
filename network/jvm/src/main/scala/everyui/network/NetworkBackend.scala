package everyui.network

import rapid.Task
import spice.http.WebSocket
import spice.http.client.{HttpClient, JVMHttpClientImplementation}

/** The desktop: Spice's client over the JDK's `java.net.http` (HTTP/2, WebSocket, TLS). */
object NetworkBackend:
  // a native image has no resources for moduload to scan, so the implementation is named (a no-op where it already is)
  SpiceHttp.use(JVMHttpClientImplementation)
  private lazy val client: HttpClient = SpiceHttp.client(JVMHttpClientImplementation)
  def webSocket(url: String): WebSocket = SpiceHttp.webSocket(client, url)
  def http(call: HttpCall): Task[HttpResult] = SpiceHttp.call(client, call)
