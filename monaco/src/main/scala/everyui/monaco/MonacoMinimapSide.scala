package everyui.monaco

/** Which side the minimap is on (`minimap.side`). */
enum MonacoMinimapSide(val wire: String) extends MonacoEnum {
  case Right extends MonacoMinimapSide("right")
  case Left extends MonacoMinimapSide("left")
}
