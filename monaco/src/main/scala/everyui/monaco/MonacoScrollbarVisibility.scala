package everyui.monaco

/** When a scrollbar shows (`scrollbar.vertical`, `scrollbar.horizontal`). */
enum MonacoScrollbarVisibility(val wire: String) extends MonacoEnum {
  case Auto extends MonacoScrollbarVisibility("auto")
  case Visible extends MonacoScrollbarVisibility("visible")
  case Hidden extends MonacoScrollbarVisibility("hidden")
}
