package everyui.monaco

import fabric.*

/** How one token scope is drawn (`"comment"`, `"keyword"`, `"string.quoted"`): colours in hex without the `#`
  * (`"6A9955"`), and `fontStyle` any of italic, bold, underline, strikethrough, space-separated. */
case class MonacoTokenRule(token: String, foreground: Option[String] = None, background: Option[String] = None,
                           fontStyle: Option[String] = None) {
  def toJson: Json = Wire.obj("token" -> str(token), "foreground" -> Wire.opt(foreground)(str),
    "background" -> Wire.opt(background)(str), "fontStyle" -> Wire.opt(fontStyle)(str))
}
