package everyui.monaco

import fabric.*

/** A decoration's mark in the overview ruler. Colours are CSS colours (`"#58a6ff"`). */
case class MonacoOverviewRuler(color: String, darkColor: Option[String] = None, position: Option[MonacoOverviewRulerLane] = None) {
  def toJson: Json = Wire.obj("color" -> str(color), "darkColor" -> Wire.opt(darkColor)(str),
    "position" -> Wire.opt(position)(p => num(p.wire)))
}
