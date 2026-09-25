package everyui

/** youi's `Color`: red, green, blue and alpha as 0..255, read as 0..1 fractions or as the ints, with hex, rgba,
  * hue/saturation/brightness, and every named colour the web knows. */
final class Color(val redInt: Int, val greenInt: Int, val blueInt: Int, val alphaInt: Int) {
  def red: Double = redInt / 255.0
  def green: Double = greenInt / 255.0
  def blue: Double = blueInt / 255.0
  def alpha: Double = alphaInt / 255.0

  def redHex: String = Color.hexify(redInt)
  def greenHex: String = Color.hexify(greenInt)
  def blueHex: String = Color.hexify(blueInt)
  def alphaHex: String = Color.hexify(alphaInt)

  def withRed(red: Double): Color = new Color(Color.channel(red), greenInt, blueInt, alphaInt)
  def withGreen(green: Double): Color = new Color(redInt, Color.channel(green), blueInt, alphaInt)
  def withBlue(blue: Double): Color = new Color(redInt, greenInt, Color.channel(blue), alphaInt)
  def withAlpha(alpha: Double): Color = new Color(redInt, greenInt, blueInt, Color.channel(alpha))

  /** Part of the way to `other`, channel by channel and alpha too: 0 is this, 1 is `other`. */
  def blend(other: Color, t: Double): Color =
    new Color(Color.mix(redInt, other.redInt, t), Color.mix(greenInt, other.greenInt, t),
      Color.mix(blueInt, other.blueInt, t), Color.mix(alphaInt, other.alphaInt, t))

  /** `#rrggbb`. */
  def toHex: String = "#" + redHex + greenHex + blueHex
  /** `#rrggbbaa`. */
  def toHexAlpha: String = toHex + alphaHex
  def toRGBA: String = "rgba(" + redInt.toString + ", " + greenInt.toString + ", " + blueInt.toString + ", " + alpha.toString + ")"

  /** 0..1 around the wheel. */
  def hue: Double = {
    val colorMax = math.max(redInt, math.max(greenInt, blueInt))
    val colorMin = math.min(redInt, math.min(greenInt, blueInt))
    if (saturation == 0.0) 0.0
    else {
      val range = (colorMax - colorMin).toDouble
      val r = (colorMax - redInt) / range
      val g = (colorMax - greenInt) / range
      val b = (colorMax - blueInt) / range
      val t = if (redInt == colorMax) b - g else if (greenInt == colorMax) 2.0 + r - b else 4.0 + g - r
      val h = t / 6.0
      if (h < 0.0) h + 1.0 else h
    }
  }
  def saturation: Double = {
    val colorMax = math.max(redInt, math.max(greenInt, blueInt))
    val colorMin = math.min(redInt, math.min(greenInt, blueInt))
    if (colorMax == 0) 0.0 else (colorMax - colorMin).toDouble / colorMax.toDouble
  }
  def brightness: Double = math.max(redInt, math.max(greenInt, blueInt)).toDouble / 255.0
  def withHue(hue: Double): Color = Color.fromHSB(hue, saturation, brightness).withAlpha(alpha)
  def withSaturation(saturation: Double): Color = Color.fromHSB(hue, saturation, brightness).withAlpha(alpha)
  def withBrightness(brightness: Double): Color = Color.fromHSB(hue, saturation, brightness).withAlpha(alpha)

  override def equals(other: Any): Boolean = other match {
    case c: Color => c.redInt == redInt && c.greenInt == greenInt && c.blueInt == blueInt && c.alphaInt == alphaInt
    case _ => false
  }
  override def hashCode: Int = ((redInt * 31 + greenInt) * 31 + blueInt) * 31 + alphaInt
  override def toString: String = if (alphaInt == 255) toHex else toHexAlpha
}

object Color {
  private val Digits: String = "0123456789abcdef"

  /** One channel part of the way from `a` to `b`. */
  private def mix(a: Int, b: Int, t: Double): Int = math.max(0, math.min(255, math.round(a + (b - a) * t).toInt))

