package everyui.network

import mockwebserver3.{Dispatcher, MockResponse, MockWebServer, RecordedRequest}
import okhttp3.Protocol
import okhttp3.tls.{HandshakeCertificates, HeldCertificate}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spice.http.client.{HttpClient, OkHttpClientImplementation}

import scala.concurrent.duration.*
import scala.util.Try

/** Android's client (OkHttp, through Spice) and an HTTP/2 connection the network drops without a word, as a Fire TV's
  * was as it woke: a server over TLS behind a relay (`DeadConnectionRelay`) that stops carrying the connections open as
  * it is frozen, every byte lost and nothing closed, while new ones go through. Unpinged, a call on the dead connection
  * waits its whole timeout; pinged (`SpiceHttp.ping`, made short here), the connection is found dead within two pings
  * and dropped from the pool, and a call caught on it is sent again on a fresh one by OkHttp itself (the connection
  * failed, not the call: nothing reached the server) and answered within two pings, not at its timeout; one made after
  * the connection lay idle and dead goes out on a fresh one at once. */
class DeadConnectionSpec extends AnyWordSpec with Matchers {
  SpiceHttp.use(OkHttpClientImplementation)

  private def withServer[T](test: (String, DeadConnectionRelay) => T): T = {
    val held = new HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    val certificates = new HandshakeCertificates.Builder().heldCertificate(held).build()
    val server = new MockWebServer()
    server.useHttps(certificates.sslSocketFactory())
    server.setProtocols(java.util.List.of(Protocol.HTTP_2, Protocol.HTTP_1_1))
    server.setDispatcher(new Dispatcher {
      override def dispatch(request: RecordedRequest): MockResponse =
        new MockResponse.Builder().code(200).body("ok " + request.getMethod).build()
    })
    server.start()
    val relay = new DeadConnectionRelay(server.getPort)
    try test("https://localhost:" + relay.port + "/", relay)
    finally {
      relay.close()
      Try(server.close())
    }
  }

  private def client(timeout: FiniteDuration, ping: Option[FiniteDuration]): HttpClient =
    SpiceHttp.client(OkHttpClientImplementation).ignoreSSLCertificates.timeout(timeout).pingInterval(ping)

  // the call made, how long it took and what came of it
  private def timed(c: HttpClient, call: HttpCall): (Long, Try[HttpResult]) = {
    val start = System.nanoTime()
    val result = Try(SpiceHttp.call(c, call).sync())
    ((System.nanoTime() - start) / 1000000L, result)
  }

  "A dead HTTP/2 connection" should {
    "keep a call on it waiting its whole timeout where nothing pings it" in withServer { (url, relay) =>
      val c = client(6.seconds, None)
      timed(c, HttpCall("GET", url))._2.get.body shouldBe "ok GET"
      relay.freeze()
      val (ms, result) = timed(c, HttpCall("POST", url + "login", body = Some("{}")))
      result.isFailure shouldBe true
      ms should be >= 5900L
    }

    "be found dead within two pings, a call on it sent again on a fresh one and answered" in
      withServer { (url, relay) =>
        val c = client(20.seconds, Some(1.second))
        timed(c, HttpCall("GET", url))._2.get.body shouldBe "ok GET"
        relay.connections shouldBe 1
        relay.freeze()
        // a POST on the dead connection: the ping unanswered, OkHttp fails the connection and sends the call again on
        // a fresh one itself (the connection failed, not the call), answered long before its timeout
        val (postMs, post) = timed(c, HttpCall("POST", url + "login", body = Some("{}")))
        post.get.body shouldBe "ok POST"
        postMs should be < 5000L
        relay.connections shouldBe 2
        // the next goes out on the same fresh connection at once
        val (nextMs, next) = timed(c, HttpCall("POST", url + "login", body = Some("{}")))
        next.get.body shouldBe "ok POST"
        nextMs should be < 2000L
        relay.connections shouldBe 2
        // a GET caught on a dead connection the same
        relay.freeze()
        val (getMs, get) = timed(c, HttpCall("GET", url))
        get.get.body shouldBe "ok GET"
        getMs should be < 5000L
        relay.connections shouldBe 3
      }

    "be dropped while it lies idle, so the call after it goes out fresh at once" in withServer { (url, relay) =>
      val c = client(20.seconds, Some(1.second))
      timed(c, HttpCall("GET", url))._2.get.body shouldBe "ok GET"
      relay.freeze()
      // (the time a person takes to type a password, in pings)
      Thread.sleep(3500L)
      val (ms, result) = timed(c, HttpCall("POST", url + "login", body = Some("{}")))
      result.get.body shouldBe "ok POST"
      ms should be < 2000L
      relay.connections shouldBe 2
    }

    "be pinged by the client every platform's backend makes" in {
      SpiceHttp.client(OkHttpClientImplementation).pingInterval shouldBe Some(SpiceHttp.ping)
      SpiceHttp.ping shouldBe 15.seconds
    }
  }
}
