package everyui.network

import rapid.Task
import spice.http.{Headers, HttpMethod, HttpRequest, WebSocket}
import spice.http.client.{ClientPlatform, HttpClient, HttpClientImplementation, HttpClientImplementationManager, RetryManager}
import spice.http.client.intercept.Interceptor
import spice.http.content.StringContent
import spice.net.{ContentType, DNS, URL}

import scala.concurrent.duration.*

/** The network module on a backend where Spice's own client runs (the JVM, Scala.js): one `HttpClient` over the
  * implementation the backend names — named rather than found through moduload's classpath scan, which an APK
  * or a native image has no resources for. */
object SpiceHttp {
  /** Name the client implementation for `HttpClient`'s companion, which asks the implementation manager as it
    * initialises: a no-op where moduload already found one on the classpath. */
  def use(implementation: HttpClientImplementation): Unit =
    try HttpClientImplementationManager.register(_ => implementation)
    catch case _: IllegalArgumentException => ()

  def client(implementation: HttpClientImplementation): HttpClient = HttpClient(
    request = HttpRequest(),
    implementation = implementation,
    retryManager = RetryManager.simple(retries = 2, delay = 1.second),
    interceptor = Interceptor.empty,
    saveDirectory = ClientPlatform.defaultSaveDirectory,
    timeout = 60.seconds,
    streamingTimeout = None,
    pingInterval = None,
    dns = DNS.default,
    dropNullValuesInJson = false,
    sessionManager = None,
    failOnHttpStatus = false,
    validateSSLCertificates = true,
    proxy = None,
    failures = 0
  )

  def webSocket(client: HttpClient, url: String): WebSocket =
    client.implementation.instance(client).webSocket(URL.parse(url), Headers.empty)

  def call(client: HttpClient, call: HttpCall): Task[HttpResult] = {
    var c = client.url(URL.parse(call.url)).method(HttpMethod(call.method))
    if (call.timeout > 0.0) c = c.timeout((call.timeout * 1000.0).toLong.millis)
    call.headers.foreach((k, v) => c = c.header(k, v))
    call.body.foreach(b => c = c.content(StringContent(b, ContentType.parse(call.contentType))))
    c.send().flatMap { response =>
      response.content.map(_.asString).getOrElse(Task.pure("")).map { body =>
        HttpResult(response.status.code, response.headers.map.toList.flatMap((k, vs) => vs.map(v => k -> v)), body)
      }
    }
  }
}
