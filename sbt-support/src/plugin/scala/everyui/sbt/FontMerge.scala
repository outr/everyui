package everyui.sbt

import java.io.{ByteArrayOutputStream, DataOutputStream, File}
import java.nio.file.Files
import scala.collection.mutable

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
    val base = Font(Files.readAllBytes(face.toPath))
    val from = Font(Files.readAllBytes(donor.toPath))
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
    val loca = new Out
    glyphs.foreach { g =>
      loca.u32(glyf.size.toLong)
      glyf.write(g)
      while (glyf.size % 4 != 0) glyf.write(0)
    }
    loca.u32(glyf.size.toLong)

    val hmtx = new Out
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
    Files.write(out.toPath, Font.write(tables))
    added
  }

  /** The characters of a TrueType or OpenType face, by the glyph each maps to. */
  def characters(face: File): Map[Int, Int] = Font(Files.readAllBytes(face.toPath)).characters

  private final case class Font(bytes: Array[Byte]) {
    val tables: Map[String, Array[Byte]] = (0 until u16(bytes, 4)).map { i =>
      val r = 12 + 16 * i
      val tag = new String(bytes, r, 4, "ISO-8859-1")
      val offset = u32(bytes, r + 8).toInt
      tag -> java.util.Arrays.copyOfRange(bytes, offset, offset + u32(bytes, r + 12).toInt)
    }.toMap
    def table(tag: String): Array[Byte] = tables.getOrElse(tag, sys.error(s"FontMerge: the face has no $tag table"))
    val unitsPerEm: Int = u16(table("head"), 18)
    val numGlyphs: Int = u16(table("maxp"), 4)
    private val longLoca = s16(table("head"), 50) == 1
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
    def characters: Map[Int, Int] = Cmap.read(table("cmap"))
  }

  private object Font {
    def write(tables: Map[String, Array[Byte]]): Array[Byte] = {
      val tags = tables.keys.toSeq.sorted
      val n = tags.size
      val out = new Out
      val power = Integer.highestOneBit(n)
      out.u32(0x00010000L); out.u16(n); out.u16(16 * power); out.u16(Integer.numberOfTrailingZeros(power)); out.u16(16 * (n - power))
      var offset = 12 + 16 * n
      tags.foreach { tag =>
        val t = tables(tag)
        out.raw(tag.getBytes("ISO-8859-1")); out.u32(checksum(t)); out.u32(offset.toLong); out.u32(t.length.toLong)
        offset += (t.length + 3) / 4 * 4
      }
      tags.foreach { tag => out.raw(tables(tag)); out.pad() }
      val font = out.bytes
      // head's checkSumAdjustment makes the whole file's sum 0xB1B0AFBA
      val headAt = 12 + 16 * n + tags.takeWhile(_ != "head").map(t => (tables(t).length + 3) / 4 * 4).sum
      put32(font, headAt + 8, (0xB1B0AFBAL - checksum(font)) & 0xFFFFFFFFL)
      font
    }
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
  }

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
        val out = new Out
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
      val out = new Out
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

  /** The character map: read from the face's best subtable, written as a BMP's (format 4) and a full one (12). */
  private object Cmap {
    def read(cmap: Array[Byte]): Map[Int, Int] = {
      val subtables = (0 until u16(cmap, 2)).map { i =>
        val offset = u32(cmap, 8 + 8 * i).toInt
        (u16(cmap, 4 + 8 * i), u16(cmap, 6 + 8 * i), offset, u16(cmap, offset))
      }
      val unicode = subtables.filter((p, e, _, _) => p == 0 || (p == 3 && (e == 1 || e == 10 || e == 0)))
      unicode.find(_._4 == 12).orElse(unicode.find(_._4 == 4)) match {
        case Some((_, _, offset, 12)) =>
          (0 until u32(cmap, offset + 12).toInt).flatMap { i =>
            val r = offset + 16 + 12 * i
            val start = u32(cmap, r).toInt
            val glyph = u32(cmap, r + 8).toInt
            (start to u32(cmap, r + 4).toInt).map(c => c -> (glyph + c - start))
          }.toMap
        case Some((_, _, offset, _)) =>
          val segments = u16(cmap, offset + 6) / 2
          val ends = offset + 14
          val starts = ends + 2 * segments + 2
          val deltas = starts + 2 * segments
          val ranges = deltas + 2 * segments
          (0 until segments).flatMap { s =>
            val start = u16(cmap, starts + 2 * s)
            val delta = s16(cmap, deltas + 2 * s)
            val range = u16(cmap, ranges + 2 * s)
            (start to u16(cmap, ends + 2 * s)).filter(_ != 0xFFFF).map { c =>
              val g =
                if (range == 0) (c + delta) & 0xFFFF
                else {
                  val at = u16(cmap, ranges + 2 * s + range + 2 * (c - start))
                  if (at == 0) 0 else (at + delta) & 0xFFFF
                }
              c -> g
            }
          }.filter(_._2 != 0).toMap
        case None => sys.error("FontMerge: the face has no Unicode character map")
      }
    }

    /** `map` as a cmap, with the face's variation sequences (format 14, in `old`) kept as they are. */
    def write(map: Map[Int, Int], old: Array[Byte]): Array[Byte] = {
      val sorted = map.toSeq.sortBy(_._1)
      // runs of consecutive characters on consecutive glyphs
      def runs(entries: Seq[(Int, Int)]): Seq[(Int, Int, Int)] = {
        val out = mutable.ArrayBuffer[(Int, Int, Int)]()
        entries.foreach { case (c, g) =>
          out.lastOption match {
            case Some((s, e, sg)) if c == e + 1 && g == sg + (c - s) => out(out.size - 1) = (s, c, sg)
            case _ => out += ((c, c, g))
          }
        }
        out.toSeq
      }
      val bmp = runs(sorted.filter(_._1 < 0xFFFF)) :+ ((0xFFFF, 0xFFFF, 0))
      val f4 = new Out
      val n = bmp.size
      val power = Integer.highestOneBit(n)
      f4.u16(4); f4.u16(16 + 8 * n); f4.u16(0)
      f4.u16(2 * n); f4.u16(2 * power); f4.u16(Integer.numberOfTrailingZeros(power)); f4.u16(2 * (n - power))
      bmp.foreach((_, e, _) => f4.u16(e))
      f4.u16(0)
      bmp.foreach((s, _, _) => f4.u16(s))
      bmp.foreach((s, _, g) => f4.u16(if (s == 0xFFFF) 1 else (g - s) & 0xFFFF))
      bmp.foreach(_ => f4.u16(0))
      val full = runs(sorted)
      val f12 = new Out
      f12.u16(12); f12.u16(0); f12.u32(16L + 12 * full.size); f12.u32(0); f12.u32(full.size.toLong)
      full.foreach { (s, e, g) => f12.u32(s.toLong); f12.u32(e.toLong); f12.u32(g.toLong) }
      val variations = (0 until u16(old, 2)).map(i => u32(old, 8 + 8 * i).toInt).find(o => u16(old, o) == 14)
        .map(o => java.util.Arrays.copyOfRange(old, o, o + u32(old, o + 2).toInt))
      val bodies = Seq(f4.bytes, f12.bytes) ++ variations
      val records = Seq((0, 3, 0), (0, 4, 1)) ++ variations.map(_ => (0, 5, 2)) ++ Seq((3, 1, 0), (3, 10, 1))
      val out = new Out
      out.u16(0); out.u16(records.size)
      val starts = bodies.scanLeft(4 + 8 * records.size)(_ + _.length)
      records.foreach { (p, e, b) => out.u16(p); out.u16(e); out.u32(starts(b).toLong) }
      bodies.foreach(out.raw)
      out.bytes
    }
  }

  private final class Out {
    private val buffer = new ByteArrayOutputStream()
    private val data = new DataOutputStream(buffer)
    def u8(v: Int): Unit = data.writeByte(v)
    def u16(v: Int): Unit = data.writeShort(v)
    def s16(v: Int): Unit = data.writeShort(v)
    def u32(v: Long): Unit = data.writeInt(v.toInt)
    def raw(b: Array[Byte]): Unit = data.write(b)
    def pad(): Unit = while (buffer.size % 4 != 0) data.writeByte(0)
    def bytes: Array[Byte] = { data.flush(); buffer.toByteArray }
  }

  private def u16(b: Array[Byte], at: Int): Int = ((b(at) & 0xFF) << 8) | (b(at + 1) & 0xFF)
  private def s16(b: Array[Byte], at: Int): Int = u16(b, at).toShort.toInt
  private def u32(b: Array[Byte], at: Int): Long = (u16(b, at).toLong << 16) | u16(b, at + 2)
  private def put16(b: Array[Byte], at: Int, v: Int): Unit = { b(at) = (v >> 8).toByte; b(at + 1) = v.toByte }
  private def put32(b: Array[Byte], at: Int, v: Long): Unit = { put16(b, at, (v >> 16).toInt); put16(b, at + 2, v.toInt) }
}
