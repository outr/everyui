package everyui.sbt

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.aayushatharva.brotli4j.decoder.Decoder
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, File}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/** A bundle's text compressed beside itself (WebBundle.precompress), Brotli and gzip, and a sealed bundle's too. */
class PrecompressSpec extends AnyWordSpec with Matchers {
  Brotli4jLoader.ensureAvailability()
  private def bytes(file: File): Array[Byte] = Files.readAllBytes(file.toPath)
  private def put(out: File, path: String, text: String): Unit = {
    val f = new File(out, path)
    f.getParentFile.mkdirs()
    Files.write(f.toPath, text.getBytes(UTF_8))
  }
  private val script = (1 to 400).map(i => s"function f$i(a){return a+$i;}").mkString("\n")

  "WebBundle.precompress" should {
    lazy val out = {
      val o = Files.createTempDirectory("precompress").toFile
      put(o, "main.js", script)
      put(o, "l10n/app_en.arb", (1 to 100).map(i => s"\"k$i\": \"words $i\"").mkString("{", ",", "}"))
      put(o, "images/logo.png", script)
      put(o, "tiny.json", "{}")
      WebBundle.precompress(o)
      o
    }

    "write .br and .gz beside the text, each the file's bytes once decoded" in {
      Seq("main.js", "l10n/app_en.arb").foreach { path =>
        val original = bytes(new File(out, path))
        Decoder.decompress(bytes(new File(out, path + ".br"))).getDecompressedData.toSeq shouldBe original.toSeq
        new GZIPInputStream(new ByteArrayInputStream(bytes(new File(out, path + ".gz")))).readAllBytes().toSeq shouldBe original.toSeq
        bytes(new File(out, path + ".br")).length should be < bytes(new File(out, path + ".gz")).length
      }
    }

    "leave a picture as it is, and a file compression would not make smaller" in {
      new File(out, "images/logo.png.br").exists shouldBe false
      new File(out, "tiny.json.br").exists shouldBe false
      new File(out, "tiny.json.gz").exists shouldBe false
    }
  }

  "WebBundle.seal" should {
    "compress the sealed bundle, its page and hashed scripts too, unless told not to" in {
      def bundle(): File = {
        val o = Files.createTempDirectory("seal").toFile
        put(o, "index.html", "<html><head></head><body>" + "<p>words</p>" * 50 + "<script src=\"main.js\"></script></body></html>")
        put(o, "main.js", script)
        put(o, "fonts/a.ttf", script)
        o
      }
      val closed = bundle()
      WebBundle.seal(closed)
      val main = closed.list().find(n => n.startsWith("main.") && n.endsWith(".js")).get
      new File(closed, main + ".br").exists shouldBe true
      new File(closed, "index.html.br").exists shouldBe true
      new File(closed, "a").listFiles().head.listFiles().head.list() should contain allOf ("a.ttf", "a.ttf.br", "a.ttf.gz")
      val plain = bundle()
      WebBundle.seal(plain, compress = false)
      plain.list().filter(_.endsWith(".br")) shouldBe empty
    }
  }
}
