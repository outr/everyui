package everyui.network

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What a response says of itself: its headers whatever their case, its type, and the file name its
  * Content-Disposition gives (RFC 6266), quoted, unquoted or percent-encoded; and a file's name from its URL. */
class HttpResultSpec extends AnyWordSpec with Matchers {
  private def disposed(value: String): Option[String] = HttpResult(200, List("content-disposition" -> value), "").fileName

  "HttpResult" should {
    "find a header whatever its case" in {
      val r = HttpResult(200, List("Content-Type" -> "text/csv; charset=utf-8", "X-A" -> "1", "x-a" -> "2"), "")
      r.header("content-type") shouldBe Some("text/csv; charset=utf-8")
      r.header("X-a") shouldBe Some("1")
      r.header("Missing") shouldBe None
      r.contentType shouldBe Some("text/csv; charset=utf-8")
      r.mime shouldBe Some("text/csv")
      HttpResult(200, List("content-type" -> "Image/PNG"), "").mime shouldBe Some("image/png")
      HttpResult(200, Nil, "").mime shouldBe None
    }

    "read the file name from a Content-Disposition" in {
      disposed("attachment; filename=\"report.csv\"") shouldBe Some("report.csv")
      disposed("attachment; filename=report.csv") shouldBe Some("report.csv")
      disposed("attachment;filename=report.csv;size=10") shouldBe Some("report.csv")
      disposed("attachment; FileName=\"a b.zip\"") shouldBe Some("a b.zip")
      disposed("attachment; filename=\"say \\\"hi\\\".txt\"") shouldBe Some("say \"hi\".txt")
      disposed("attachment; filename=\"a;b.txt\"") shouldBe Some("a;b.txt")
      disposed("attachment") shouldBe None
      disposed("inline; filename=\"\"") shouldBe None
    }

    "prefer filename* (RFC 5987), in UTF-8 or ISO-8859-1" in {
      disposed("attachment; filename=\"resume.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf") shouldBe Some("résumé.pdf")
      disposed("attachment; filename*=utf-8'en'%E2%82%AC%20rates.csv") shouldBe Some("€ rates.csv")
      disposed("attachment; filename*=iso-8859-1''caf%E9.txt") shouldBe Some("café.txt")
      disposed("attachment; filename*=x-unknown''a.txt; filename=\"b.txt\"") shouldBe Some("b.txt")
    }

    "drop any path the server put before the name" in {
      disposed("attachment; filename=\"../../etc/passwd\"") shouldBe Some("passwd")
      disposed("attachment; filename=\"C:\\\\temp\\\\x.png\"") shouldBe Some("x.png")
    }
  }

  "HttpFiles.fromUrl" should {
    "name a file after the URL's last segment" in {
      HttpFiles.fromUrl("https://example.com/entity/download-export?x=1") shouldBe "download-export"
      HttpFiles.fromUrl("/files/report.csv#top") shouldBe "report.csv"
      HttpFiles.fromUrl("https://example.com") shouldBe "download"
      HttpFiles.fromUrl("https://example.com/dir/") shouldBe "download"
    }
  }
}
