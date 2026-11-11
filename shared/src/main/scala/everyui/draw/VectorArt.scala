package everyui.draw

import everyui.Color
import everyui.render.Platform

/** A picture made of shapes in a box of its own (an SVG's viewBox: `left`, `top`, `width` by `height`): each shape a
  * figure, filled or stroked or both, in its own colour (its alpha the shape's opacity). A monochrome mark (an icon, a
  * logo) is drawn in one colour given at the time (`figures(size, color)`); a picture in its own colours (an SVG an
  * `Image` reads) is painted with a platform's own pen (`paint`). Read from SVG path data or from a whole SVG file.
  *
  * `naturalWidth` by `naturalHeight` is its own size where its file gives one (its `<svg>`'s width and height), `align`
  * how it sits in a box of another shape (SVG's `preserveAspectRatio`: "xMidYMid", "xMinYMax" and the rest, always as
  * meet, or "none" to be stretched to it), `ends` each shape's stroke ends and corners ("round round"; SVG's own "butt
  * miter" where it has none) and `skipped` what of its file was not drawn (a gradient, text, a filter), in words. */
case class VectorArt(left: Double, top: Double, width: Double, height: Double, shapes: List[Figure],
                     naturalWidth: Double = 0.0, naturalHeight: Double = 0.0, align: String = "xMidYMid",
                     ends: List[String] = Nil, skipped: List[String] = Nil) {
  /** The shapes fitted into a square `size` pixels on a side, kept in proportion and centred (SVG's own `xMidYMid
    * meet`), all in `color`, as much of it as each shape's own opacity lets through. */
  def figures(size: Double, color: Color): List[Figure] =
    if (width <= 0.0 || height <= 0.0) Nil
    else {
      val scale = math.min(size / width, size / height)
      val dx = (size - width * scale) / 2.0 - left * scale
      val dy = (size - height * scale) / 2.0 - top * scale
      shapes.map(f => f.copy(path = f.path.placed(scale, dx, dy),
        fill = f.fill.map(c => if (c.alphaInt == 255) color else color.withAlpha(color.alpha * c.alpha)),
        stroke = f.stroke.map(c => if (c.alphaInt == 255) color else color.withAlpha(color.alpha * c.alpha)),
        width = f.width * scale))
    }

  /** Its own size: its file's width and height where it gives them (one of them with the other in the viewBox's
    * proportion), else its viewBox's. */
  def natural: (Double, Double) =
    if (naturalWidth > 0.0 && naturalHeight > 0.0) (naturalWidth, naturalHeight)
    else if (naturalWidth > 0.0 && width > 0.0) (naturalWidth, naturalWidth * height / width)
    else if (naturalHeight > 0.0 && height > 0.0) (naturalHeight * width / height, naturalHeight)
    else (width, height)

  /** Every shape in its own colours, in order (each filled, then stroked), into a picture `wide` by `high` pixels with
    * `pen`: the viewBox fitted to it by `align`, kept in proportion and placed (meet) or stretched to it ("none"). */
  def paint(pen: ArtPen, wide: Double, high: Double): Unit = if (width > 0.0 && height > 0.0) {
    val stretched = align == "none"
    val scale = math.min(wide / width, high / height)
    val sx = if (stretched) wide / width else scale
    val sy = if (stretched) high / height else scale
    val across = if (align.contains("xMin")) 0.0 else if (align.contains("xMax")) 1.0 else 0.5
    val down = if (align.contains("YMin")) 0.0 else if (align.contains("YMax")) 1.0 else 0.5
    val dx = (wide - width * sx) * across - left * sx
    val dy = (high - height * sy) * down - top * sy
    var rest = ends
    shapes.foreach { f =>
      val finish = if (rest.nonEmpty) rest.head else "butt miter"
      rest = if (rest.nonEmpty) rest.tail else Nil
      val path = f.path.transformed(sx, 0.0, 0.0, sy, dx, dy)
      f.fill.foreach(c => pen.fill(path, c, f.evenOdd))
      f.stroke.foreach(c => pen.stroke(path, c, f.width * math.sqrt(sx * sy), finish.takeWhile(_ != ' '),
        finish.dropWhile(_ != ' ').trim))
    }
  }
}

