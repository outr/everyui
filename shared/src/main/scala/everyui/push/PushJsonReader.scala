package everyui.push

/** Reads one JSON value from `text` (PushJson), failing with an IllegalArgumentException where it is not JSON. */
private[push] final class PushJsonReader(text: String) {
  private var at = 0

  def done: Boolean = at >= text.length

  private def fail(): Nothing = throw new IllegalArgumentException("not JSON at " + at.toString)

  def space(): Unit = while (!done && " \t\r\n".indexOf(text.charAt(at).toInt) >= 0) at += 1

  private def expect(word: String): Unit =
    if (text.startsWith(word, at)) at += word.length else fail()

  def value(): Any = {
    space()
    if (done) fail()
    text.charAt(at) match {
      case '[' =>
        at += 1
        var items = Vector.empty[Any]
        space()
        if (!done && text.charAt(at) == ']') at += 1
        else {
          var more = true
          while (more) {
            items :+= value()
            space()
            if (done) fail()
            if (text.charAt(at) == ',') at += 1
            else if (text.charAt(at) == ']') { at += 1; more = false }
            else fail()
          }
        }
        items
      case '{' =>
        at += 1
        var fields = Map.empty[String, Any]
        space()
        if (!done && text.charAt(at) == '}') at += 1
        else {
          var more = true
          while (more) {
            space()
            val key = string()
            space()
            expect(":")
            fields += key -> value()
            space()
            if (done) fail()
            if (text.charAt(at) == ',') at += 1
            else if (text.charAt(at) == '}') { at += 1; more = false }
            else fail()
          }
        }
        fields
      case '"' => string()
      case 't' => expect("true"); true
      case 'f' => expect("false"); false
      case 'n' => expect("null"); null
      case _ => number()
    }
  }

  private def number(): Double = {
    val start = at
    while (!done && "+-0123456789.eE".indexOf(text.charAt(at).toInt) >= 0) at += 1
    text.substring(start, at).toDoubleOption.getOrElse(fail())
  }

  private def string(): String = {
    if (done || text.charAt(at) != '"') fail()
    at += 1
    val out = new StringBuilder
    var closed = false
    while (!closed) {
      if (done) fail()
      val c = text.charAt(at)
      at += 1
      if (c == '"') closed = true
      else if (c != '\\') out.append(c)
      else {
        if (done) fail()
        val e = text.charAt(at)
        at += 1
        e match {
          case 'n' => out.append('\n')
          case 't' => out.append('\t')
          case 'r' => out.append('\r')
          case 'b' => out.append('\b')
          case 'f' => out.append('\f')
          case 'u' =>
            if (at + 4 > text.length) fail()
            var code = 0
            (0 until 4).foreach { i =>
              val d = Character.digit(text.charAt(at + i), 16)
              if (d < 0) fail()
              code = code * 16 + d
            }
            at += 4
            out.append(code.toChar)
          case other => out.append(other)
        }
      }
    }
    out.toString
  }
}
