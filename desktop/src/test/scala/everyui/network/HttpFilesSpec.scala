package everyui.network

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import everyui.FxSpec
import everyui.file.SaveResult
import everyui.render.FileAccess

import java.net.InetSocketAddress
import java.nio.file.{Files => NioFiles}
import java.util.concurrent.atomic.AtomicInteger

/** Bytes to and from a server on the desktop (Spice over java.net.http), against a server of the spec's own: a POST
  * answered with a file of every byte value, read as bytes with its type and name; bytes sent as they are; and the file
  * saved where the dialog (the spec's) says, by the server's name or one given first, with a failed status saving nothing
  * and a closed dialog making no request. */
class HttpFilesSpec extends FxSpec {
  private val file: Array[Byte] = Array.tabulate[Byte](70000)(i => ((i * 7 + i / 256) % 256).toByte)
  private val exports = new AtomicInteger(0)

  private val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
  server.createContext("/export", (x: HttpExchange) => {
    exports.incrementAndGet()
    val asked = new String(x.getRequestBody.readAllBytes(), "UTF-8")
    if (asked.contains("\"deny\"")) answer(x, 403, "text/plain", "nope".getBytes("UTF-8"))
    else {
      x.getResponseHeaders.add("Content-Disposition", "attachment; filename=\"export 1.bin\"; filename*=UTF-8''export%20%C3%A9.bin")
      answer(x, 200, "application/octet-stream", file)
    }
  })
  server.createContext("/echo", (x: HttpExchange) => {
    val body = x.getRequestBody.readAllBytes()
    answer(x, 200, Option(x.getRequestHeaders.getFirst("Content-Type")).getOrElse("none"), body)
  })
  server.start()
  private val base = "http://127.0.0.1:" + server.getAddress.getPort.toString

  override def afterAll(): Unit = {
    server.stop(0)
    super.afterAll()
  }

  private def answer(x: HttpExchange, status: Int, contentType: String, body: Array[Byte]): Unit = {
    x.getResponseHeaders.add("Content-Type", contentType)
    x.sendResponseHeaders(status, body.length.toLong)
    x.getResponseBody.write(body)
    x.close()
  }

  /** What a Task answered, once it has, waited for a network's while. */
  private def await[T](start: (T => Unit) => Unit): T = {
    val latch = new java.util.concurrent.CountDownLatch(1)
    var result: Option[T] = None
    fx(start { r =>
      result = Some(r)
      latch.countDown()
    })
    latch.await(20, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
    result.get
  }

  private def http(call: HttpCall): HttpResult = await[HttpResult](done => Network.http(call).map(done).start())

  "A binary call" should {
    "read the response as its bytes, with its type and file name" in {
      val r = http(HttpCall("POST", base + "/export", body = Some("{\"auth\":\"t\"}"), binary = true))
      r.status shouldBe 200
      r.bytes.get.toList shouldBe file.toList
      r.mime shouldBe Some("application/octet-stream")
      r.fileName shouldBe Some("export é.bin")
    }

    "leave a text call's body as text, with no bytes" in {
      val r = http(HttpCall("POST", base + "/echo", body = Some("héllo"), contentType = "text/plain"))
      r.body shouldBe "héllo"
      r.bytes shouldBe None
    }

    "send a body of bytes as they are, of its type" in {
      val sent = Array.tabulate[Byte](256)(_.toByte)
      val r = http(HttpCall("PUT", base + "/echo", bodyBytes = Some(sent), contentType = "image/png", binary = true))
      r.bytes.get.toList shouldBe sent.toList
      r.mime shouldBe Some("image/png")
    }
  }

  "HttpFiles.save" should {
    val dir = NioFiles.createTempDirectory("everyui-httpfiles-spec")
    var suggested: List[String] = Nil
    def answering(target: Option[java.io.File]): Unit = FileAccess.chooseSave = name => {
      suggested = suggested :+ name
      target
    }
    def saved(call: HttpCall, name: Option[String] = None): SaveResult =
      await[SaveResult](done => HttpFiles.save(call, name).map(done).start())

    "save the answer by the server's name" in {
      val target = dir.resolve("a.bin").toFile
      answering(Some(target))
      saved(HttpCall("POST", base + "/export", body = Some("{}"))) shouldBe SaveResult.Saved(Some(target.getPath))
      suggested.last shouldBe "export é.bin"
      NioFiles.readAllBytes(target.toPath).toList shouldBe file.toList
    }

    "offer the name given, and make the request once a file is chosen" in {
      val target = dir.resolve("b.bin").toFile
      answering(Some(target))
      saved(HttpCall("POST", base + "/export", body = Some("{}")), Some("mine.bin")) shouldBe SaveResult.Saved(Some(target.getPath))
      suggested.last shouldBe "mine.bin"
      NioFiles.readAllBytes(target.toPath).toList shouldBe file.toList
    }

    "make no request when the dialog is closed" in {
      answering(None)
      val before = exports.get()
      saved(HttpCall("POST", base + "/export", body = Some("{}")), Some("x.bin")) shouldBe SaveResult.Cancelled
      exports.get() shouldBe before
    }

    "save nothing for a status that is not a success" in {
      val target = dir.resolve("c.bin").toFile
      answering(Some(target))
      saved(HttpCall("POST", base + "/export", body = Some("{\"deny\":1}")), Some("c.bin")) shouldBe
        SaveResult.SaveFailed("HTTP 403: nope")
      target.exists() shouldBe false
      saved(HttpCall("POST", base + "/export", body = Some("{\"deny\":1}"))) shouldBe SaveResult.SaveFailed("HTTP 403: nope")
    }
  }
}
