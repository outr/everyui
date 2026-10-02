package everyui.sbt

import java.nio.file.Files

/** A TrueType or OpenType face as its tables, by tag: what FontMerge and FontSubset read and write. A static TrueType
  * face (glyf outlines) for the glyphs. */
final case class Sfnt(tables: Map[String, Array[Byte]]) {
  import Sfnt.*

  def table(tag: String): Array[Byte] = tables.getOrElse(tag, sys.error(s"the face has no $tag table"))
  /** CFF outlines (OTTO) or TrueType ones. */
  def flavor: Long = if (tables.contains("CFF ") || tables.contains("CFF2")) 0x4F54544FL else 0x00010000L
  def unitsPerEm: Int = u16(table("head"), 18)
  def numGlyphs: Int = u16(table("maxp"), 4)
  private def longLoca: Boolean = s16(table("head"), 50) == 1
  private def offset(g: Int): Int = if (longLoca) u32(table("loca"), 4 * g).toInt else 2 * u16(table("loca"), 2 * g)
  def glyph(g: Int): Array[Byte] = java.util.Arrays.copyOfRange(table("glyf"), offset(g), offset(g + 1))
  /** Each glyph's advance and left side bearing. */
  def metrics: (IndexedSeq[Int], IndexedSeq[Int]) = {
    val hmtx = table("hmtx")
    val long = u16(table("hhea"), 34)
    val advance = (0 until numGlyphs).map(g => u16(hmtx, 4 * math.min(g, long - 1)))
    val bearing = (0 until numGlyphs).map(g => if (g < long) s16(hmtx, 4 * g + 2) else s16(hmtx, 4 * long + 2 * (g - long)))
    (advance, bearing)
  }
  /** Its characters, by the glyph each maps to. */
  def characters: Map[Int, Int] = Cmap.read(table("cmap"))

  /** The face as a file, its tables in tag order, each with its checksum, and head's checkSumAdjustment made. */
  def bytes: Array[Byte] = {
    val tags = tables.keys.toSeq.sorted
    val n = tags.size
    val out = new SfntOut
    val power = Integer.highestOneBit(n)
    out.u32(flavor); out.u16(n); out.u16(16 * power); out.u16(Integer.numberOfTrailingZeros(power)); out.u16(16 * (n - power))
    var at = 12 + 16 * n
    tags.foreach { tag =>
      val t = tables(tag)
      out.raw(tag.getBytes("ISO-8859-1")); out.u32(checksum(t)); out.u32(at.toLong); out.u32(t.length.toLong)
      at += (t.length + 3) / 4 * 4
    }
    tags.foreach { tag => out.raw(tables(tag)); out.pad() }
    val font = out.bytes
    // head's checkSumAdjustment makes the whole file's sum 0xB1B0AFBA
    if (tables.contains("head")) {
      val headAt = 12 + 16 * n + tags.takeWhile(_ != "head").map(t => (tables(t).length + 3) / 4 * 4).sum
      put32(font, headAt + 8, 0L)
      put32(font, headAt + 8, (0xB1B0AFBAL - checksum(font)) & 0xFFFFFFFFL)
    }
    font
  }
}

object Sfnt {
  def apply(bytes: Array[Byte]): Sfnt = {
    if (u32(bytes, 0) == 0x74746366L) sys.error("a font collection (.ttc) is not a face")
    Sfnt((0 until u16(bytes, 4)).map { i =>
      val r = 12 + 16 * i
      val tag = new String(bytes, r, 4, "ISO-8859-1")
      val offset = u32(bytes, r + 8).toInt
      tag -> java.util.Arrays.copyOfRange(bytes, offset, offset + u32(bytes, r + 12).toInt)
    }.toMap)
  }

  def read(file: java.io.File): Sfnt = apply(Files.readAllBytes(file.toPath))

  def checksum(t: Array[Byte]): Long = {
    var sum = 0L
    var i = 0
    while (i < t.length) {
      var word = 0L
      (0 until 4).foreach(k => word = (word << 8) | (if (i + k < t.length) t(i + k) & 0xFF else 0))
      sum = (sum + word) & 0xFFFFFFFFL
      i += 4
    }
    sum
  }

  def u16(b: Array[Byte], at: Int): Int = ((b(at) & 0xFF) << 8) | (b(at + 1) & 0xFF)
  def s16(b: Array[Byte], at: Int): Int = u16(b, at).toShort.toInt
  def u32(b: Array[Byte], at: Int): Long = (u16(b, at).toLong << 16) | u16(b, at + 2)
  def put16(b: Array[Byte], at: Int, v: Int): Unit = { b(at) = (v >> 8).toByte; b(at + 1) = v.toByte }
  def put32(b: Array[Byte], at: Int, v: Long): Unit = { put16(b, at, (v >> 16).toInt); put16(b, at + 2, v.toInt) }
}
