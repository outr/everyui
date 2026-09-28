package everyui.monaco

/** Where in the minimap a decoration is marked. */
enum MonacoMinimapPosition(val wire: Int) {
  case Inline extends MonacoMinimapPosition(1)
  case Gutter extends MonacoMinimapPosition(2)
}
