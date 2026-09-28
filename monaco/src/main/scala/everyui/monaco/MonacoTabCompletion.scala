package everyui.monaco

/** Whether Tab completes (`tabCompletion`). */
enum MonacoTabCompletion(val wire: String) extends MonacoEnum {
  case On extends MonacoTabCompletion("on")
  case Off extends MonacoTabCompletion("off")
  case OnlySnippets extends MonacoTabCompletion("onlySnippets")
}
