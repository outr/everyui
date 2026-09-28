package everyui.monaco

/** When the bracket matching the cursor's is lit (`matchBrackets`). */
enum MonacoMatchBrackets(val wire: String) extends MonacoEnum {
  case Never extends MonacoMatchBrackets("never")
  case Near extends MonacoMatchBrackets("near")
  case Always extends MonacoMatchBrackets("always")
}
