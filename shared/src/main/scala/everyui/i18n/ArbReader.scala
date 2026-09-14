package everyui.i18n

import everyui.render.Platform

/** A cursor over JSON text, as much of it as an ARB file needs: strings with their escapes, and skipping values. Only
  * substrings and comparisons, so every backend's compiler carries it. */
class ArbReader(text: String) {
  private var at = 0
  private val quote = "\""
  private val backslash = "\\"

  def done: Boolean = at >= text.length
  /** The character here, as a one-character string ("" at the end). */
  def peek(): String = if (done) "" else text.substring(at, at + 1)
  def skip(): Unit = at += 1

  def space(): Unit = while (!done && (peek() == " " || peek() == "\n" || peek() == "\r" || peek() == "\t")) at += 1

  /** A string, at its opening quote, with its escapes resolved. */
  def string(): String = {
    var out = ""
    at += 1
    var closed = false
    while (!closed && !done) {
      val c = peek()
      if (c == quote) {
        closed = true
        at += 1
      }
      else if (c == backslash && at + 1 < text.length) {
        val e = text.substring(at + 1, at + 2)
        if (e == "n") out = out + "\n"
        else if (e == "t") out = out + "\t"
        else if (e == "r") out = out + "\r"
        else if (e == "u" && at + 5 < text.length) {
          out = out + Platform.fromCodePoint(ArbReader.hex(text.substring(at + 2, at + 6)))
          at += 4
        }
        else out = out + e
        at += 2
      }
      else {
        // the run up to the next quote or backslash in one piece
        var end = at + 1
        while (end < text.length && text.substring(end, end + 1) != quote && text.substring(end, end + 1) != backslash) end += 1
        out = out + text.substring(at, end)
        at = end
      }
    }
    out
  }

  /** Past whatever value starts here: a string, an object or array (with everything in it), or a bare word. */
  def value(): Unit = {
    val c = peek()
    if (c == quote) string()
    else if (c == "{" || c == "[") {
      var depth = 0
      var going = true
      while (going && !done) {
        val d = peek()
        if (d == quote) string()
        else {
          if (d == "{" || d == "[") depth += 1
          else if (d == "}" || d == "]") depth -= 1
          at += 1
          if (depth == 0) going = false
        }
      }
    }
    else while (!done && peek() != "," && peek() != "}" && peek() != "]") at += 1
  }
}

object ArbReader {
  /** Four hex digits as a number. */
  def hex(digits: String): Int = {
    var n = 0
    var i = 0
    while (i < digits.length) {
      n = n * 16 + "0123456789abcdef".indexOf(digits.substring(i, i + 1).toLowerCase)
      i += 1
    }
    n
  }
}
