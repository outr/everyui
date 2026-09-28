package everyui.monaco

/** When brackets and quotes close themselves (`autoClosingBrackets`, `autoClosingQuotes`). */
enum MonacoAutoClosingStrategy(val wire: String) extends MonacoEnum {
  case Always extends MonacoAutoClosingStrategy("always")
  case LanguageDefined extends MonacoAutoClosingStrategy("languageDefined")
  case BeforeWhitespace extends MonacoAutoClosingStrategy("beforeWhitespace")
  case Never extends MonacoAutoClosingStrategy("never")
}
