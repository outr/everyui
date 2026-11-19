package everyui.render

/** A TV's launch parameters, a JSON object (`{"open": "/title/12", "contentId": "12"}`), as the names and values
  * `Platform.launchArg` and `Platform.onLaunch` hand an app: a string as itself, a number, true or false as written,
  * an object or a list as its JSON text, null left out. Anything but an object, or JSON that does not parse, is no
  * parameters. Plain Scala, so the desktop's specs read it as the TV does. */
object LaunchParams {
  def parse(json: String): Map[String, String] =
    if (json == null) Map.empty
    else try read(json) catch { case _: IllegalArgumentException => Map.empty }

  private def read(text: String): Map[String, String] = {
    var at = 0
    def done: Boolean = at >= text.length
    def peek: Char = if (done) '\u0000' else text.charAt(at)
    def space(): Unit = while (!done && " \t\r\n".indexOf(peek) >= 0) at += 1
    def wrong(why: String): Nothing = throw new IllegalArgumentException(why + " at " + at.toString)
    def expect(c: Char): Unit = {
      if (peek != c) wrong("expected " + c.toString)
      at += 1
    }

    def string(): String = {
      expect('"')
      val out = new StringBuilder
      while (peek != '"') {
        if (done) wrong("unclosed string")
        val c = peek
        at += 1
        if (c != '\\') out.append(c)
        else {
          val e = peek
          at += 1
          e match {
            case 'n' => out.append('\n')
            case 't' => out.append('\t')
            case 'r' => out.append('\r')
            case 'b' => out.append('\b')
            case 'f' => out.append('\f')
            case 'u' if at + 4 <= text.length =>
              // not hex: a NumberFormatException, an IllegalArgumentException
              out.append(Integer.parseInt(text.substring(at, at + 4), 16).toChar)
              at += 4
            case 'u' => wrong("short escape")
            case other => out.append(other)
          }
        }
      }
      at += 1
      out.toString
    }

    // past an object or a list, strings and all
    def skip(): Unit = {
      var depth = 0
      var first = true
      while (first || depth > 0) {
        first = false
        if (done) wrong("unclosed")
        peek match {
          case '"' => string()
          case '{' | '[' =>
            depth += 1
            at += 1
          case '}' | ']' =>
            depth -= 1
            at += 1
          case _ => at += 1
        }
      }
    }

    // a value as the app is handed it: None for null
    def value(): Option[String] = peek match {
      case '"' => Some(string())
      case '{' | '[' =>
        val from = at
        skip()
        Some(text.substring(from, at))
      case _ =>
        val from = at
        while (!done && ",}] \t\r\n".indexOf(peek) < 0) at += 1
        val word = text.substring(from, at)
        if (word.isEmpty) wrong("no value")
        if (word == "null") None else Some(word)
    }

    space()
    if (peek != '{') Map.empty
    else {
      at += 1
      var found = Map.empty[String, String]
      space()
      if (peek == '}') at += 1
      else {
        var more = true
        while (more) {
          space()
          val name = string()
          space()
          expect(':')
          space()
          value().foreach(v => found += name -> v)
          space()
          if (peek == ',') at += 1
          else {
            expect('}')
            more = false
          }
        }
      }
      space()
      if (done) found else Map.empty
    }
  }
}