object VectorArt {
  /** Path data (a `<path>`'s `d`) drawn filled in a box `width` by `height` from (`left`, `top`): a Material
    * symbol's 24 by 24, say. */
  def path(d: String, width: Double = 24.0, height: Double = 24.0, left: Double = 0.0, top: Double = 0.0,
           evenOdd: Boolean = false): VectorArt =
    VectorArt(left, top, width, height, List(Figure(PathData.parse(d), fill = Some(Color.Black), evenOdd = evenOdd)))

  // the elements whose contents are never drawn where they stand
  private val unseen = Set("defs", "clipPath", "mask", "symbol", "pattern", "marker", "linearGradient",
    "radialGradient", "filter", "title", "desc", "metadata", "style", "script", "foreignObject")
  // the elements that hold others and hand them what they have
  private val groups = Set("svg", "g", "a", "switch")
  // what a shape takes from the elements round it
  private val inherited = List("fill", "fill-rule", "fill-opacity", "stroke", "stroke-width", "stroke-opacity",
    "stroke-linecap", "stroke-linejoin", "color", "display", "visibility")
  // what is not drawn, by the attribute that asks for it
  private val unfollowed = List("filter" -> "filters", "mask" -> "masks", "clip-path" -> "clip paths",
    "marker-start" -> "markers", "marker-mid" -> "markers", "marker-end" -> "markers", "marker" -> "markers")
  private val identity: List[Double] = List(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)

  /** An SVG file as art: its viewBox (or its width and height), its own size and `preserveAspectRatio`, and each
    * `<path>` (every command, arcs too), `<circle>`, `<ellipse>`, `<rect>` (its corners rounded by rx and ry),
    * `<line>`, `<polygon>` and `<polyline>`, each through every `transform` round it and its own (matrix, translate,
    * scale, rotate, skewX and skewY). Each is filled in its `fill` (black where it has none; nothing for "none") and
    * stroked in its `stroke` `stroke-width` wide (with its linecap and linejoin), by its `fill-rule`, at its
    * `fill-opacity`, `stroke-opacity` and the `opacity` of it and every group round it, each taken from the `<svg>`
    * and `<g>` round it where it has none of its own, attributes and `style` alike. A colour is a name, #rgb, #rrggbb,
    * rgb(), rgba(), "none" or `currentColor` (its `color`, or black). A gradient is drawn in its first colour; text,
    * `<use>`, an embedded image, a filter, a mask, a clip, a marker, a pattern and a CSS `<style>` are not drawn, each
    * named in `skipped`, and what is left drawn. A group's opacity is each shape's (where two of its shapes overlap,
    * the one under shows through). None where it holds no SVG.
    *
    * Total on any text, as a picture from the network may be anything: a tag or attribute whose name is no XML name
    * (`<:>`, `a:=`) is passed by, a number that is not finite is no number, a shape whose points a transform has taken
    * past what a Double holds is left out (named in `skipped`), and whatever is cut short is read as far as it goes.
    * Should anything throw all the same, it is logged and the text is no SVG. */
  def parse(svg: String): Option[VectorArt] =
    if (svg == null) None
    else try read(svg) catch {
      case e: Exception =>
        Platform.log("VectorArt: an SVG could not be read (" + e.toString + ")")
        None
    }

