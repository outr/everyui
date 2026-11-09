package everyui.draw

import everyui.Color

/** A colour as an SVG file writes it (`fill`, `stroke`, `color`, `stop-color`): a name the web knows ("tomato", "grey"
  * as "gray"), "#rgb", "#rgba", "#rrggbb", "#rrggbbaa", "rgb(r, g, b)" or "rgba(r, g, b, a)" (commas or spaces, each
  * channel a number or a percentage), and "transparent"; None for anything else, "none" and "currentColor" among them,
  * which the reader of the file settles itself. */
object SvgColor {
  def apply(value: String): Option[Color] = {
    val s = value.trim.toLowerCase
    if (s.isEmpty) None
    else if (s == "transparent") Some(Color.Clear)
    else if (s.startsWith("#")) {
      val digits = s.substring(1)
      if ((digits.length == 3 || digits.length == 4 || digits.length == 6 || digits.length == 8) &&
        digits.forall(c => (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) Some(Color.fromHex(digits))
      else None
    } else if (s.startsWith("rgb") && s.indexOf('(') > 0 && s.indexOf(')') > s.indexOf('(')) {
      val parts = s.substring(s.indexOf('(') + 1, s.indexOf(')')).split("[\\s,/]+").toList.filter(_.nonEmpty)
      val channels = parts.take(3).map(p => if (p.endsWith("%")) p.dropRight(1).toDoubleOption.map(_ * 255.0 / 100.0)
        else p.toDoubleOption)
      val alpha = parts.drop(3).headOption.map(p => if (p.endsWith("%")) p.dropRight(1).toDoubleOption.map(_ / 100.0)
        else p.toDoubleOption).getOrElse(Some(1.0))
      if (parts.length < 3 || channels.exists(_.isEmpty) || alpha.isEmpty) None
      else {
        val c = channels.map(v => math.max(0, math.min(255, math.round(v.get).toInt)))
        Some(Color.fromRGBA(c(0), c(1), c(2), math.max(0.0, math.min(1.0, alpha.get))))
      }
    } else {
      val at = names.indexOf(" " + s.replace("grey", "gray") + " ")
      if (at < 0 || s.contains(" ")) None
      else {
        val hex = at + s.replace("grey", "gray").length + 2
        Some(Color.fromHex(names.substring(hex, hex + 6)))
      }
    }
  }

  // every name the web knows, each followed by its colour, a space before and after each
  private val names: String = " " +
    "aliceblue f0f8ff antiquewhite faebd7 aqua 00ffff aquamarine 7fffd4 azure f0ffff beige f5f5dc " +
    "bisque ffe4c4 black 000000 blanchedalmond ffebcd blue 0000ff blueviolet 8a2be2 brown a52a2a " +
    "burlywood deb887 cadetblue 5f9ea0 chartreuse 7fff00 chocolate d2691e coral ff7f50 " +
    "cornflowerblue 6495ed cornsilk fff8dc crimson dc143c cyan 00ffff darkblue 00008b darkcyan " +
    "008b8b darkgoldenrod b8860b darkgray a9a9a9 darkgreen 006400 darkkhaki bdb76b darkmagenta " +
    "8b008b darkolivegreen 556b2f darkorange ff8c00 darkorchid 9932cc darkred 8b0000 darksalmon " +
    "e9967a darkseagreen 8fbc8f darkslateblue 483d8b darkslategray 2f4f4f darkturquoise 00ced1 " +
    "darkviolet 9400d3 deeppink ff1493 deepskyblue 00bfff dimgray 696969 dodgerblue 1e90ff firebrick " +
    "b22222 floralwhite fffaf0 forestgreen 228b22 fuchsia ff00ff gainsboro dcdcdc ghostwhite f8f8ff " +
    "gold ffd700 goldenrod daa520 gray 808080 green 008000 greenyellow adff2f honeydew f0fff0 " +
    "hotpink ff69b4 indianred cd5c5c indigo 4b0082 ivory fffff0 khaki f0e68c lavender e6e6fa " +
    "lavenderblush fff0f5 lawngreen 7cfc00 lemonchiffon fffacd lightblue add8e6 lightcoral f08080 " +
    "lightcyan e0ffff lightgoldenrodyellow fafad2 lightgray d3d3d3 lightgreen 90ee90 lightpink " +
    "ffb6c1 lightsalmon ffa07a lightseagreen 20b2aa lightskyblue 87cefa lightslategray 778899 " +
    "lightsteelblue b0c4de lightyellow ffffe0 lime 00ff00 limegreen 32cd32 linen faf0e6 magenta " +
    "ff00ff maroon 800000 mediumaquamarine 66cdaa mediumblue 0000cd mediumorchid ba55d3 mediumpurple " +
    "9370db mediumseagreen 3cb371 mediumslateblue 7b68ee mediumspringgreen 00fa9a mediumturquoise " +
    "48d1cc mediumvioletred c71585 midnightblue 191970 mintcream f5fffa mistyrose ffe4e1 moccasin " +
    "ffe4b5 navajowhite ffdead navy 000080 oldlace fdf5e6 olive 808000 olivedrab 6b8e23 orange " +
    "ffa500 orangered ff4500 orchid da70d6 palegoldenrod eee8aa palegreen 98fb98 paleturquoise " +
    "afeeee palevioletred db7093 papayawhip ffefd5 peachpuff ffdab9 peru cd853f pink ffc0cb plum " +
    "dda0dd powderblue b0e0e6 purple 800080 rebeccapurple 663399 red ff0000 rosybrown bc8f8f " +
    "royalblue 4169e1 saddlebrown 8b4513 salmon fa8072 sandybrown f4a460 seagreen 2e8b57 seashell " +
    "fff5ee sienna a0522d silver c0c0c0 skyblue 87ceeb slateblue 6a5acd slategray 708090 snow fffafa " +
    "springgreen 00ff7f steelblue 4682b4 tan d2b48c teal 008080 thistle d8bfd8 tomato ff6347 " +
    "turquoise 40e0d0 violet ee82ee wheat f5deb3 white ffffff whitesmoke f5f5f5 yellow ffff00 " +
    "yellowgreen 9acd32 "
}
