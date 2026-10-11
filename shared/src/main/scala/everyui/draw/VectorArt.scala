package everyui.draw

import everyui.Color

/** A monochrome mark in a box of its own (an SVG's viewBox: `left`, `top`, `width` by `height`): its shapes as
  * figures, each filled or stroked or both, in a colour that stands for "drawn" and is given at the time
  * (`figures(size, color)`). What an icon or a logo is, read from SVG path data or from a whole simple SVG file. */
case class VectorArt(left: Double, top: Double, width: Double, height: Double, shapes: List[Figure]) {
  /** The shapes fitted into a square `size` pixels on a side, kept in proportion and centred (SVG's own `xMidYMid
    * meet`), all in `color`. */
  def figures(size: Double, color: Color): List[Figure] =
    if (width <= 0.0 || height <= 0.0) Nil
    else {
      val scale = math.min(size / width, size / height)
      val dx = (size - width * scale) / 2.0 - left * scale
      val dy = (size - height * scale) / 2.0 - top * scale
      shapes.map(f => f.copy(path = f.path.placed(scale, dx, dy), fill = f.fill.map(_ => color),
        stroke = f.stroke.map(_ => color), width = f.width * scale))
    }
}

object VectorArt {
  /** Path data (a `<path>`'s `d`) drawn filled in a box `width` by `height` from (`left`, `top`): a Material
    * symbol's 24 by 24, say. */
  def path(d: String, width: Double = 24.0, height: Double = 24.0, left: Double = 0.0, top: Double = 0.0,
           evenOdd: Boolean = false): VectorArt =
    VectorArt(left, top, width, height, List(Figure(PathData.parse(d), fill = Some(Color.Black), evenOdd = evenOdd)))

  // the elements whose contents are never drawn where they stand
  private val unseen = Set("defs", "clipPath", "mask", "symbol", "pattern", "marker", "linearGradient", "radialGradient",
    "filter", "title", "desc", "metadata", "style", "script")
  // what a shape takes from the elements round it
  private val inherited = List("fill", "fill-rule", "stroke", "stroke-width", "display", "visibility")

  /** A simple monochrome SVG file as art (simple-icons' marks, an app's own icons): its viewBox (or its width and
    * height), and each `<path>`, `<circle>`, `<ellipse>`, `<rect>`, `<line>`, `<polygon>` and `<polyline>`, filled
    * unless its fill is "none" and stroked where it has a stroke, by the fill rule and stroke width it has or takes
    * from the `<svg>` and `<g>` round it (attributes and `style` alike). Every colour in it is the one colour the art
    * is drawn in. A `transform`, a `<use>`, a clip, a mask, a gradient and text are not followed; anything with
    * them may draw wrongly, and should be flattened to plain paths first. None where it holds no SVG. */
  def parse(svg: String): Option[VectorArt] = {
    var at = 0
    var box: Option[(Double, Double, Double, Double)] = None
    var shapes: List[Figure] = Nil
    // the attributes each open `<svg>` and `<g>` hands down, innermost first
    var context: List[Map[String, String]] = Nil
    var hidden = 0
    var found = false
    while (at < svg.length && svg.indexOf('<', at) >= 0) {
      at = svg.indexOf('<', at)
      if (svg.startsWith("<!--", at)) at = end(svg, svg.indexOf("-->", at), 3)
      else if (svg.startsWith("<![CDATA[", at)) at = end(svg, svg.indexOf("]]>", at), 3)
      else if (svg.startsWith("<?", at) || svg.startsWith("<!", at)) at = end(svg, svg.indexOf('>', at), 1)
      else {
        val close = closing(svg, at)
        val tag = svg.substring(at + 1, close)
        at = close + 1
        val closes = tag.startsWith("/")
        val selfClosed = tag.endsWith("/")
        val body = tag.stripPrefix("/").stripSuffix("/")
        val name = body.takeWhile(c => c > ' ').split(':').last
        if (closes) {
          if (hidden > 0) {
            if (unseen.contains(name)) hidden -= 1
          } else if ((name == "g" || name == "svg") && context.nonEmpty) context = context.tail
        } else if (hidden > 0) {
          if (unseen.contains(name) && !selfClosed) hidden += 1
        } else if (unseen.contains(name)) {
          if (!selfClosed) hidden += 1
        } else {
          val own = attributes(body)
          val all = inherited.flatMap(k => own.get(k).orElse(context.flatMap(_.get(k)).headOption).map(k -> _)).toMap
          if (name == "svg") {
            found = true
            if (box.isEmpty) box = viewBox(own)
          }
          if ((name == "g" || name == "svg") && !selfClosed) context = own :: context
          else if (all.get("display") != Some("none") && all.get("visibility") != Some("hidden"))
            shape(name, own).foreach { path =>
              val filled = all.getOrElse("fill", "black") != "none"
              val stroked = all.get("stroke").exists(_ != "none")
              if (filled || stroked) shapes = Figure(path, fill = if (filled) Some(Color.Black) else None,
                stroke = if (stroked) Some(Color.Black) else None,
                width = all.get("stroke-width").map(length).getOrElse(1.0),
                evenOdd = all.get("fill-rule").contains("evenodd")) :: shapes
            }
        }
      }
    }
    if (!found) None
    else {
      val (l, t, w, h) = box.getOrElse((0.0, 0.0, 24.0, 24.0))
      Some(VectorArt(l, t, w, h, shapes.reverse))
    }
  }

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

