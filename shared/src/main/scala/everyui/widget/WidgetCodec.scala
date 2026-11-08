package everyui.widget

import everyui.font.MaterialIcon

/** A widget's tree and its saved setup as text, to keep on the device between draws: each widget's last good tree is
  * drawn at once as the launcher asks, before its content answers. Words are kept as their length and themselves
  * ("5:hello"), numbers end at ";", so any words go through whole; a text that is not one of these reads as None,
  * which draws as nothing kept. */
object WidgetCodec {
  private val version = "w1|"

  def encode(node: WidgetNode): String = {
    val out = new StringBuilder(version)
    write(node, out)
    out.toString
  }

  def decode(text: String): Option[WidgetNode] = read(text, at => node(text, at))

  def encode(setup: WidgetSetup): String = {
    val out = new StringBuilder(version)
    words(setup.kind, out)
    count(setup.choices.size, out)
    setup.choices.toList.sorted.foreach(c => words(c, out))
    out.append(if (setup.countsOnly) '1' else '0')
    out.toString
  }

  def decodeSetup(text: String): Option[WidgetSetup] = read(text, at => {
    val (kind, a) = wordsAt(text, at)
    val (n, b) = countAt(text, a)
    var next = b
    val choices = (0 until n).map { _ =>
      val (c, d) = wordsAt(text, next)
      next = d
      c
    }.toSet
    (WidgetSetup(kind, choices, flagAt(text, next)), next + 1)
  })

  /** `body` from just past the version, the whole of `text` read by it, or None. */
  private def read[T](text: String, body: Int => (T, Int)): Option[T] =
    if (text == null || !text.startsWith(version)) None
    else try {
      val (value, end) = body(version.length)
      if (end == text.length) Some(value) else None
    }
    catch { case _: Exception => None }

  private def write(node: WidgetNode, out: StringBuilder): Unit = node match {
    case WidgetNode.Text(w, style, colour, lines, label) =>
      out.append('t')
      words(w, out)
      count(style.ordinal, out)
      count(colour.ordinal, out)
      count(lines, out)
      words(label, out)
    case WidgetNode.Picture(source, size, label) =>
      out.append('p')
      source match {
        case WidgetPicture.Icon(icon) =>
          out.append('i')
          words(icon.name, out)
          words(icon.glyph, out)
        case WidgetPicture.Url(url) =>
          out.append('u')
          words(url, out)
        case WidgetPicture.Asset(path) =>
          out.append('a')
          words(path, out)
      }
      number(size, out)
      words(label, out)
    case WidgetNode.Row(parts, gap, tap) =>
      out.append('r')
      nodes(parts, out)
      number(gap, out)
      maybeTap(tap, out)
    case WidgetNode.Column(parts, gap, tap) =>
      out.append('c')
      nodes(parts, out)
      number(gap, out)
      maybeTap(tap, out)
    case WidgetNode.Button(label, icon, tap) =>
      out.append('b')
      words(label, out)
      icon match {
        case Some(i) =>
          out.append('1')
          words(i.name, out)
          words(i.glyph, out)
        case None => out.append('0')
      }
      tapped(tap, out)
    case WidgetNode.Items(rows, empty) =>
      out.append('l')
      nodes(rows, out)
      words(empty, out)
    case WidgetNode.Spacer => out.append('s')
  }

  private def nodes(parts: Vector[WidgetNode], out: StringBuilder): Unit = {
    count(parts.size, out)
    parts.foreach(p => write(p, out))
  }

  private def maybeTap(tap: Option[WidgetTap], out: StringBuilder): Unit = tap match {
    case Some(t) =>
      out.append('1')
      tapped(t, out)
    case None => out.append('0')
  }

  private def tapped(tap: WidgetTap, out: StringBuilder): Unit = tap match {
    case WidgetTap.Open(path) =>
      out.append('o')
      words(path, out)
    case WidgetTap.Action(name, args) =>
      out.append('a')
      words(name, out)
      count(args.size, out)
      args.toList.sortBy(_._1).foreach { (k, v) =>
        words(k, out)
        words(v, out)
      }
  }

  private def words(s: String, out: StringBuilder): Unit = out.append(s.length).append(':').append(s)
  private def count(n: Int, out: StringBuilder): Unit = out.append(n).append(';')
  private def number(d: Double, out: StringBuilder): Unit = out.append(d.toString).append(';')

