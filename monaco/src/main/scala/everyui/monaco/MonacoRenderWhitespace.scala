package everyui.monaco

/** Where whitespace is drawn (`renderWhitespace`). */
enum MonacoRenderWhitespace(val wire: String) extends MonacoEnum {
  case None extends MonacoRenderWhitespace("none")
  case Boundary extends MonacoRenderWhitespace("boundary")
  case Selection extends MonacoRenderWhitespace("selection")
  case Trailing extends MonacoRenderWhitespace("trailing")
  case All extends MonacoRenderWhitespace("all")
}
