package everyui.sbt

import scala.collection.mutable

/** A face's character map: read from its best Unicode subtable, written as a BMP's (format 4) and a full one (12). */
object Cmap {
  import Sfnt.*

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
      case None => sys.error("the face has no Unicode character map")
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
    val f4 = new SfntOut
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
    val f12 = new SfntOut
    f12.u16(12); f12.u16(0); f12.u32(16L + 12 * full.size); f12.u32(0); f12.u32(full.size.toLong)
    full.foreach { (s, e, g) => f12.u32(s.toLong); f12.u32(e.toLong); f12.u32(g.toLong) }
    val variations = (0 until u16(old, 2)).map(i => u32(old, 8 + 8 * i).toInt).find(o => u16(old, o) == 14)
      .map(o => java.util.Arrays.copyOfRange(old, o, o + u32(old, o + 2).toInt))
    val bodies = Seq(f4.bytes, f12.bytes) ++ variations
    val records = Seq((0, 3, 0), (0, 4, 1)) ++ variations.map(_ => (0, 5, 2)) ++ Seq((3, 1, 0), (3, 10, 1))
    val out = new SfntOut
    out.u16(0); out.u16(records.size)
    val starts = bodies.scanLeft(4 + 8 * records.size)(_ + _.length)
    records.foreach { (p, e, b) => out.u16(p); out.u16(e); out.u32(starts(b).toLong) }
    bodies.foreach(out.raw)
    out.bytes
  }
}
