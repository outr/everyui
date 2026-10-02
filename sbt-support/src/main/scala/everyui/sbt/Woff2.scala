package everyui.sbt

/** A face as WOFF2 (https://www.w3.org/TR/WOFF2/): its tables as they are (the null transform, glyf and loca's too,
  * which the format allows), compressed together with Brotli at its best, in its mode for fonts. */
object Woff2 {
  import Sfnt.*

  /** The tags the table directory names by number, in order. */
  val known: Seq[String] = Seq(
    "cmap", "head", "hhea", "hmtx", "maxp", "name", "OS/2", "post", "cvt ", "fpgm", "glyf", "loca", "prep", "CFF ", "VORG",
    "EBDT", "EBLC", "gasp", "hdmx", "kern", "LTSH", "PCLT", "VDMX", "vhea", "vmtx", "BASE", "GDEF", "GPOS", "GSUB", "EBSC",
    "JSTF", "MATH", "CBDT", "CBLC", "COLR", "CPAL", "SVG ", "sbix", "acnt", "avar", "bdat", "bloc", "bsln", "cvar", "fdsc",
    "feat", "fmtx", "fvar", "gvar", "hsty", "just", "lcar", "mort", "morx", "opbd", "prop", "trak", "Zapf", "Silf", "Glat",
    "Gloc", "Feat", "Sill")

  def apply(font: Sfnt): Array[Byte] = {
    val tags = font.tables.keys.toSeq.sorted
    val data = new SfntOut
    tags.foreach(t => data.raw(font.tables(t)))
    val compressed = Compression.brotli(data.bytes, font = true)

    val directory = new SfntOut
    tags.foreach { tag =>
      val index = known.indexOf(tag)
      // transform version 3 for glyf and loca, 0 for the rest: the null transform, so no transformed length
      val version = if (tag == "glyf" || tag == "loca") 3 << 6 else 0
      directory.u8(version | (if (index >= 0) index else 63))
      if (index < 0) directory.raw(tag.getBytes("ISO-8859-1"))
      base128(directory, font.tables(tag).length.toLong)
    }
    val dir = directory.bytes
    val sfntSize = 12 + 16 * tags.size + tags.map(t => (font.tables(t).length + 3) / 4 * 4).sum
    val length = (48 + dir.length + compressed.length + 3) / 4 * 4
    val out = new SfntOut
    out.u32(0x774F4632L)
    out.u32(font.flavor)
    out.u32(length.toLong)
    out.u16(tags.size)
    out.u16(0)
    out.u32(sfntSize.toLong)
    out.u32(compressed.length.toLong)
    out.u16(1); out.u16(0)
    (0 until 5).foreach(_ => out.u32(0L))
    out.raw(dir)
    out.raw(compressed)
    out.pad()
    out.bytes
  }

  // UIntBase128: seven bits a byte, the highest first, every byte but the last with its top bit set
  private def base128(out: SfntOut, value: Long): Unit = {
    val groups = Iterator.iterate(value)(_ >>> 7).takeWhile(_ > 0).map(v => (v & 0x7F).toInt).toSeq.reverse
    val bytes = if (groups.isEmpty) Seq(0) else groups
    bytes.zipWithIndex.foreach((b, i) => out.u8(if (i < bytes.size - 1) b | 0x80 else b))
  }
}