  private def read(svg: String): Option[VectorArt] = {
    val gradients = firstColours(svg)
    var box: Option[(Double, Double, Double, Double)] = None
    var natural = (0.0, 0.0)
    var align = "xMidYMid"
    var shapes: List[Figure] = Nil
    var ends: List[String] = Nil
    var skipped: List[String] = Nil
    // what each open group hands down (its own attributes over all round it's), innermost first, and its transform and
    // opacity with all round it: one look a shape, however deep (a look through every group for each was the square
    // of the depth, and a file of nested groups held its thread for minutes)
    var context: List[Map[String, String]] = Nil
    var frames: List[(List[Double], Double)] = Nil
    var hidden = 0
    var found = false
    def skip(what: String): Unit = if (!skipped.contains(what)) skipped = skipped :+ what
    tags(svg, (name, body, closes, selfClosed) => {
      if (name == "style" && !closes && !selfClosed) skip("CSS <style>")
      if (name == "foreignObject" && !closes) skip("foreignObject")
      if (closes) {
        if (hidden > 0) {
          if (unseen.contains(name)) hidden -= 1
        } else if (groups.contains(name) && context.nonEmpty) {
          context = context.tail
          frames = frames.tail
        }
      } else if (hidden > 0 || unseen.contains(name)) {
        if (unseen.contains(name) && !selfClosed) hidden += 1
      } else {
        val own = attributes(body)
        val handed = context.headOption.getOrElse(Map.empty[String, String])
        val all = inherited.flatMap(k => own.get(k).orElse(handed.get(k)).map(k -> _)).toMap
        val (around, through) = frames.headOption.getOrElse((identity, 1.0))
        val m = own.get("transform").map(t => multiply(around, transform(t))).getOrElse(around)
        val opacity = through * own.get("opacity").map(fraction).getOrElse(1.0)
        unfollowed.foreach { case (k, what) => if (own.get(k).exists(_ != "none")) skip(what) }
        if (name == "svg" && !found) {
          found = true
          box = viewBox(own)
          natural = (size(own.get("width")), size(own.get("height")))
          own.get("preserveAspectRatio").foreach { p =>
            align = p.trim.takeWhile(_ > ' ')
            if (p.contains("slice")) skip("preserveAspectRatio slice (drawn as meet)")
          }
        }
        if (groups.contains(name)) {
          if (!selfClosed) {
            context = all :: context
            frames = (m, opacity) :: frames
          }
        } else if (name == "text") skip("text")
        else if (name == "use") skip("<use>")
        else if (name == "image") skip("embedded images")
        else if (all.get("display") != Some("none") && all.get("visibility") != Some("hidden") &&
          all.get("visibility") != Some("collapse"))
          shape(name, own).foreach { path =>
            val current = all.get("color").flatMap(SvgColor(_)).getOrElse(Color.Black)
            def faded(c: Color, key: String): Color =
              c.withAlpha(c.alpha * opacity * all.get(key).map(fraction).getOrElse(1.0))
            val fill = paint(all.getOrElse("fill", "black"), current, gradients, skip).map(faded(_, "fill-opacity"))
            val width = all.get("stroke-width").map(length).getOrElse(1.0) *
              math.sqrt(math.abs(m(0) * m(3) - m(1) * m(2)))
            val stroke = if (width <= 0.0) None
              else all.get("stroke").flatMap(s => paint(s, current, gradients, skip)).map(faded(_, "stroke-opacity"))
            val placed = path.transformed(m(0), m(1), m(2), m(3), m(4), m(5))
            // (a transform's scale times a large number can be more than a Double holds: no platform draws that)
            if (!finite(width) || !placed.ops.forall(_.values.forall(finite))) skip("shapes too large to draw")
            else if (fill.nonEmpty || stroke.nonEmpty) {
              shapes = Figure(placed, fill = fill, stroke = stroke, width = width,
                evenOdd = all.get("fill-rule").contains("evenodd")) :: shapes
              ends = (all.getOrElse("stroke-linecap", "butt") + " " + all.getOrElse("stroke-linejoin", "miter")) :: ends
            }
          }
      }
    })
    if (!found) None
    else {
      val (l, t, w, h) = box.getOrElse((0.0, 0.0, 24.0, 24.0))
      Some(VectorArt(l, t, w, h, shapes.reverse, natural._1, natural._2, align, ends.reverse, skipped))
    }
  }

  /** Each tag of `svg` in turn to `f`: its name (without a namespace's prefix), what is inside its brackets, whether it
    * closes an element and whether it closes itself; comments, CDATA, declarations, words between tags and a tag whose
    * name is no XML name (`<:>`, `<a:>`, `< svg>`) passed by. */
  private def tags(svg: String, f: (String, String, Boolean, Boolean) => Unit): Unit = {
    var at = 0
    while (at < svg.length && svg.indexOf('<', at) >= 0) {
      at = svg.indexOf('<', at)
      if (svg.startsWith("<!--", at)) at = end(svg, svg.indexOf("-->", at), 3)
      else if (svg.startsWith("<![CDATA[", at)) at = end(svg, svg.indexOf("]]>", at), 3)
      else if (svg.startsWith("<?", at) || svg.startsWith("<!", at)) at = end(svg, svg.indexOf('>', at), 1)
      else {
        val close = closing(svg, at)
        val tag = svg.substring(at + 1, math.min(close, svg.length))
        at = close + 1
        val body = tag.stripPrefix("/").stripSuffix("/")
        localName(body.takeWhile(c => c > ' ')).foreach(name => f(name, body, tag.startsWith("/"), tag.endsWith("/")))
      }
    }
  }

