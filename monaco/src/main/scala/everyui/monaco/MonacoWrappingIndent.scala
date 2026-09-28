package everyui.monaco

/** How a wrapped line's continuation is indented (`wrappingIndent`). */
enum MonacoWrappingIndent(val wire: String) extends MonacoEnum {
  case None extends MonacoWrappingIndent("none")
  case Same extends MonacoWrappingIndent("same")
  case Indent extends MonacoWrappingIndent("indent")
  case DeepIndent extends MonacoWrappingIndent("deepIndent")
}
