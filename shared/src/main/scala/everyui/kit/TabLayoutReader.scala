package everyui.kit

/** Reads a `TabLayout` back from the JSON `TabLayout.json` writes: its seven fields, any other passed over. Throws
  * on text that is not such JSON, which `TabLayout.parse` answers as None. */
private[kit] class TabLayoutReader(text: String) {
  private var at = 0

  /** Whether all of the text has been read. */
  def ended: Boolean = {
    blank()
    at >= text.length
  }

  def layout(): TabLayout = {
    var l = TabLayout()
    expect('{')
    if (!take('}')) {
      var more = true
      while (more) {
        val name = string()
        expect(':')
        if (name == "tabs") l = l.copy(tabs = list(() => string()))
        else if (name == "selected") l = l.copy(selected = string())
        else if (name == "vertical") l = l.copy(vertical = boolean())
        else if (name == "ratio") l = l.copy(ratio = number())
        else if (name == "parts") l = l.copy(parts = list(() => layout()))
        else if (name == "keepEmpty") l = l.copy(keepEmpty = if (nothing()) None else Some(boolean()))
        else if (name == "data") l = l.copy(data = strings())
        else skip()
        more = take(',')
      }
      expect('}')
    }
    l
  }

  private def list[T](item: () => T): List[T] = {
    expect('[')
    var items: List[T] = Nil
    if (!take(']')) {
      var more = true
      while (more) {
        items = item() :: items
        more = take(',')
      }
      expect(']')
    }
    items.reverse
  }

  private def string(): String = {
    expect('"')
    var out = ""
    while (text.charAt(at) != '"') {
      val c = text.charAt(at)
      if (c == '\\') {
        val e = text.charAt(at + 1)
        if (e == 'u') {
          // a control character, which `json` never writes and an id has no use for: a space, as `quoted` writes it
          out = out + " "
          at += 6
        }
        else {
          out = out + (if (e == 'n') "\n" else if (e == 't') "\t" else e.toString)
          at += 2
        }
      }
      else {
        out = out + c.toString
        at += 1
      }
    }
    at += 1
    out
  }

  private def boolean(): Boolean = {
    blank()
    if (ahead("true")) {
      at += 4
      true
    }
    else if (ahead("false")) {
      at += 5
      false
    }
    else throw new IllegalArgumentException("a boolean at " + at.toString)
  }

  // a JSON null, read past where it comes next
  private def nothing(): Boolean = {
    blank()
    if (ahead("null")) {
      at += 4
      true
    }
    else false
  }

  // an object of words by name, `{"name": "words", …}`
  private def strings(): Map[String, String] = {
    expect('{')
    var out = Map.empty[String, String]
    if (!take('}')) {
      var more = true
      while (more) {
        val name = string()
        expect(':')
        out = out + (name -> string())
        more = take(',')
      }
      expect('}')
    }
    out
  }

  /** A number, read digit by digit rather than by `toDouble`, which the Roku's simulator reads to a whole number. */
  private def number(): Double = {
    blank()
    val negative = take('-')
    // the digits as a whole number, divided once by the power of ten after the point: exact, as toDouble is, for what
    // `json` writes
    var value = 0.0
    var places = 0
    var fraction = false
    var more = true
    while (more && at < text.length) {
      val c = text.charAt(at).toString
      val digit = "0123456789".indexOf(c)
      if (digit >= 0) {
        value = value * 10.0 + digit
        if (fraction) places += 1
        at += 1
      }
      else if (c == "." && !fraction) {
        fraction = true
        at += 1
      }
      else more = false
    }
    var ten = 1.0
    var p = 0
    while (p < places) {
      ten = ten * 10.0
      p += 1
    }
    value = value / ten
    if (take('e') || take('E')) {
      val down = take('-')
      if (!down) take('+')
      var power = 0
      while (at < text.length && "0123456789".indexOf(text.charAt(at).toString) >= 0) {
        power = power * 10 + "0123456789".indexOf(text.charAt(at).toString)
        at += 1
      }
      var i = 0
      while (i < power) {
        value = if (down) value / 10.0 else value * 10.0
        i += 1
      }
    }
    if (negative) -value else value
  }

  /** A value of a field it does not know, passed over. */
  private def skip(): Unit = {
    blank()
    val c = text.charAt(at)
    if (c == '"') string()
    else if (c == '{') layout()
    else if (c == '[') list(() => skip())
    else if (c == 't' || c == 'f') boolean()
    else if (c == 'n' && ahead("null")) at += 4
    else number()
  }

  /** Whether `word` comes next. */
  private def ahead(word: String): Boolean = at + word.length <= text.length && text.substring(at, at + word.length) == word

  private def blank(): Unit = while (at < text.length && " \t\r\n".indexOf(text.charAt(at).toString) >= 0) at += 1

  private def take(c: Char): Boolean = {
    blank()
    if (at < text.length && text.charAt(at) == c) {
      at += 1
      true
    }
    else false
  }

  private def expect(c: Char): Unit = if (!take(c)) throw new IllegalArgumentException("'" + c.toString + "' at " + at.toString)
}
