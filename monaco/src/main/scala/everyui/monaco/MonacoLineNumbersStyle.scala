package everyui.monaco

/** How the gutter numbers lines (`lineNumbers`). */
enum MonacoLineNumbersStyle(val wire: String) extends MonacoEnum {
  case On extends MonacoLineNumbersStyle("on")
  case Off extends MonacoLineNumbersStyle("off")
  case Relative extends MonacoLineNumbersStyle("relative")
  case Interval extends MonacoLineNumbersStyle("interval")
}