  /** A tag's attributes by name (without a namespace's prefix), with its `style`'s declarations over them. */
  private def attributes(tag: String): Map[String, String] = {
    var out = Map.empty[String, String]
    var i = tag.indexWhere(c => c <= ' ')
    while (i >= 0 && i < tag.length) {
      val eq = tag.indexOf('=', i)
      if (eq < 0) i = -1
      else {
        val name = tag.substring(i, eq).trim.split(':').last
        var q = eq + 1
        while (q < tag.length && tag.charAt(q) <= ' ') q += 1
        if (q >= tag.length) i = -1
        else {
          val quote = tag.charAt(q)
          val stop = if (quote == '"' || quote == '\'') tag.indexOf(quote, q + 1) else tag.indexWhere(c => c <= ' ', q)
          val value = if (stop < 0) tag.substring(if (quote == '"' || quote == '\'') q + 1 else q)
            else tag.substring(if (quote == '"' || quote == '\'') q + 1 else q, stop)
          out = out + (name -> value.trim)
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

  private def numbers(s: String): List[Double] =
    s.split("[\\s,]+").toList.filter(_.nonEmpty).flatMap(n => n.toDoubleOption)

  // a length's number, its unit ("px") left off
  private def length(s: String): Double = s.reverse.dropWhile(c => c.isLetter || c == '%').reverse.trim.toDoubleOption.getOrElse(0.0)

  private def viewBox(svg: Map[String, String]): Option[(Double, Double, Double, Double)] =
    svg.get("viewBox").map(numbers) match {
      case Some(List(l, t, w, h)) => Some((l, t, w, h))
      case _ =>
        val (w, h) = (svg.get("width").map(length).getOrElse(0.0), svg.get("height").map(length).getOrElse(0.0))
        // a width of "1em", say, is no box: 24 by 24 is what such an icon is drawn in nearly always
        if (w > 0.0 && h > 0.0 && !svg.get("width").exists(_.endsWith("em"))) Some((0.0, 0.0, w, h)) else None
    }

  /** The path of one of the shapes SVG has, by its element's name; None for any other element. */
  private def shape(name: String, a: Map[String, String]): Option[Path] = {
    def n(key: String): Double = a.get(key).map(length).getOrElse(0.0)
    name match {
      case "path" => a.get("d").map(PathData.parse)
      case "circle" => Some(Path.circle(n("cx"), n("cy"), n("r")))
      case "ellipse" =>
        val (cx, cy, rx, ry) = (n("cx"), n("cy"), n("rx"), n("ry"))
        Some(PathData.parse("M" + (cx - rx) + " " + cy + "A" + rx + " " + ry + " 0 1 0 " + (cx + rx) + " " + cy +
          "A" + rx + " " + ry + " 0 1 0 " + (cx - rx) + " " + cy + "Z"))
      case "rect" =>
        val r = if (a.contains("rx")) n("rx") else n("ry")
        Some(Path.roundRect(n("x"), n("y"), n("width"), n("height"), r))
      case "line" => Some(Path.line(n("x1"), n("y1"), n("x2"), n("y2")))
      case "polygon" => a.get("points").map(p => PathData.parse("M" + p + "Z"))
      case "polyline" => a.get("points").map(p => PathData.parse("M" + p))
      case _ => None
    }
  }
}