  private def node(text: String, at: Int): (WidgetNode, Int) = text.charAt(at) match {
    case 't' =>
      val (w, a) = wordsAt(text, at + 1)
      val (style, b) = countAt(text, a)
      val (colour, c) = countAt(text, b)
      val (lines, d) = countAt(text, c)
      val (label, e) = wordsAt(text, d)
      (WidgetNode.Text(w, WidgetText.fromOrdinal(style), WidgetColour.fromOrdinal(colour), lines, label), e)
    case 'p' =>
      val (source, a) = text.charAt(at + 1) match {
        case 'i' =>
          val (name, x) = wordsAt(text, at + 2)
          val (glyph, y) = wordsAt(text, x)
          (WidgetPicture.Icon(MaterialIcon(name, glyph)), y)
        case 'u' =>
          val (url, x) = wordsAt(text, at + 2)
          (WidgetPicture.Url(url), x)
        case 'a' =>
          val (path, x) = wordsAt(text, at + 2)
          (WidgetPicture.Asset(path), x)
        case other => throw new IllegalArgumentException("picture " + other)
      }
      val (size, b) = numberAt(text, a)
      val (label, c) = wordsAt(text, b)
      (WidgetNode.Picture(source, size, label), c)
    case 'r' =>
      val (parts, a) = nodesAt(text, at + 1)
      val (gap, b) = numberAt(text, a)
      val (tap, c) = maybeTapAt(text, b)
      (WidgetNode.Row(parts, gap, tap), c)
    case 'c' =>
      val (parts, a) = nodesAt(text, at + 1)
      val (gap, b) = numberAt(text, a)
      val (tap, c) = maybeTapAt(text, b)
      (WidgetNode.Column(parts, gap, tap), c)
    case 'b' =>
      val (label, a) = wordsAt(text, at + 1)
      val (icon, b) =
        if (flagAt(text, a)) {
          val (name, x) = wordsAt(text, a + 1)
          val (glyph, y) = wordsAt(text, x)
          (Some(MaterialIcon(name, glyph)), y)
        }
        else (None, a + 1)
      val (tap, c) = tapAt(text, b)
      (WidgetNode.Button(label, icon, tap), c)
    case 'l' =>
      val (rows, a) = nodesAt(text, at + 1)
      val (empty, b) = wordsAt(text, a)
      (WidgetNode.Items(rows, empty), b)
    case 's' => (WidgetNode.Spacer, at + 1)
    case other => throw new IllegalArgumentException("node " + other)
  }

  private def nodesAt(text: String, at: Int): (Vector[WidgetNode], Int) = {
    val (n, a) = countAt(text, at)
    var next = a
    val parts = (0 until n).map { _ =>
      val (part, b) = node(text, next)
      next = b
      part
    }.toVector
    (parts, next)
  }

  private def maybeTapAt(text: String, at: Int): (Option[WidgetTap], Int) =
    if (flagAt(text, at)) {
      val (tap, a) = tapAt(text, at + 1)
      (Some(tap), a)
    }
    else (None, at + 1)

  private def tapAt(text: String, at: Int): (WidgetTap, Int) = text.charAt(at) match {
    case 'o' =>
      val (path, a) = wordsAt(text, at + 1)
      (WidgetTap.Open(path), a)
    case 'a' =>
      val (name, a) = wordsAt(text, at + 1)
      val (n, b) = countAt(text, a)
      var next = b
      val args = (0 until n).map { _ =>
        val (k, c) = wordsAt(text, next)
        val (v, d) = wordsAt(text, c)
        next = d
        k -> v
      }.toMap
      (WidgetTap.Action(name, args), next)
    case other => throw new IllegalArgumentException("tap " + other)
  }

  private def wordsAt(text: String, at: Int): (String, Int) = {
    val colon = text.indexOf(':', at)
    val length = text.substring(at, colon).toInt
    if (length < 0) throw new IllegalArgumentException("length " + length)
    (text.substring(colon + 1, colon + 1 + length), colon + 1 + length)
  }

  private def countAt(text: String, at: Int): (Int, Int) = {
    val end = text.indexOf(';', at)
    (text.substring(at, end).toInt, end + 1)
  }

  private def numberAt(text: String, at: Int): (Double, Int) = {
    val end = text.indexOf(';', at)
    (text.substring(at, end).toDouble, end + 1)
  }

  private def flagAt(text: String, at: Int): Boolean = text.charAt(at) match {
    case '1' => true
    case '0' => false
    case other => throw new IllegalArgumentException("flag " + other)
  }
}
