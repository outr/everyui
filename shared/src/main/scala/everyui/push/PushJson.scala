package everyui.push

/** Just enough JSON for a push's buttons (`PushButton.parse`), the same on every platform: a value read as a Vector
  * (an array), a Map (an object), a String, a Double, a Boolean or null, or None where the text is not JSON. */
private[push] object PushJson {
  def parse(text: String): Option[Any] = {
    val reader = new PushJsonReader(text)
    try {
      val value = reader.value()
      reader.space()
      if (reader.done) Some(value) else None
    }
    catch { case _: IllegalArgumentException => None }
  }

  /** `s` as a JSON string, quoted. */
  def quote(s: String): String = {
    val out = new StringBuilder("\"")
    s.foreach {
      case '"' => out.append("\\\"")
      case '\\' => out.append("\\\\")
      case '\n' => out.append("\\n")
      case '\r' => out.append("\\r")
      case '\t' => out.append("\\t")
      case c if c < ' ' =>
        val hex = "0123456789abcdef"
        out.append("\\u00").append(hex.charAt(c.toInt / 16)).append(hex.charAt(c.toInt % 16))
      case c => out.append(c)
    }
    out.append('"').toString
  }
}
