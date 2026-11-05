package everyui.network

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import everyui.FxSpec
import everyui.error.Errors
import fabric.io.JsonParser
import fabric.str

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/** An app's problems at its server's log, against a server of the spec's own: a problem `Errors` heard sent with where
  * it was, its stack, the platform, the build, the app's own fields and headers; the same line with other ids sent
  * once, and no more than so many a minute; a failed request sent as a warning, and the log's own sends never; a
  * message shown sent as a warning. */
class RemoteLogSpec extends FxSpec {
  private val lines = new ConcurrentLinkedQueue[(Map[String, String], Option[String])]()

  private val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
  server.createContext("/log", (x: HttpExchange) => {
    val body = JsonParser(new String(x.getRequestBody.readAllBytes(), "UTF-8"))
    lines.add((body.asObj.value.map((k, v) => k -> v.asString).toMap,
      Option(x.getRequestHeaders.getFirst("Authorization"))))
    // the server failing its log is not itself logged
    x.sendResponseHeaders(500, -1L)
    x.close()
  })
  server.createContext("/missing", (x: HttpExchange) => {
    x.sendResponseHeaders(404, -1L)
    x.close()
  })
  server.start()
  private val base = "http://127.0.0.1:" + server.getAddress.getPort.toString

  override def afterAll(): Unit = {
    server.stop(0)
    super.afterAll()
  }

  /** A log installed for `test`, and everything it chained onto taken back after. */
  private def logging[T](perMinute: Int = 20)(test: RemoteLog => T): T = {
    lines.clear()
    val observe = Network.observe
    val forward = everyui.scribe.forward
    val log = new RemoteLog(base + "/log", headers = () => List("Authorization" -> "Bearer t0"),
      fields = () => List("deviceId" -> str("d1")), build = "abc123", perMinute = perMinute)
      .install(warnings = true, requests = true, toasts = true)
    try test(log) finally {
      log.uninstall()
      Network.observe = observe
      everyui.scribe.forward = forward
      Network.unobserved = Set.empty
    }
  }

  private def sent: List[Map[String, String]] = lines.asScala.toList.map(_._1)

  "A remote log" should {
    "send a problem with where it was, its stack, and what the app adds" in logging() { _ =>
      fx(Errors.report("pointer Click on Card", new IllegalStateException("card broke")))
      eventually(lines.size == 1) shouldBe true
      val (line, auth) = lines.peek()
      line("level") shouldBe "error"
      line("where") shouldBe "pointer Click on Card"
      line("message") shouldBe "java.lang.IllegalStateException: card broke"
      line("stack") should include("RemoteLogSpec")
      line("platform") shouldBe "desktop"
      line("build") shouldBe "abc123"
      line("deviceId") shouldBe "d1"
      auth shouldBe Some("Bearer t0")
    }
    "send a line once whatever ids are in it, and no more than so many a minute" in logging(perMinute = 3) { log =>
      fx {
        log.warn("card 812 failed to archive")
        log.warn("card 813 failed to archive")
        log.warn("card 9f3e2a1c-77 failed to archive")
        log.warn("one")
        log.warn("two")
        log.warn("three")
      }
      eventually(lines.size == 3) shouldBe true
      Thread.sleep(300L)
      sent.map(_("message")).sorted shouldBe List("card 812 failed to archive", "one", "two")
    }
    "send a failed request as a warning, and never its own sends" in logging() { log =>
      val failed = new java.util.concurrent.CountDownLatch(1)
      Network.get(base + "/missing").map(_ => failed.countDown()).startUnit()
      failed.await(10L, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
      eventually(lines.size == 1) shouldBe true
      // each send was answered 500, and none of them came back as a line of its own
      fx(log.info("after"))
      eventually(lines.size == 2) shouldBe true
      Thread.sleep(500L)
      sent.map(_("message")).head should startWith("http GET " + base + "/missing -> 404")
      sent.map(_("level")) shouldBe List("warn", "info")
    }
    "send scribe's warnings and errors" in logging() { _ =>
      fx(everyui.scribe.error("socket lost"))
      eventually(lines.size == 1) shouldBe true
      sent.head("level") shouldBe "error"
      sent.head("message") shouldBe "socket lost"
    }
    "send each message shown as a warning, and none once taken off" in {
      logging() { _ =>
        fx(everyui.kit.Toast.show("Could not archive the card"))
        eventually(lines.size == 1) shouldBe true
        sent.head("level") shouldBe "warn"
        sent.head("message") shouldBe "toast: Could not archive the card"
      }
      fx {
        everyui.kit.Toast.show("Saved")
        everyui.kit.Toast.hide()
      }
      Thread.sleep(300L)
      lines.size shouldBe 1
    }
  }

  "A log's throttle" should {
    "take the ids and numbers out of a line" in {
      LogThrottle.normal("card 812 (c42, 9f3e2a1c-77ab) failed after 3.5s") shouldBe "card # (#, #) failed after #"
    }
  }
}
