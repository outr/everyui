package everyui.monaco

/** When the folding arrows show (`showFoldingControls`). */
enum MonacoShowFoldingControls(val wire: String) extends MonacoEnum {
  case Always extends MonacoShowFoldingControls("always")
  case Never extends MonacoShowFoldingControls("never")
  case Mouseover extends MonacoShowFoldingControls("mouseover")
}