  /** An XML name without its namespace's prefix ("svg:rect" is "rect", "rect" itself): None where it is none, as for
    * an empty one, colons alone (":", "::"), a prefix with nothing after it ("a:"), nothing before a colon (":b"),
    * more than one colon, or a character no name holds. */
  private[everyui] def localName(qualified: String): Option[String] = {
    val colon = qualified.indexOf(':')
    val local = if (colon < 0) qualified else qualified.substring(colon + 1)
    if (named(local) && (colon < 0 || named(qualified.substring(0, colon)))) Some(local) else None
  }

  // whether `s` is a name XML allows, with no colon: a letter or "_", then letters, digits, "-", "_" and "."
  private def named(s: String): Boolean = s.nonEmpty && (s.charAt(0).isLetter || s.charAt(0) == '_') &&
    s.forall(c => c.isLetter || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.')

  // whether `v` is a number a picture can be drawn with: neither infinite nor NaN, as a number less itself is 0 unless
  // it is one of those (written so, as a Roku has no isNaN or isInfinite)
  private[everyui] def finite(v: Double): Boolean = v - v == 0.0

  /** The first colour of each gradient by its id (one that takes its stops from another, by `href`, that one's), with
    * its stop-opacity: what a shape filled with it is drawn in. Read before the shapes, as a file may define its
    * gradients after the shapes that use them. */
  private def firstColours(svg: String): Map[String, Color] = {
    var first = Map.empty[String, Color]
    var links = List.empty[(String, String)]
    var at = ""
    tags(svg, (name, body, closes, selfClosed) => {
      if (name == "linearGradient" || name == "radialGradient") {
        if (closes) at = ""
        else {
          val a = attributes(body)
          val id = a.getOrElse("id", "")
          a.get("href").foreach(h => links = (id, h.trim.stripPrefix("#")) :: links)
          at = if (selfClosed) "" else id
        }
      } else if (name == "stop" && !closes && at.nonEmpty && !first.contains(at)) {
        val a = attributes(body)
        val c = a.get("stop-color").flatMap(SvgColor(_)).getOrElse(Color.Black)
        first = first.updated(at, c.withAlpha(c.alpha * a.get("stop-opacity").map(fraction).getOrElse(1.0)))
      }
    })
    links.foldLeft(first) { case (m, (id, to)) => if (m.contains(id) || !m.contains(to)) m else m.updated(id, m(to)) }
  }

  /** A fill or a stroke's colour: None for "none"; `current` for currentColor; a gradient's first colour (noted in
    * `skip`), or a fallback after it, or nothing for another paint server (a pattern); black where it is no colour. */
  private def paint(value: String, current: Color, gradients: Map[String, Color],
                    skip: String => Unit): Option[Color] = {
    val v = value.trim
    if (v == "none") None
    else if (v == "currentColor" || v == "currentcolor") Some(current)
    else if (v.startsWith("url(") && v.indexOf(')') > 0) {
      val id = v.substring(4, v.indexOf(')')).trim.filter(c => c != '"' && c != '\'').stripPrefix("#")
      val fallback = v.substring(v.indexOf(')') + 1).trim
      gradients.get(id) match {
        case Some(c) =>
          skip("gradients (drawn in their first colour)")
          Some(c)
        case None =>
          skip("patterns")
          // a fallback is a colour (or none, or currentColor), never another url(): read as one, a value of url()
          // after url() went as deep as there were of them, and a long one ran out of stack
          if (fallback.nonEmpty && !fallback.startsWith("url(")) paint(fallback, current, gradients, skip) else None
      }
    } else SvgColor(v).orElse(Some(Color.Black))
  }

  /** `transform`'s functions as one matrix (a, b, c, d, e, f), the first applied last, as SVG has them. Read in one
    * pass (one that cut off what it had read for each function copied the rest each time, the square of its length). */
  private def transform(t: String): List[Double] = {
    var m = identity
    var from = 0
    while (t.indexOf('(', from) > from && t.indexOf(')', from) > t.indexOf('(', from)) {
      val open = t.indexOf('(', from)
      val close = t.indexOf(')', from)
      val kind = t.substring(from, open).filter(_.isLetter)
      val v = numbers(t.substring(open + 1, close))
      def at(i: Int, otherwise: Double): Double = if (v.length > i) v(i) else otherwise
      val turn = at(0, 0.0) * math.Pi / 180.0
      val step = kind match {
        case "matrix" if v.length == 6 => v
        case "translate" => List(1.0, 0.0, 0.0, 1.0, at(0, 0.0), at(1, 0.0))
        case "scale" => List(at(0, 1.0), 0.0, 0.0, at(1, at(0, 1.0)), 0.0, 0.0)
        case "rotate" =>
          val (cx, cy) = (at(1, 0.0), at(2, 0.0))
          multiply(multiply(List(1.0, 0.0, 0.0, 1.0, cx, cy),
            List(math.cos(turn), math.sin(turn), -math.sin(turn), math.cos(turn), 0.0, 0.0)),
            List(1.0, 0.0, 0.0, 1.0, -cx, -cy))
        case "skewX" => List(1.0, 0.0, math.tan(turn), 1.0, 0.0, 0.0)
        case "skewY" => List(1.0, math.tan(turn), 0.0, 1.0, 0.0, 0.0)
        case _ => identity
      }
      m = multiply(m, step)
      from = close + 1
    }
    m
  }

  // `p` after `q`: a point through `q`, then through `p`
  private def multiply(p: List[Double], q: List[Double]): List[Double] = List(
    p(0) * q(0) + p(2) * q(1), p(1) * q(0) + p(3) * q(1), p(0) * q(2) + p(2) * q(3), p(1) * q(2) + p(3) * q(3),
    p(0) * q(4) + p(2) * q(5) + p(4), p(1) * q(4) + p(3) * q(5) + p(5))

  private def end(svg: String, found: Int, length: Int): Int = if (found < 0) svg.length else found + length

  // the `>` that ends the tag starting at `from`, past any in its quoted values
  private def closing(svg: String, from: Int): Int = {
    var i = from + 1
    var quote = ' '
    while (i < svg.length && (quote != ' ' || svg.charAt(i) != '>')) {
      val c = svg.charAt(i)
      if (quote == ' ' && (c == '"' || c == '\'')) quote = c
      else if (c == quote) quote = ' '
      i += 1
    }
    i
  }

  /** A tag's attributes by name (without a namespace's prefix), with its `style`'s declarations over them. One whose
    * name is no XML name (`::=`, `a:=`, `:b=`) is passed by, and of words with no value before a name (`hidden`) the
    * name alone is taken. */
  private def attributes(tag: String): Map[String, String] = {
    var out = Map.empty[String, String]
    var i = tag.indexWhere(c => c <= ' ')
    while (i >= 0 && i < tag.length) {
      val eq = tag.indexOf('=', i)
      if (eq < 0) i = -1
      else {
        val words = tag.substring(i, eq).trim
        val name = localName(words.reverse.takeWhile(c => c > ' ').reverse)
        var q = eq + 1
        while (q < tag.length && tag.charAt(q) <= ' ') q += 1
        if (q >= tag.length) i = -1
        else {
          val quote = tag.charAt(q)
          val stop = if (quote == '"' || quote == '\'') tag.indexOf(quote, q + 1) else tag.indexWhere(c => c <= ' ', q)
          val value = if (stop < 0) tag.substring(if (quote == '"' || quote == '\'') q + 1 else q)
            else tag.substring(if (quote == '"' || quote == '\'') q + 1 else q, stop)
          name.foreach(n => out = out + (n -> value.trim))
          i = if (stop < 0) -1 else stop + 1
        }
      }
    }
    out.get("style").foreach { style =>
      style.split(';').foreach { d =>
        val colon = d.indexOf(':')
        if (colon > 0) out = out + (d.substring(0, colon).trim -> d.substring(colon + 1).trim)
      }
    }
    out
  }

  // the finite numbers in `s` (a NaN or 1e999 no number)
  private def numbers(s: String): List[Double] =
    s.split("[\\s,]+").toList.filter(_.nonEmpty).flatMap(n => n.toDoubleOption.filter(finite))

  // a length's number, its unit ("px") left off; 0 for none, or one that is not finite
  private def length(s: String): Double =
    s.reverse.dropWhile(c => c.isLetter || c == '%').reverse.trim.toDoubleOption.filter(finite).getOrElse(0.0)

  // an opacity, a number from 0 to 1 or a percentage (whole for one that is no number, NaN among them)
  private def fraction(s: String): Double = {
    val v = if (s.trim.endsWith("%")) length(s) / 100.0 else s.trim.toDoubleOption.filter(finite).getOrElse(1.0)
    math.max(0.0, math.min(1.0, v))
  }

  // the `<svg>`'s width or height as its own size: none for a share of something else (100%) or of the words (1em)
  private def size(s: Option[String]): Double = s match {
    case Some(v) if !v.trim.endsWith("%") && !v.trim.endsWith("em") && !v.trim.endsWith("ex") => length(v)
    case _ => 0.0
  }

  private def viewBox(svg: Map[String, String]): Option[(Double, Double, Double, Double)] =
    svg.get("viewBox").map(numbers) match {
      case Some(List(l, t, w, h)) => Some((l, t, w, h))
      case _ =>
        val (w, h) = (svg.get("width").map(length).getOrElse(0.0), svg.get("height").map(length).getOrElse(0.0))
        // a width of "1em", say, is no box: 24 by 24 is what such an icon is drawn in nearly always
        if (w > 0.0 && h > 0.0 && !svg.get("width").exists(_.endsWith("em"))) Some((0.0, 0.0, w, h)) else None
    }

  /** The path of one of the shapes SVG has, by its element's name; None for any other element, and for a shape of no
    * size (a rect with no width, a circle with no radius), which SVG draws as nothing. */
  private def shape(name: String, a: Map[String, String]): Option[Path] = {
    def n(key: String): Double = a.get(key).map(length).getOrElse(0.0)
    name match {
      case "path" => a.get("d").map(PathData.parse)
      case "circle" => if (n("r") > 0.0) Some(Path.circle(n("cx"), n("cy"), n("r"))) else None
      case "ellipse" =>
        val (cx, cy, rx, ry) = (n("cx"), n("cy"), n("rx"), n("ry"))
        if (rx <= 0.0 || ry <= 0.0) None
        else Some(PathData.parse("M" + (cx - rx) + " " + cy + "A" + rx + " " + ry + " 0 1 0 " + (cx + rx) + " " + cy +
          "A" + rx + " " + ry + " 0 1 0 " + (cx - rx) + " " + cy + "Z"))
      case "rect" =>
        val (x, y, w, h) = (n("x"), n("y"), n("width"), n("height"))
        // one radius given is both; neither more than half its side
        val rx = math.min(w / 2.0, if (a.contains("rx")) n("rx") else n("ry"))
        val ry = math.min(h / 2.0, if (a.contains("ry")) n("ry") else n("rx"))
        if (w <= 0.0 || h <= 0.0) None
        else if (rx <= 0.0 || ry <= 0.0) Some(Path.rect(x, y, w, h))
        else if (rx == ry) Some(Path.roundRect(x, y, w, h, rx))
        else {
          def corner(toX: Double, toY: Double): String = "A" + rx + " " + ry + " 0 0 1 " + toX + " " + toY
          Some(PathData.parse("M" + (x + rx) + " " + y + "H" + (x + w - rx) + corner(x + w, y + ry) + "V" +
            (y + h - ry) + corner(x + w - rx, y + h) + "H" + (x + rx) + corner(x, y + h - ry) + "V" + (y + ry) +
            corner(x + rx, y) + "Z"))
        }
      case "line" => Some(Path.line(n("x1"), n("y1"), n("x2"), n("y2")))
      case "polygon" => a.get("points").map(p => PathData.parse("M" + p + "Z"))
      case "polyline" => a.get("points").map(p => PathData.parse("M" + p))
      case _ => None
    }
  }
}
