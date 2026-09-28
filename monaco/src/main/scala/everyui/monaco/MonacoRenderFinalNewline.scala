package everyui.monaco

/** How the last line's number shows when the text ends in a newline (`renderFinalNewline`). */
enum MonacoRenderFinalNewline(val wire: String) extends MonacoEnum {
  case On extends MonacoRenderFinalNewline("on")
  case Off extends MonacoRenderFinalNewline("off")
  case Dimmed extends MonacoRenderFinalNewline("dimmed")
}
