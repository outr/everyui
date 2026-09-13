package everyui.network

import rapid.Task
import spice.http.WebSocket
import spice.http.client.{HttpClient, OkHttpClientImplementation}

/** Android: Spice's client over OkHttp (the platform has no `java.net.http`). */
object NetworkBackend:
  // Android's runtime has no virtual threads (Loom): rapid runs its fibers on a thread pool instead.
  rapid.Task.Virtual = false
  // An APK carries no `moduload.list` resource for Spice to find its client by, so it is named here.
  SpiceHttp.use(OkHttpClientImplementation)

  /** Runs the object's initialiser; the activity calls it before anything starts a Task. */
  def init(): Unit = ()

  private lazy val client: HttpClient = SpiceHttp.client(OkHttpClientImplementation)
  def webSocket(url: String): WebSocket = SpiceHttp.webSocket(client, url)
  def http(call: HttpCall): Task[HttpResult] = SpiceHttp.call(client, call)
