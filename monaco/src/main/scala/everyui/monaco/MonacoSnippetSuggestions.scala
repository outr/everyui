package everyui.monaco

/** Where snippets sit among the suggestions (`snippetSuggestions`). */
enum MonacoSnippetSuggestions(val wire: String) extends MonacoEnum {
  case Top extends MonacoSnippetSuggestions("top")
  case Bottom extends MonacoSnippetSuggestions("bottom")
  case Inline extends MonacoSnippetSuggestions("inline")
  case None extends MonacoSnippetSuggestions("none")
}
