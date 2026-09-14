package everyui.i18n

/** An Application Resource Bundle (the `.arb` translation files Flutter and Google's tools use): a JSON object of
  * message keys to ICU templates, with `@key` metadata objects beside them. Read as the messages alone. */
object Arb {
  /** Every `key: "template"` at the top level; metadata (`@key`, `@@locale`) and anything that is not a string is
    * skipped. Malformed input yields what was read before it. */
  def parse(text: String): Map[String, String] = {
    val reader = new ArbReader(text)
    var messages = Map.empty[String, String]
    reader.space()
    if (reader.peek() == "{") {
      reader.skip()
      var more = true
      while (more && !reader.done) {
        reader.space()
        if (reader.peek() != "\"") more = false
        else {
          val key = reader.string()
          reader.space()
          if (reader.peek() == ":") reader.skip()
          reader.space()
          if (reader.peek() == "\"") {
            val value = reader.string()
            if (!key.startsWith("@")) messages = messages + (key -> value)
          }
          else reader.value()
          reader.space()
          if (reader.peek() == ",") reader.skip() else more = false
        }
      }
    }
    messages
  }
}
