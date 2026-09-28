package everyui.monaco

import fabric.*

/** A decoration's mark in the minimap. */
case class MonacoMinimapDecoration(color: String, position: Option[MonacoMinimapPosition] = None) {
  def toJson: Json = Wire.obj("color" -> str(color), "position" -> Wire.opt(position)(p => num(p.wire)))
}
