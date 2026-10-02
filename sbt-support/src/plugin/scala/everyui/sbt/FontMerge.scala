package everyui.sbt

import java.io.{ByteArrayOutputStream, File}
import java.nio.file.Files
import scala.collection.mutable

import Sfnt.*

/** Glyphs of one TrueType face put into another, for the characters it has none for: how a Roku's fallback face for a
  * script gets the ASCII and punctuation its text mixes in. A Roku Label draws in one face, with no fallback of its
  * own, so an Arabic word ending in "…" drew a box where Noto Sans Arabic has no glyph.
  *
  * The donor's glyphs are scaled to the face's units per em, with their hinting left out (it runs against the donor's
  * own programs), and a composite glyph brings its components. The face keeps everything of its own; its glyph names
  * (`post`) are dropped, and the tables that count its glyphs (`hdmx`, `LTSH`, `VDMX`) with them. Both must be static
  * TrueType faces (glyf outlines).
  *
  * {{{
  *   FontMerge.add(file("NotoSansArabic-Regular.ttf"), file("NotoSans-Regular.ttf"), FontMerge.latin, file("out.ttf"))
  * }}}
  */
object FontMerge {
  /** ASCII, Latin-1's punctuation and letters, the general punctuation (spaces, dashes, quotes, the ellipsis, per mille,
    * guillemets: what a Roku's runtime draws in the face of the word it is in, uiScriptOf), the euro, the trade mark
    * and the minus. */
  val latin: Seq[Int] = (0x20 to 0x7E) ++ (0xA0 to 0xFF) ++ (0x2000 to 0x206F) ++ Seq(0x20AC, 0x2122, 0x2212)

  /** Write `face` to `out` with `donor`'s glyph for each of `characters` that `face` has none for and `donor` has. The
    * characters added, in order. */
  def add(face: File, donor: File, characters: Seq[Int], out: File): Seq[Int] = {
    val base = Sfnt(Files.readAllBytes(face.toPath))
    val from = Sfnt(Files.readAllBytes(donor.toPath))
    Seq(base, from).foreach { f =>
      if (f.tables.contains("fvar") || !f.tables.contains("glyf"))
        sys.error("FontMerge: a static TrueType face (glyf outlines, no variations) is needed")
    }
    val baseMap = base.characters
    val fromMap = from.characters
    val added = characters.distinct.filter(c => !baseMap.contains(c) && fromMap.contains(c))
    val scale = base.unitsPerEm.toDouble / from.unitsPerEm

    // the donor's glyphs wanted, with the components of each composite, numbered after the face's own
    val newId = mutable.LinkedHashMap[Int, Int]()
    def want(g: Int): Unit = if (!newId.contains(g)) {
      newId(g) = base.numGlyphs + newId.size
      Glyph.components(from.glyph(g)).foreach(want)
    }
    added.foreach(c => want(fromMap(c)))

    val (fromAdvance, fromBearing) = from.metrics
    val (advance, bearing) = base.metrics
    val glyphs = mutable.ArrayBuffer.from((0 until base.numGlyphs).map(base.glyph))
    val advances = mutable.ArrayBuffer.from(advance)
    val bearings = mutable.ArrayBuffer.from(bearing)
    var points = 0
    var contours = 0
    newId.keys.foreach { g =>
      val scaled = Glyph.scaled(from.glyph(g), scale, newId)
      glyphs += scaled
      advances += math.round(fromAdvance(g) * scale).toInt
      bearings += (if (scaled.isEmpty) math.round(fromBearing(g) * scale).toInt else s16(scaled, 2))
      if (scaled.nonEmpty && s16(scaled, 0) > 0) {
        contours = math.max(contours, s16(scaled, 0))
        points = math.max(points, u16(scaled, 10 + 2 * (s16(scaled, 0) - 1)) + 1)
      }
    }
    val count = glyphs.size

    // glyf and a long loca
    val glyf = new ByteArrayOutputStream()
    val loca = new SfntOut
    glyphs.foreach { g =>
      loca.u32(glyf.size.toLong)
      glyf.write(g)
      while (glyf.size % 4 != 0) glyf.write(0)
    }
    loca.u32(glyf.size.toLong)

    val hmtx = new SfntOut
    (0 until count).foreach { i => hmtx.u16(advances(i)); hmtx.s16(bearings(i)) }

    val cmap = Cmap.write(baseMap ++ added.map(c => c -> newId(fromMap(c))), base.table("cmap"))

    val head = base.table("head").clone()
    put32(head, 8, 0L)
    put16(head, 50, 1)
    val boxes = newId.keys.toSeq.map(g => glyphs(newId(g))).filter(_.nonEmpty)
    if (boxes.nonEmpty) {
      put16(head, 36, (s16(head, 36) +: boxes.map(s16(_, 2))).min)
      put16(head, 38, (s16(head, 38) +: boxes.map(s16(_, 4))).min)
      put16(head, 40, (s16(head, 40) +: boxes.map(s16(_, 6))).max)
      put16(head, 42, (s16(head, 42) +: boxes.map(s16(_, 8))).max)
    }

    val hhea = base.table("hhea").clone()
    put16(hhea, 10, advances.max)
    put16(hhea, 34, count)

    val maxp = base.table("maxp").clone()
    val fromMaxp = from.table("maxp")
    put16(maxp, 4, count)
    if (maxp.length >= 32 && fromMaxp.length >= 32) {
      put16(maxp, 6, math.max(u16(maxp, 6), points))
      put16(maxp, 8, math.max(u16(maxp, 8), contours))
      Seq(10, 12, 28, 30).foreach(o => put16(maxp, o, math.max(u16(maxp, o), u16(fromMaxp, o))))
    }

    // glyph names for the face's own glyphs only would not match the count: none at all (version 3)
    val post = base.table("post").take(32).clone()
    put32(post, 0, 0x00030000L)

    val all = (baseMap.keySet ++ added).toSeq
    val os2 = base.tables.get("OS/2").map { t =>
      val o = t.clone()
      put16(o, 64, math.min(all.min, 0xFFFF))
      put16(o, 66, math.min(all.max, 0xFFFF))
      o
    }

    val dropped = Set("hdmx", "LTSH", "VDMX", "DSIG", "vhea", "vmtx")
    val tables = base.tables.filterNot((tag, _) => dropped(tag)) ++
      Map("glyf" -> glyf.toByteArray, "loca" -> loca.bytes, "hmtx" -> hmtx.bytes, "cmap" -> cmap, "head" -> head,
        "hhea" -> hhea, "maxp" -> maxp, "post" -> post) ++ os2.map("OS/2" -> _)
    Files.write(out.toPath, Sfnt(tables).bytes)
    added
  }