  lazy val Clear: Color = fromHex("00000000")
  lazy val AliceBlue: Color = fromHex("F0F8FFFF")
  lazy val AntiqueWhite: Color = fromHex("FAEBD7FF")
  lazy val Aqua: Color = fromHex("00FFFFFF")
  lazy val Aquamarine: Color = fromHex("7FFFD4FF")
  lazy val Azure: Color = fromHex("F0FFFFFF")
  lazy val Beige: Color = fromHex("F5F5DCFF")
  lazy val Bisque: Color = fromHex("FFE4C4FF")
  lazy val Black: Color = fromHex("000000FF")
  lazy val BlanchedAlmond: Color = fromHex("FFEBCDFF")
  lazy val Blue: Color = fromHex("0000FFFF")
  lazy val BlueViolet: Color = fromHex("8A2BE2FF")
  lazy val Brown: Color = fromHex("A52A2AFF")
  lazy val BurlyWood: Color = fromHex("DEB887FF")
  lazy val CadetBlue: Color = fromHex("5F9EA0FF")
  lazy val Chartreuse: Color = fromHex("7FFF00FF")
  lazy val Chocolate: Color = fromHex("D2691EFF")
  lazy val Coral: Color = fromHex("FF7F50FF")
  lazy val CornflowerBlue: Color = fromHex("6495EDFF")
  lazy val Cornsilk: Color = fromHex("FFF8DCFF")
  lazy val Crimson: Color = fromHex("DC143CFF")
  lazy val Cyan: Color = fromHex("00FFFFFF")
  lazy val DarkBlue: Color = fromHex("00008BFF")
  lazy val DarkCyan: Color = fromHex("008B8BFF")
  lazy val DarkGoldenRod: Color = fromHex("B8860BFF")
  lazy val DarkGray: Color = fromHex("A9A9A9FF")
  lazy val DarkGreen: Color = fromHex("006400FF")
  lazy val DarkKhaki: Color = fromHex("BDB76BFF")
  lazy val DarkMagenta: Color = fromHex("8B008BFF")
  lazy val DarkOliveGreen: Color = fromHex("556B2FFF")
  lazy val DarkOrange: Color = fromHex("FF8C00FF")
  lazy val DarkOrchid: Color = fromHex("9932CCFF")
  lazy val DarkRed: Color = fromHex("8B0000FF")
  lazy val DarkSalmon: Color = fromHex("E9967AFF")
  lazy val DarkSeaGreen: Color = fromHex("8FBC8FFF")
  lazy val DarkSlateBlue: Color = fromHex("483D8BFF")
  lazy val DarkSlateGray: Color = fromHex("2F4F4FFF")
  lazy val DarkTurquoise: Color = fromHex("00CED1FF")
  lazy val DarkViolet: Color = fromHex("9400D3FF")
  lazy val DeepPink: Color = fromHex("FF1493FF")
  lazy val DeepSkyBlue: Color = fromHex("00BFFFFF")
  lazy val DimGray: Color = fromHex("696969FF")
  lazy val DodgerBlue: Color = fromHex("1E90FFFF")
  lazy val FireBrick: Color = fromHex("B22222FF")
  lazy val FloralWhite: Color = fromHex("FFFAF0FF")
  lazy val ForestGreen: Color = fromHex("228B22FF")
  lazy val Fuchsia: Color = fromHex("FF00FFFF")
  lazy val Gainsboro: Color = fromHex("DCDCDCFF")
  lazy val GhostWhite: Color = fromHex("F8F8FFFF")
  lazy val Gold: Color = fromHex("FFD700FF")
  lazy val GoldenRod: Color = fromHex("DAA520FF")
  lazy val Gray: Color = fromHex("808080FF")
  lazy val Green: Color = fromHex("008000FF")
  lazy val GreenYellow: Color = fromHex("ADFF2FFF")
  lazy val HoneyDew: Color = fromHex("F0FFF0FF")
  lazy val HotPink: Color = fromHex("FF69B4FF")
  lazy val IndianRed: Color = fromHex("CD5C5CFF")
  lazy val Indigo: Color = fromHex("4B0082FF")
  lazy val Ivory: Color = fromHex("FFFFF0FF")
  lazy val Khaki: Color = fromHex("F0E68CFF")
  lazy val Lavender: Color = fromHex("E6E6FAFF")
  lazy val LavenderBlush: Color = fromHex("FFF0F5FF")
  lazy val LawnGreen: Color = fromHex("7CFC00FF")
  lazy val LemonChiffon: Color = fromHex("FFFACDFF")
  lazy val LightBlue: Color = fromHex("ADD8E6FF")
  lazy val LightCoral: Color = fromHex("F08080FF")
  lazy val LightCyan: Color = fromHex("E0FFFFFF")
  lazy val LightGoldenRodYellow: Color = fromHex("FAFAD2FF")
  lazy val LightGray: Color = fromHex("D3D3D3FF")
  lazy val LightGreen: Color = fromHex("90EE90FF")
  lazy val LightPink: Color = fromHex("FFB6C1FF")
  lazy val LightSalmon: Color = fromHex("FFA07AFF")
  lazy val LightSeaGreen: Color = fromHex("20B2AAFF")
  lazy val LightSkyBlue: Color = fromHex("87CEFAFF")
  lazy val LightSlateGray: Color = fromHex("778899FF")
  lazy val LightSteelBlue: Color = fromHex("B0C4DEFF")
  lazy val LightYellow: Color = fromHex("FFFFE0FF")
  lazy val Lime: Color = fromHex("00FF00FF")
  lazy val LimeGreen: Color = fromHex("32CD32FF")
  lazy val Linen: Color = fromHex("FAF0E6FF")
  lazy val Magenta: Color = fromHex("FF00FFFF")
  lazy val Maroon: Color = fromHex("800000FF")
  lazy val MediumAquaMarine: Color = fromHex("66CDAAFF")
  lazy val MediumBlue: Color = fromHex("0000CDFF")
  lazy val MediumOrchid: Color = fromHex("BA55D3FF")
  lazy val MediumPurple: Color = fromHex("9370DBFF")
  lazy val MediumSeaGreen: Color = fromHex("3CB371FF")
  lazy val MediumSlateBlue: Color = fromHex("7B68EEFF")
  lazy val MediumSpringGreen: Color = fromHex("00FA9AFF")
  lazy val MediumTurquoise: Color = fromHex("48D1CCFF")
  lazy val MediumVioletRed: Color = fromHex("C71585FF")
  lazy val MidnightBlue: Color = fromHex("191970FF")
  lazy val MintCream: Color = fromHex("F5FFFAFF")
  lazy val MistyRose: Color = fromHex("FFE4E1FF")
  lazy val Moccasin: Color = fromHex("FFE4B5FF")
  lazy val NavajoWhite: Color = fromHex("FFDEADFF")
  lazy val Navy: Color = fromHex("000080FF")
  lazy val OldLace: Color = fromHex("FDF5E6FF")
  lazy val Olive: Color = fromHex("808000FF")
  lazy val OliveDrab: Color = fromHex("6B8E23FF")
  lazy val Orange: Color = fromHex("FFA500FF")
  lazy val OrangeRed: Color = fromHex("FF4500FF")
  lazy val Orchid: Color = fromHex("DA70D6FF")
  lazy val PaleGoldenRod: Color = fromHex("EEE8AAFF")
  lazy val PaleGreen: Color = fromHex("98FB98FF")
  lazy val PaleTurquoise: Color = fromHex("AFEEEEFF")
  lazy val PaleVioletRed: Color = fromHex("DB7093FF")
  lazy val PapayaWhip: Color = fromHex("FFEFD5FF")
  lazy val PeachPuff: Color = fromHex("FFDAB9FF")
  lazy val Peru: Color = fromHex("CD853FFF")
  lazy val Pink: Color = fromHex("FFC0CBFF")
  lazy val Plum: Color = fromHex("DDA0DDFF")
  lazy val PowderBlue: Color = fromHex("B0E0E6FF")
  lazy val Purple: Color = fromHex("800080FF")
  lazy val RebeccaPurple: Color = fromHex("663399FF")
  lazy val Red: Color = fromHex("FF0000FF")
  lazy val RosyBrown: Color = fromHex("BC8F8FFF")
  lazy val RoyalBlue: Color = fromHex("4169E1FF")
  lazy val SaddleBrown: Color = fromHex("8B4513FF")
  lazy val Salmon: Color = fromHex("FA8072FF")
  lazy val SandyBrown: Color = fromHex("F4A460FF")
  lazy val SeaGreen: Color = fromHex("2E8B57FF")
  lazy val SeaShell: Color = fromHex("FFF5EEFF")
  lazy val Sienna: Color = fromHex("A0522DFF")
  lazy val Silver: Color = fromHex("C0C0C0FF")
  lazy val SkyBlue: Color = fromHex("87CEEBFF")
  lazy val SlateBlue: Color = fromHex("6A5ACDFF")
  lazy val SlateGray: Color = fromHex("708090FF")
  lazy val Snow: Color = fromHex("FFFAFAFF")
  lazy val SpringGreen: Color = fromHex("00FF7FFF")
  lazy val SteelBlue: Color = fromHex("4682B4FF")
  lazy val Tan: Color = fromHex("D2B48CFF")
  lazy val Teal: Color = fromHex("008080FF")
  lazy val Thistle: Color = fromHex("D8BFD8FF")
  lazy val Tomato: Color = fromHex("FF6347FF")
  lazy val Turquoise: Color = fromHex("40E0D0FF")
  lazy val Violet: Color = fromHex("EE82EEFF")
  lazy val Wheat: Color = fromHex("F5DEB3FF")
  lazy val White: Color = fromHex("FFFFFFFF")
  lazy val WhiteSmoke: Color = fromHex("F5F5F5FF")
  lazy val Yellow: Color = fromHex("FFFF00FF")
  lazy val YellowGreen: Color = fromHex("9ACD32FF")

