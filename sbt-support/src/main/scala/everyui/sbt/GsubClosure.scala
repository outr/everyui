package everyui.sbt

import scala.collection.mutable

/** The glyphs a face's glyph substitutions (GSUB) can turn a set of glyphs into: a ligature whose parts are all there,
  * an alternate, a contextual form. FontSubset keeps their outlines, so a subset draws "fi" as the face's ligature
  * rather than as nothing. Only the features a browser applies by itself are followed: those CSS turns on only when
  * asked (small capitals, old-style or tabular figures, stylistic sets, `aalt`'s every alternate) never are, as
  * EveryUI asks for none. */
object GsubClosure {
  import Sfnt.*

  /** The features followed: every one but these. */
  val optional: Set[String] = Set("aalt", "smcp", "c2sc", "pcap", "c2pc", "unic", "titl", "onum", "lnum", "pnum", "tnum",
    "frac", "afrc", "dnom", "numr", "ordn", "sinf", "subs", "sups", "zero", "salt", "swsh", "cswh", "hist", "hlig", "dlig",
    "case", "cpsp", "jalt", "nalt", "ornm", "hwid", "fwid", "pwid", "qwid", "twid", "palt", "halt", "ruby", "trad",
    "smpl", "expt", "hojo", "nlck", "jp78", "jp83", "jp90", "jp04", "vert", "vrt2", "vkna", "vhal", "vpal", "pkna",
    "ital", "mgrk", "rand") ++ (1 to 20).map(i => f"ss$i%02d") ++ (1 to 99).map(i => f"cv$i%02d")

  /** `glyphs` and every glyph the face's substitutions make of them, with the components of a composite outline. */
  def apply(font: Sfnt, glyphs: Set[Int]): Set[Int] = {
    val gsub = font.tables.get("GSUB")
    val lookups = gsub.map(t => followed(t)).getOrElse(Nil)
    val found = mutable.Set.from(glyphs)
    var changed = true
    while (changed) {
      val before = found.size
      lookups.foreach { case (t, at) => substitute(gsub.get, t, at, found) }
      found.toSeq.foreach(g => components(font, g, found))
      changed = found.size != before
    }
    found.toSet
  }

  /** A composite outline's components, and theirs, into `found`. */
  def components(font: Sfnt, g: Int, found: mutable.Set[Int]): Unit = if (g < font.numGlyphs) {
    val outline = font.glyph(g)
    if (outline.nonEmpty && s16(outline, 0) < 0) {
      var p = 10
      var flags = 0x20
      while ((flags & 0x20) != 0) {
        flags = u16(outline, p)
        val c = u16(outline, p + 2)
        if (found.add(c)) components(font, c, found)
        p += 4 + (if ((flags & 0x1) != 0) 4 else 2) +
          (if ((flags & 0x8) != 0) 2 else if ((flags & 0x40) != 0) 4 else if ((flags & 0x80) != 0) 8 else 0)
      }
    }
  }

  // The subtables (their type, as an extension unwraps it, and offset in the table) of the lookups the followed features
  // name, and of the lookups a contextual one among them applies.
  private def followed(t: Array[Byte]): Seq[(Int, Int)] = {
    val features = u16(t, 6)
    val list = u16(t, 8)
    val wanted = mutable.LinkedHashSet[Int]()
    (0 until u16(t, features)).foreach { i =>
      val r = features + 2 + 6 * i
      val tag = new String(t, r, 4, "ISO-8859-1")
      if (!optional(tag)) {
        val f = features + u16(t, r + 4)
        (0 until u16(t, f + 2)).foreach(k => wanted += u16(t, f + 4 + 2 * k))
      }
    }
    val count = u16(t, list)
    def subtables(index: Int): Seq[(Int, Int)] = if (index >= count) Nil else {
      val l = list + u16(t, list + 2 + 2 * index)
      (0 until u16(t, l + 4)).map { k =>
        val at = l + u16(t, l + 6 + 2 * k)
        if (u16(t, l) == 7) (u16(t, at + 2), at + u32(t, at + 4).toInt) else (u16(t, l), at)
      }
    }
    val queue = mutable.Queue.from(wanted)
    val out = Seq.newBuilder[(Int, Int)]
    while (queue.nonEmpty) {
      subtables(queue.dequeue()).foreach { s =>
        out += s
        nested(t, s._1, s._2).foreach(n => if (wanted.add(n)) queue.enqueue(n))
      }
    }
    out.result()
  }

