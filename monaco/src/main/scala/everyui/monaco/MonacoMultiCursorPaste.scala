package everyui.monaco

/** How a paste is spread over several cursors (`multiCursorPaste`). */
enum MonacoMultiCursorPaste(val wire: String) extends MonacoEnum {
  case Spread extends MonacoMultiCursorPaste("spread")
  case Full extends MonacoMultiCursorPaste("full")
}
