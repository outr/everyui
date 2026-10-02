package everyui.sbt

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.aayushatharva.brotli4j.decoder.Decoder
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File

/** A face as WOFF2 (Woff2): its header, its table directory and its tables, compressed with Brotli, read back here as a
  * browser reads them (the null transform for each). */
class Woff2Spec extends AnyWordSpec with Matchers {
  import Sfnt.*

  private val fonts = Seq("assets/fonts", "../assets/fonts").map(new File(_)).find(_.isDirectory).get

  // the tables of a WOFF2 whose tables are all null-transformed, checking its header against them
  private def read(woff: Array[Byte]): Map[String, Array[Byte]] = {
    Brotli4jLoader.ensureAvailability()
    u32(woff, 0) shouldBe 0x774F4632L
    u32(woff, 8) shouldBe woff.length.toLong
    woff.length % 4 shouldBe 0
    val count = u16(woff, 12)
    var p = 48
    def base128(): Long = {
      var v = 0L
      var b = 0x80
      while ((b & 0x80) != 0) { b = woff(p) & 0xFF; p += 1; v = (v << 7) | (b & 0x7F) }
      v
    }
    val entries = (0 until count).map { _ =>
      val flags = woff(p) & 0xFF
      p += 1
      val tag = if ((flags & 63) == 63) { p += 4; new String(woff, p - 4, 4, "ISO-8859-1") } else Woff2.known(flags & 63)
      val version = flags >> 6
      if (tag == "glyf" || tag == "loca") version shouldBe 3 else version shouldBe 0
      tag -> base128().toInt
    }
    val compressed = u32(woff, 20).toInt
    val data = Decoder.decompress(java.util.Arrays.copyOfRange(woff, p, p + compressed)).getDecompressedData
    val starts = entries.scanLeft(0)(_ + _._2)
    data.length shouldBe starts.last
    u32(woff, 16) shouldBe (12 + 16 * count + entries.map(e => (e._2 + 3) / 4 * 4).sum).toLong
    entries.zip(starts).map { case ((tag, length), at) => tag -> java.util.Arrays.copyOfRange(data, at, at + length) }.toMap
  }

  "Woff2" should {
    "hold every table of the face as it is" in {
      Seq("OpenSans-Regular.ttf", "RobotoMono-Regular.ttf").foreach { name =>
        val face = Sfnt.read(new File(fonts, name))
        val tables = read(Woff2(face))
        tables.keySet shouldBe face.tables.keySet
        face.tables.foreach((tag, t) => tables(tag).toSeq shouldBe t.toSeq)
      }
    }

    "name a table the directory has no number for by its tag" in {
      val face = Sfnt.read(new File(fonts, "OpenSans-Regular.ttf"))
      val tables = read(Woff2(Sfnt(face.tables + ("Zzzz" -> Array[Byte](1, 2, 3)))))
      tables("Zzzz").toSeq shouldBe Seq[Byte](1, 2, 3)
    }

    "be smaller than the face" in {
      val file = new File(fonts, "RobotoMono-Regular.ttf")
      Woff2(Sfnt.read(file)).length.toLong should be < file.length * 6 / 10
    }
  }
}
