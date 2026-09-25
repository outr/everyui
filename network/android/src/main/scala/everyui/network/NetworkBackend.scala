package everyui.network

import rapid.Task
import spice.http.WebSocket
import spice.http.client.{HttpClient, OkHttpClientImplementation}

/** Android: Spice's client over OkHttp (the platform has no `java.net.http`). */
object NetworkBackend {
  // Android's runtime has no virtual threads (Loom): rapid runs its fibers on a thread pool instead.
  rapid.Task.Virtual = false
  // An APK carries no `moduload.list` resource for Spice to find its client by, so it is named here.
  SpiceHttp.use(OkHttpClientImplementation)

  /** Runs the object's initialiser; the activity calls it before anything starts a Task. And makes the sockets'
    * client now, on a thread of its own: making it indexes every root the device trusts, a fifth of a second on a
    * television, which the first socket opened paid for on the thread that draws, in the middle of the first screen. A
    * socket made and never opened is what makes it: it connects nothing, and it is dropped. */
  def init(): Unit = {
    val warm = new Thread(new Runnable {
      override def run(): Unit = try SpiceHttp.webSocket(client, "wss://localhost/") catch { case _: Throwable => () }
    }, "everyui-network-warm")
    warm.setDaemon(true)
    warm.start()
  }

  private lazy val client: HttpClient = SpiceHttp.client(OkHttpClientImplementation)
  def webSocket(url: String): WebSocket = SpiceHttp.webSocket(client, url)
  def http(call: HttpCall): Task[HttpResult] = SpiceHttp.call(client, call)
}