  /** The characters of a TrueType or OpenType face, by the glyph each maps to. */
  def characters(face: File): Map[Int, Int] = Sfnt(Files.readAllBytes(face.toPath)).characters

  /** A glyf entry: its components, and it scaled with its glyph numbers changed. */
  private object Glyph {
    private val Words = 0x1
    private val XY = 0x2
    private val Scale = 0x8
    private val More = 0x20
    private val XYScale = 0x40
    private val TwoByTwo = 0x80
    private val Instructions = 0x100

    def components(g: Array[Byte]): Seq[Int] =
      if (g.isEmpty || s16(g, 0) >= 0) Nil
      else {
        val found = mutable.ArrayBuffer[Int]()
        var p = 10
        var flags = More
        while ((flags & More) != 0) {
          flags = u16(g, p)
          found += u16(g, p + 2)
          p += 4 + (if ((flags & Words) != 0) 4 else 2) + transformSize(flags)
        }
        found.toSeq
      }

    def scaled(g: Array[Byte], scale: Double, ids: collection.Map[Int, Int]): Array[Byte] = {
      def sc(v: Int): Int = math.round(v * scale).toInt
      if (g.isEmpty) g
      else if (s16(g, 0) >= 0) simple(g, sc)
      else {
        val out = new SfntOut
        out.s16(-1)
        (2 until 10 by 2).foreach(o => out.s16(sc(s16(g, o))))
        var p = 10
        var flags = More
        while ((flags & More) != 0) {
          flags = u16(g, p)
          val words = (flags & Words) != 0
          val (a, b) =
            if (words) (s16(g, p + 4), s16(g, p + 6))
            else if ((flags & XY) != 0) (g(p + 4).toInt, g(p + 5).toInt)
            else (g(p + 4) & 0xFF, g(p + 5) & 0xFF)
          out.u16((flags | Words) & ~Instructions)
          out.u16(ids(u16(g, p + 2)))
          if ((flags & XY) != 0) { out.s16(sc(a)); out.s16(sc(b)) } else { out.u16(a); out.u16(b) }
          p += 4 + (if (words) 4 else 2)
          out.raw(java.util.Arrays.copyOfRange(g, p, p + transformSize(flags)))
          p += transformSize(flags)
        }
        out.bytes
      }
    }

    private def transformSize(flags: Int): Int =
      if ((flags & Scale) != 0) 2 else if ((flags & XYScale) != 0) 4 else if ((flags & TwoByTwo) != 0) 8 else 0

    /** A simple glyph, its points scaled, without instructions, each coordinate a 16-bit delta. */
    private def simple(g: Array[Byte], sc: Int => Int): Array[Byte] = {
      val contours = s16(g, 0)
      val ends = (0 until contours).map(i => u16(g, 10 + 2 * i))
      val count = if (contours == 0) 0 else ends.last + 1
      var p = 12 + 2 * contours + u16(g, 10 + 2 * contours)
      val flags = new Array[Int](count)
      var i = 0
      while (i < count) {
        val f = g(p) & 0xFF
        p += 1
        flags(i) = f
        i += 1
        if ((f & 0x8) != 0) {
          val repeat = g(p) & 0xFF
          p += 1
          (0 until repeat).foreach { _ => flags(i) = f; i += 1 }
        }
      }
      def coordinates(short: Int, same: Int): Array[Int] = {
        val out = new Array[Int](count)
        var v = 0
        (0 until count).foreach { k =>
          val f = flags(k)
          if ((f & short) != 0) { val d = g(p) & 0xFF; p += 1; v += (if ((f & same) != 0) d else -d) }
          else if ((f & same) == 0) { v += s16(g, p); p += 2 }
          out(k) = v
        }
        out
      }
      val xs = coordinates(0x2, 0x10).map(sc)
      val ys = coordinates(0x4, 0x20).map(sc)
      val out = new SfntOut
      out.s16(contours)
      if (count > 0) { out.s16(xs.min); out.s16(ys.min); out.s16(xs.max); out.s16(ys.max) }
      else (2 until 10 by 2).foreach(o => out.s16(sc(s16(g, o))))
      ends.foreach(out.u16)
      out.u16(0)
      // on the curve, and overlapping, kept; every coordinate a 16-bit delta
      flags.foreach(f => out.u8(f & 0x41))
      Seq(xs, ys).foreach { vs =>
        var last = 0
        vs.foreach { v => out.s16(v - last); last = v }
      }
      out.bytes
    }
  }
}