  // The lookups a contextual (5) or chained contextual (6) subtable applies.
  private def nested(t: Array[Byte], kind: Int, at: Int): Seq[Int] = {
    def records(p: Int, n: Int): Seq[Int] = (0 until n).map(i => u16(t, p + 4 * i + 2))
    def sets(first: Int, rule: Int => Seq[Int]): Seq[Int] =
      (0 until u16(t, at + first)).flatMap { i =>
        val off = u16(t, at + first + 2 + 2 * i)
        if (off == 0) Nil else {
          val set = at + off
          (0 until u16(t, set)).flatMap(k => rule(set + u16(t, set + 2 + 2 * k)))
        }
      }
    (kind, u16(t, at)) match {
      case (5, 1 | 2) =>
        sets(if (u16(t, at) == 1) 4 else 6, r => records(r + 4 + 2 * (u16(t, r) - 1), u16(t, r + 2)))
      case (5, 3) => records(at + 6 + 2 * u16(t, at + 2), u16(t, at + 4))
      case (6, 1 | 2) =>
        sets(if (u16(t, at) == 1) 4 else 10, { r =>
          val input = r + 2 + 2 * u16(t, r)
          val ahead = input + 2 + 2 * (u16(t, input) - 1)
          val subst = ahead + 2 + 2 * u16(t, ahead)
          records(subst + 2, u16(t, subst))
        })
      case (6, 3) =>
        val input = at + 4 + 2 * u16(t, at + 2)
        val ahead = input + 2 + 2 * u16(t, input)
        val subst = ahead + 2 + 2 * u16(t, ahead)
        records(subst + 2, u16(t, subst))
      case _ => Nil
    }
  }

  // The glyphs a subtable makes of those in `found`, into it.
  private def substitute(t: Array[Byte], kind: Int, at: Int, found: mutable.Set[Int]): Unit = {
    def covered(offset: Int): Seq[(Int, Int)] = coverage(t, at + offset).filter(c => found(c._1))
    def array(p: Int): Seq[Int] = (0 until u16(t, p)).map(i => u16(t, p + 2 + 2 * i))
    (kind, u16(t, at)) match {
      case (1, 1) => covered(u16(t, at + 2)).foreach(c => found += (c._1 + s16(t, at + 4)) & 0xFFFF)
      case (1, 2) => covered(u16(t, at + 2)).foreach(c => found += u16(t, at + 6 + 2 * c._2))
      case (2 | 3, 1) => covered(u16(t, at + 2)).foreach(c => found ++= array(at + u16(t, at + 6 + 2 * c._2)))
      case (4, 1) => covered(u16(t, at + 2)).foreach { c =>
        val set = at + u16(t, at + 6 + 2 * c._2)
        (0 until u16(t, set)).foreach { k =>
          val lig = set + u16(t, set + 2 + 2 * k)
          if ((1 until u16(t, lig + 2)).forall(i => found(u16(t, lig + 2 + 2 * i)))) found += u16(t, lig)
        }
      }
      case (8, 1) =>
        val ahead = at + 6 + 2 * u16(t, at + 4)
        val subst = ahead + 2 + 2 * u16(t, ahead)
        covered(u16(t, at + 2)).foreach(c => found += u16(t, subst + 2 + 2 * c._2))
      case _ => ()
    }
  }

  /** A coverage table's glyphs, each with its index in it. */
  def coverage(t: Array[Byte], at: Int): Seq[(Int, Int)] = u16(t, at) match {
    case 1 => (0 until u16(t, at + 2)).map(i => u16(t, at + 4 + 2 * i) -> i)
    case 2 => (0 until u16(t, at + 2)).flatMap { i =>
      val r = at + 4 + 6 * i
      (u16(t, r) to u16(t, r + 2)).map(g => g -> (u16(t, r + 4) + g - u16(t, r)))
    }
    case _ => Nil
  }
}
