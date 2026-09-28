package everyui.monaco

/** How the minimap is scaled (`minimap.size`). */
enum MonacoMinimapSize(val wire: String) extends MonacoEnum {
  case Proportional extends MonacoMinimapSize("proportional")
  case Fill extends MonacoMinimapSize("fill")
  case Fit extends MonacoMinimapSize("fit")
}
