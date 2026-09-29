package everyui.network

import fabric.rw.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A multipart/form-data body as a browser writes one: the parts under the boundary with their dispositions and types,
  * names escaped as the HTML standard has it, UTF-8 names and values, and a file's bytes exactly as they were. */
class MultipartSpec extends AnyWordSpec with Matchers {
  private def text(bytes: Array[Byte]): String = new String(bytes, "UTF-8")

  "Multipart.boundary" should {
    "be a fixed start and 24 letters and digits, fresh each time" in {
      val a = Multipart.boundary()
      val b = Multipart.boundary()
      a should fullyMatch regex "----EveryUIFormBoundary[A-Za-z0-9]{24}"
      a should not be b
    }
  }

  "Multipart.encode" should {
    "write each part under the boundary, with CRLF lines and a closing boundary" in {
      val body = Multipart.encode(List(
        FormPart.text("title", "Hello"),
        FormPart.json("meta", Map("a" -> 1)),
        FormPart.file("file", "a.txt", "one\ntwo".getBytes("UTF-8"), "text/plain")), "XYZ")
      text(body) shouldBe
        "--XYZ\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nHello\r\n" +
        "--XYZ\r\nContent-Disposition: form-data; name=\"meta\"\r\n\r\n{\"a\":1}\r\n" +
        "--XYZ\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\nContent-Type: text/plain\r\n\r\none\ntwo\r\n" +
        "--XYZ--\r\n"
    }

    "write an empty form as the closing boundary alone" in {
      text(Multipart.encode(Nil, "B")) shouldBe "--B--\r\n"
    }

    "escape quotes, CR and LF in names and file names, and keep the rest as UTF-8" in {
      Multipart.escape("a\"b\r\nc") shouldBe "a%22b%0D%0Ac"
      val body = text(Multipart.encode(List(FormPart.file("f\"1", "pic \"1\"\r\né.png", Array[Byte](1), "image/png")), "B"))
      body should include ("name=\"f%221\"; filename=\"pic %221%22%0D%0Aé.png\"\r\n")
      Multipart.encode(List(FormPart.text("é", "ü")), "B").toList.containsSlice("é".getBytes("UTF-8").toList) shouldBe true
    }

    "keep a file's bytes exactly, every byte value among them" in {
      val bytes = Array.tabulate[Byte](512)(i => (i % 256).toByte)
      val body = Multipart.encode(List(FormPart.text("x", "y"), FormPart.file("file", "all.bin", bytes)), "B")
      val head = "--B\r\nContent-Disposition: form-data; name=\"x\"\r\n\r\ny\r\n" +
        "--B\r\nContent-Disposition: form-data; name=\"file\"; filename=\"all.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n"
      val tail = "\r\n--B--\r\n"
      body.length shouldBe head.length + 512 + tail.length
      body.slice(head.length, head.length + 512).toList shouldBe bytes.toList
      text(body.slice(0, head.length)) shouldBe head
      text(body.slice(head.length + 512, body.length)) shouldBe tail
    }
  }

  "FormPart.json" should {
    "be a field of compact JSON" in {
      FormPart.json("n", 42) shouldBe FormPart.FieldPart("n", "42")
      FormPart.json("m", Map("a" -> "b\nc")) shouldBe FormPart.FieldPart("m", "{\"a\":\"b\\nc\"}")
    }
  }

  "HttpCall.multipart" should {
    "POST the parts, with the headers given" in {
      val call = HttpCall.multipart("/upload", List(FormPart.text("a", "b")), headers = List("X-Auth" -> "t"))
      call.method shouldBe "POST"
      call.headers shouldBe List("X-Auth" -> "t")
      call.body shouldBe None
      call.parts shouldBe List(FormPart.FieldPart("a", "b"))
    }
  }
}
