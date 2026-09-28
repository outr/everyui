package everyui.monaco

/** Whether the editor is tuned for a screen reader (`accessibilitySupport`). */
enum MonacoAccessibilitySupport(val wire: String) extends MonacoEnum {
  case Auto extends MonacoAccessibilitySupport("auto")
  case On extends MonacoAccessibilitySupport("on")
  case Off extends MonacoAccessibilitySupport("off")
}
