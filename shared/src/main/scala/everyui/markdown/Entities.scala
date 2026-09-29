package everyui.markdown

import everyui.render.Platform

/** HTML's character references in Markdown text: `&amp;`, `&#35;`, `&#x22;`, and the named ones writing uses most.
  * A name not known here stays as it was written. */
private[markdown] object Entities {
  private val named: Map[String, String] = Map(
    "amp" -> "&", "lt" -> "<", "gt" -> ">", "quot" -> "\"", "apos" -> "'", "nbsp" -> "\u00a0", "copy" -> "\u00a9",
    "reg" -> "\u00ae", "trade" -> "\u2122", "hellip" -> "\u2026", "mdash" -> "\u2014", "ndash" -> "\u2013",
    "lsquo" -> "\u2018", "rsquo" -> "\u2019", "sbquo" -> "\u201a", "ldquo" -> "\u201c", "rdquo" -> "\u201d",
    "bdquo" -> "\u201e", "laquo" -> "\u00ab", "raquo" -> "\u00bb", "lsaquo" -> "\u2039", "rsaquo" -> "\u203a",
    "bull" -> "\u2022", "middot" -> "\u00b7", "deg" -> "\u00b0", "plusmn" -> "\u00b1", "times" -> "\u00d7",
    "divide" -> "\u00f7", "minus" -> "\u2212", "ne" -> "\u2260", "le" -> "\u2264", "ge" -> "\u2265",
    "asymp" -> "\u2248", "infin" -> "\u221e", "sum" -> "\u2211", "prod" -> "\u220f", "radic" -> "\u221a",
    "part" -> "\u2202", "micro" -> "\u00b5", "pi" -> "\u03c0", "alpha" -> "\u03b1", "beta" -> "\u03b2",
    "gamma" -> "\u03b3", "delta" -> "\u03b4", "lambda" -> "\u03bb", "mu" -> "\u03bc", "sigma" -> "\u03c3",
    "omega" -> "\u03c9", "Delta" -> "\u0394", "Omega" -> "\u03a9", "frac12" -> "\u00bd", "frac14" -> "\u00bc",
    "frac34" -> "\u00be", "sup2" -> "\u00b2", "sup3" -> "\u00b3", "euro" -> "\u20ac", "pound" -> "\u00a3",
    "yen" -> "\u00a5", "cent" -> "\u00a2", "curren" -> "\u00a4", "sect" -> "\u00a7", "para" -> "\u00b6",
    "dagger" -> "\u2020", "Dagger" -> "\u2021", "permil" -> "\u2030", "prime" -> "\u2032", "Prime" -> "\u2033",
    "larr" -> "\u2190", "rarr" -> "\u2192", "uarr" -> "\u2191", "darr" -> "\u2193", "harr" -> "\u2194",
    "lArr" -> "\u21d0", "rArr" -> "\u21d2", "hArr" -> "\u21d4", "hearts" -> "\u2665", "spades" -> "\u2660",
    "clubs" -> "\u2663", "diams" -> "\u2666", "check" -> "\u2713", "cross" -> "\u2717", "star" -> "\u2606",
    "iexcl" -> "\u00a1", "iquest" -> "\u00bf", "shy" -> "\u00ad", "ensp" -> "\u2002", "emsp" -> "\u2003",
    "thinsp" -> "\u2009", "zwnj" -> "\u200c", "zwj" -> "\u200d", "auml" -> "\u00e4", "ouml" -> "\u00f6",
    "uuml" -> "\u00fc", "Auml" -> "\u00c4", "Ouml" -> "\u00d6", "Uuml" -> "\u00dc", "szlig" -> "\u00df",
    "aacute" -> "\u00e1", "eacute" -> "\u00e9", "iacute" -> "\u00ed", "oacute" -> "\u00f3", "uacute" -> "\u00fa",
    "agrave" -> "\u00e0", "egrave" -> "\u00e8", "ecirc" -> "\u00ea", "ccedil" -> "\u00e7", "ntilde" -> "\u00f1",
    "Eacute" -> "\u00c9", "oslash" -> "\u00f8", "aring" -> "\u00e5", "aelig" -> "\u00e6", "AElig" -> "\u00c6")

  /** The reference `s` starts with at `at` (`&` there), as the text it stands for and how long it was, or ("", 0). */
  def at(s: String, at: Int): (String, Int) = {
    val semi = s.indexOf(";", at)
    if (semi < 0 || semi - at > 33 || semi - at < 2) ("", 0)
    else {
      val body = s.substring(at + 1, semi)
      if (body.charAt(0) == '#') {
        val hex = body.length > 1 && (body.charAt(1) == 'x' || body.charAt(1) == 'X')
        val digits = body.substring(if (hex) 2 else 1)
        val code = number(digits, hex)
        if (code < 0) ("", 0)
        else (Platform.fromCodePoint(if (code == 0 || code > 0x10ffff || (code >= 0xd800 && code <= 0xdfff)) 0xfffd
          else code), semi + 1 - at)
      }
      else named.get(body) match {
        case Some(t) => (t, semi + 1 - at)
        case None => ("", 0)
      }
    }
  }

  /** `digits` as a number, in hexadecimal with `hex`; -1 where they are not one (none, too many, or not digits). */
  private def number(digits: String, hex: Boolean): Int =
    if (digits.isEmpty || digits.length > (if (hex) 6 else 7)) -1
    else {
      var n = 0
      var ok = true
      var k = 0
      while (k < digits.length) {
        val c = digits.charAt(k)
        val v =
          if (c >= '0' && c <= '9') c - '0'
          else if (hex && c >= 'a' && c <= 'f') c - 'a' + 10
          else if (hex && c >= 'A' && c <= 'F') c - 'A' + 10
          else -1
        if (v < 0) ok = false else n = n * (if (hex) 16 else 10) + v
        k += 1
      }
      if (ok) n else -1
    }

  /** Every reference in `s` replaced by what it stands for. */
  def decode(s: String): String =
    if (s.indexOf("&") < 0) s
    else {
      val out = new StringBuilder
      var k = 0
      while (k < s.length) {
        if (s.charAt(k) == '&') {
          val (text, length) = at(s, k)
          if (length > 0) {
            out.append(text)
            k += length
          }
          else {
            out.append("&")
            k += 1
          }
        }
        else {
          out.append(s.charAt(k))
          k += 1
        }
      }
      out.toString
    }
}
