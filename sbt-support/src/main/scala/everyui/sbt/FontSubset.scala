package everyui.sbt

import scala.collection.mutable

/** A TrueType face cut down to some of its characters, for the web (WebFaces). Two ways:
  *
  *  - `compact`: only those characters' glyphs, numbered afresh, and none of the tables that name glyphs by number
  *    (GSUB, GPOS, kern, …). For an icon face, drawn one character at a time (Material Icons, by codepoint).
  *  - `retained`: every glyph keeps its number and its metrics, and those the characters cannot reach (by the map, a
  *    substitution or a composite) are left without an outline. The layout tables stay as they are and still hold, so
  *    kerning, ligatures and marks are the whole face's: for words. */
object FontSubset {
  import Sfnt.*

  /** The tables `compact` keeps; it writes the glyphs, their metrics and the map itself. */
  private val compactTables = Set("head", "hhea", "maxp", "OS/2", "name", "cmap", "post", "glyf", "loca", "hmtx", "cvt ",
    "fpgm", "prep", "gasp")

  def compact(font: Sfnt, characters: Set[Int]): Sfnt = {
    outlines(font)
    val map = font.characters.filter(c => characters(c._1))
    val found = mutable.Set.from(map.values) += 0
    map.values.foreach(g => GsubClosure.components(font, g, found))
    val kept = found.toSeq.sorted
    val newId = kept.zipWithIndex.toMap
    val glyphs = kept.map { g =>
      val outline = font.glyph(g)
      if (outline.nonEmpty && s16(outline, 0) < 0) renumbered(outline, newId) else outline
    }
    val (advance, bearing) = font.metrics
    val hmtx = new SfntOut
    kept.foreach { g => hmtx.u16(advance(g)); hmtx.s16(bearing(g)) }
    val hhea = font.table("hhea").clone()
    put16(hhea, 34, kept.size)
    put16(hhea, 10, kept.map(advance).max)
    val maxp = font.table("maxp").clone()
    put16(maxp, 4, kept.size)
    val cmap = Cmap.write(map.map((c, g) => c -> newId(g)), Array[Byte](0, 0, 0, 0))
    val tables = font.tables.filter((tag, _) => compactTables(tag)) ++ glyf(font, glyphs) ++
      Map("hmtx" -> hmtx.bytes, "hhea" -> hhea, "maxp" -> maxp, "cmap" -> cmap) ++ common(font, map.keySet)
    Sfnt(tables)
  }

  def retained(font: Sfnt, characters: Set[Int]): Sfnt = {
    outlines(font)
    val map = font.characters.filter(c => characters(c._1))
    val kept = GsubClosure(font, map.values.toSet + 0)
    val glyphs = (0 until font.numGlyphs).map(g => if (kept(g)) font.glyph(g) else Array.emptyByteArray)
    // the metrics of a glyph left out are nothing too; the caches of every glyph's width at a size (hdmx, LTSH, VDMX)
    // and the signature go
    val (advance, bearing) = font.metrics
    val hmtx = new SfntOut
    (0 until font.numGlyphs).foreach { g =>
      hmtx.u16(if (kept(g)) advance(g) else 0)
      hmtx.s16(if (kept(g)) bearing(g) else 0)
    }
    val hhea = font.table("hhea").clone()
    put16(hhea, 34, font.numGlyphs)
    val tables = font.tables -- Seq("DSIG", "hdmx", "LTSH", "VDMX") ++ glyf(font, glyphs) ++
      Map("cmap" -> Cmap.write(map, font.table("cmap")), "hmtx" -> hmtx.bytes, "hhea" -> hhea) ++ common(font, map.keySet)
    Sfnt(tables)
  }

  private def outlines(font: Sfnt): Unit =
    if (!font.tables.contains("glyf")) sys.error("FontSubset: a TrueType face (glyf outlines) is needed")

  // glyf, loca, and head saying which loca (short where every offset fits)
  private def glyf(font: Sfnt, glyphs: Seq[Array[Byte]]): Map[String, Array[Byte]] = {
    val body = new SfntOut
    val offsets = glyphs.map { g =>
      val at = body.size
      body.raw(g)
      body.pad()
      at
    } :+ body.size
    val short = offsets.last <= 0x1FFFE
    val loca = new SfntOut
    offsets.foreach(o => if (short) loca.u16(o / 2) else loca.u32(o.toLong))
    val head = font.table("head").clone()
    put16(head, 50, if (short) 0 else 1)
    Map("glyf" -> body.bytes, "loca" -> loca.bytes, "head" -> head)
  }

  // what both ways change beside the glyphs: no glyph names (post version 3), and OS/2's first and last character
  private def common(font: Sfnt, characters: collection.Set[Int]): Map[String, Array[Byte]] = {
    val post = font.table("post").take(32).clone()
    put32(post, 0, 0x00030000L)
    val os2 = font.tables.get("OS/2").filter(_ => characters.nonEmpty).map { t =>
      val o = t.clone()
      put16(o, 64, math.min(characters.min, 0xFFFF))
      put16(o, 66, math.min(characters.max, 0xFFFF))
      o
    }
    Map("post" -> post) ++ os2.map("OS/2" -> _)
  }

  // a composite outline with its components' numbers changed
  private def renumbered(outline: Array[Byte], newId: Map[Int, Int]): Array[Byte] = {
    val out = outline.clone()
    var p = 10
    var flags = 0x20
    while ((flags & 0x20) != 0) {
      flags = u16(out, p)
      put16(out, p + 2, newId(u16(out, p + 2)))
      p += 4 + (if ((flags & 0x1) != 0) 4 else 2) +
        (if ((flags & 0x8) != 0) 2 else if ((flags & 0x40) != 0) 4 else if ((flags & 0x80) != 0) 8 else 0)
    }
    out
  }
}
