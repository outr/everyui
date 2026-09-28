package everyui.monaco

/** The modifier that adds a cursor with a click (`multiCursorModifier`). */
enum MonacoMultiCursorModifier(val wire: String) extends MonacoEnum {
  case CtrlCmd extends MonacoMultiCursorModifier("ctrlCmd")
  case Alt extends MonacoMultiCursorModifier("alt")
}
