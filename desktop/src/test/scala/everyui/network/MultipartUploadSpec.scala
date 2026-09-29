package everyui.network

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import everyui.FxSpec
import fabric.rw.*

import java.net.InetSocketAddress

/** A multipart upload on the desktop (Spice over java.net.http), against a server of the spec's own that takes the
  * body apart: a JSON field and a file of every byte value arrive as they were, and a picked file is a part by its
  * name and type. */
class MultipartUploadSpec extends FxSpec {
  private val file: Array[Byte] = Array.tabulate[Byte](70000)(i => ((i * 7 + i / 256) % 256).toByte)
  @volatile private var lastUpload: Map[String, (Map[String, String], Array[Byte])] = Map.empty
  @volatile private var lastType: String = ""

  private val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
  server.createContext("/upload", (x: HttpExchange) => {
    val contentType = x.getRequestHeaders.getFirst("Content-Type")
    lastType = contentType
    lastUpload = parts(x.getRequestBody.readAllBytes(), contentType.substring(contentType.indexOf("boundary=") + 9))
    answer(x, 200, "application/json", ("{\"parts\":" + lastUpload.size.toString + "}").getBytes("UTF-8"))
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

  /** The parts of a multipart body, by name: each one's headers and its bytes. */
  private def parts(body: Array[Byte], boundary: String): Map[String, (Map[String, String], Array[Byte])] = {
    val latin = new String(body, "ISO-8859-1")
    val delimiter = "--" + boundary
    latin.split(java.util.regex.Pattern.quote(delimiter)).toList.drop(1).takeWhile(!_.startsWith("--")).map { chunk =>
      val content = chunk.substring(2, chunk.length - 2)
      val split = content.indexOf("\r\n\r\n")
      val headers = content.substring(0, split).split("\r\n").toList.map { line =>
        val colon = line.indexOf(":")
        line.substring(0, colon).toLowerCase -> new String(line.substring(colon + 1).trim.getBytes("ISO-8859-1"), "UTF-8")
      }.toMap
      val name = "name=\"([^\"]*)\"".r.findFirstMatchIn(headers("content-disposition")).get.group(1)
      name -> (headers, content.substring(split + 4).getBytes("ISO-8859-1"))
    }.toMap
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

  "A multipart call" should {
    "reach the server as a JSON field and a file, byte for byte" in {
      val r = http(HttpCall.multipart(base + "/upload", List(
        FormPart.json("request", Map("token" -> "t", "note" -> "a\nb")),
        FormPart.file("file", "pic \"1\" é.bin", file))))
      r.body shouldBe "{\"parts\":2}"
      lastType should fullyMatch regex "multipart/form-data; boundary=----EveryUIFormBoundary[A-Za-z0-9]{24}"
      val (fieldHeaders, field) = lastUpload("request")
      new String(field, "UTF-8") shouldBe "{\"token\":\"t\",\"note\":\"a\\nb\"}"
      fieldHeaders.contains("content-type") shouldBe false
      val (fileHeaders, bytes) = lastUpload("file")
      fileHeaders("content-disposition") shouldBe "form-data; name=\"file\"; filename=\"pic %221%22 é.bin\""
      fileHeaders("content-type") shouldBe "application/octet-stream"
      bytes.toList shouldBe file.toList
    }
  }

  "HttpFiles.part" should {
    "make a picked file a part, by its name and type, its bytes read" in {
      val picked = everyui.file.PickedFile("me.png", 3L, "image/png", everyui.async.Task.pure(Array[Byte](1, 0, -1)), None)
      val part = await[FormPart](done => HttpFiles.part("image", picked).map(done).start())
      part match {
        case FormPart.FilePart(name, fileName, bytes, contentType) =>
          (name, fileName, bytes.toList, contentType) shouldBe ("image", "me.png", List[Byte](1, 0, -1), "image/png")
        case other => fail(other.toString)
      }
      val r = http(HttpCall.multipart(base + "/upload", List(FormPart.text("auth", "t"), part)))
      r.body shouldBe "{\"parts\":2}"
      lastUpload("image")._2.toList shouldBe List[Byte](1, 0, -1)
      lastUpload("image")._1("content-type") shouldBe "image/png"
    }
  }

}
