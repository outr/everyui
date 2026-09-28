package everyui.monaco

/** How long lines wrap (`wordWrap`). */
enum MonacoWordWrap(val wire: String) extends MonacoEnum {
  case Off extends MonacoWordWrap("off")
  case On extends MonacoWordWrap("on")
  case WordWrapColumn extends MonacoWordWrap("wordWrapColumn")
  case Bounded extends MonacoWordWrap("bounded")
}
