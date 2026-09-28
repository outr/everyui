package everyui.monaco

/** Whether Enter takes a suggestion (`acceptSuggestionOnEnter`). */
enum MonacoAcceptSuggestion(val wire: String) extends MonacoEnum {
  case On extends MonacoAcceptSuggestion("on")
  case Smart extends MonacoAcceptSuggestion("smart")
  case Off extends MonacoAcceptSuggestion("off")
}
