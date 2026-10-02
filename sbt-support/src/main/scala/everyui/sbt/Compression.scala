package everyui.sbt

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.aayushatharva.brotli4j.encoder.Encoder

import java.io.ByteArrayOutputStream
import java.util.zip.{Deflater, GZIPOutputStream}

/** Brotli and gzip at their best, for what is compressed once at build time and served many times: a bundle's text
  * (WebBundle.precompress) and WOFF2's tables. Brotli is brotli4j (Google's encoder, natively). */
object Compression {
  private lazy val loaded: Unit = Brotli4jLoader.ensureAvailability()

  /** Quality 11 with the largest window (16 MB), in its mode for fonts where `font`. */
  def brotli(bytes: Array[Byte], font: Boolean = false): Array[Byte] = {
    loaded
    val parameters = new Encoder.Parameters().setQuality(11).setWindow(24)
      .setMode(if (font) Encoder.Mode.FONT else Encoder.Mode.GENERIC)
    Encoder.compress(bytes, parameters)
  }

  /** Level 9. */
  def gzip(bytes: Array[Byte]): Array[Byte] = {
    val buffer = new ByteArrayOutputStream()
    val out = new GZIPOutputStream(buffer) {
      `def`.setLevel(Deflater.BEST_COMPRESSION)
    }
    out.write(bytes)
    out.close()
    buffer.toByteArray
  }
}