  def hexify(value: Int): String = Digits.substring(value / 16, value / 16 + 1) + Digits.substring(value % 16, value % 16 + 1)
  /** A 0..1 fraction as a 0..255 channel. */
  def channel(fraction: Double): Int = math.max(0, math.min(255, (fraction * 255.0 + 0.5).toInt))
  private def pair(hex: String, at: Int): Int =
    Digits.indexOf(hex.substring(at, at + 1).toLowerCase) * 16 + Digits.indexOf(hex.substring(at + 1, at + 2).toLowerCase)

  /** A hex string, with or without `#`: 1 digit (grey), 2 (grey), 3 (rgb), 4 (rgba), 6 (rrggbb) or 8 (rrggbbaa). */
  def fromHex(value: String): Color = {
    val v = if (value.startsWith("#")) value.substring(1) else value
    val full = v.length match {
      case 1 => v + v + v + v + v + v + "ff"
      case 2 => v + v + v + "ff"
      case 3 => v.substring(0, 1) + v.substring(0, 1) + v.substring(1, 2) + v.substring(1, 2) + v.substring(2, 3) + v.substring(2, 3) + "ff"
      case 4 => v.substring(0, 1) + v.substring(0, 1) + v.substring(1, 2) + v.substring(1, 2) + v.substring(2, 3) + v.substring(2, 3) + v.substring(3, 4) + v.substring(3, 4)
      case 6 => v + "ff"
      case _ => v
    }
    new Color(pair(full, 0), pair(full, 2), pair(full, 4), pair(full, 6))
  }
  def fromRGBA(red: Int, green: Int, blue: Int, alpha: Double): Color = new Color(red, green, blue, channel(alpha))
  def fromRGB(red: Double, green: Double, blue: Double, alpha: Double = 1.0): Color =
    new Color(channel(red), channel(green), channel(blue), channel(alpha))

