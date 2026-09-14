package everyui

/** youi's `Color`, reduced to what the ported examples use: RGBA channels and hex. */
class Color(val red: Int, val green: Int, val blue: Int, val alpha: Int):
  def toHex: String = "#" + Color.hex2(red) + Color.hex2(green) + Color.hex2(blue)
  /** `#rrggbbaa`. */
  def toHexAlpha: String = toHex + Color.hex2(alpha)
  /** The same colour at `opacity` (0..1). */
  def withAlpha(opacity: Double): Color = new Color(red, green, blue, (opacity * 255.0 + 0.5).toInt)

  override def toString: String = toHex

object Color:
  private val Digits: String = "0123456789abcdef"

  lazy val Clear: Color = new Color(0, 0, 0, 0)
  lazy val Black: Color = fromHex("000000")
  lazy val White: Color = fromHex("ffffff")
  lazy val LightGray: Color = fromHex("d3d3d3")
  lazy val DarkGray: Color = fromHex("a9a9a9")
  lazy val SteelBlue: Color = fromHex("4682b4")

  def hex2(value: Int): String =
    Digits.substring(value / 16, value / 16 + 1) + Digits.substring(value % 16, value % 16 + 1)

  private def channel(hex: String, at: Int): Int =
    Digits.indexOf(hex.substring(at, at + 1).toLowerCase) * 16 + Digits.indexOf(hex.substring(at + 1, at + 2).toLowerCase)

  /** `#rrggbb`, `rrggbb`, `#rgb` or `rgb`. */
  def fromHex(value: String): Color =
    val v = if value.startsWith("#") then value.substring(1) else value
    val full =
      if v.length == 3 then
        v.substring(0, 1) + v.substring(0, 1) + v.substring(1, 2) + v.substring(1, 2) + v.substring(2, 3) + v.substring(2, 3)
      else v
    new Color(channel(full, 0), channel(full, 2), channel(full, 4), 255)