  /** Hue, saturation and brightness, each 0..1. */
  def fromHSB(hue: Double, saturation: Double, brightness: Double): Color = if (saturation == 0.0) {
    val v = channel(brightness)
    new Color(v, v, v, 255)
  } else {
    val h = (hue - math.floor(hue)) * 6.0
    val f = h - math.floor(h)
    val p = brightness * (1.0 - saturation)
    val q = brightness * (1.0 - saturation * f)
    val t = brightness * (1.0 - (saturation * (1.0 - f)))
    val sector = h.toInt
    if (sector == 0) fromRGB(brightness, t, p)
    else if (sector == 1) fromRGB(q, brightness, p)
    else if (sector == 2) fromRGB(p, brightness, t)
    else if (sector == 3) fromRGB(p, q, brightness)
    else if (sector == 4) fromRGB(t, p, brightness)
    else fromRGB(brightness, p, q)
  }

  /** "#abc", "aabbcc", "rgb(1, 2, 3)", "rgba(1, 2, 3, 0.5)" or "transparent"; nothing for anything else. */
  def parse(value: String): Option[Color] = {
    val s = value.trim.toLowerCase
    if (s.isEmpty) None
    else if (s == "transparent") Some(Clear)
    else if (s.startsWith("rgb")) {
      val inside = s.substring(s.indexOf('(') + 1, s.indexOf(')'))
      val parts = inside.split(',').toList.map(_.trim)
      if (parts.length == 3) Some(new Color(parts(0).toInt, parts(1).toInt, parts(2).toInt, 255))
      else if (parts.length == 4) Some(fromRGBA(parts(0).toInt, parts(1).toInt, parts(2).toInt, parts(3).toDouble))
      else None
    }
    else if (s.forall(c => Digits.contains(c.toString) || c == '#')) Some(fromHex(s))
    else None
  }
}
