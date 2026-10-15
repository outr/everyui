package everyui.i18n

/** The part of ICU MessageFormat app translations use: `{name}` placeholders, `{n, plural, =0{…} =1{…} one{…}
  * other{…}}` (a `#` inside is the number) and `{choice, select, a{…} other{…}}`, nested to any depth. A plural's
  * categories are the current language's (`Strings.language`): `one` for 1 (and 0 in French and Portuguese), none in
  * Chinese, and Arabic's `zero`, `one`, `two`, `few` (3 to 10) and `many` (11 to 99, by the last two digits). Only
  * substrings and comparisons, so every backend's compiler carries it. */
object Icu {
  def format(template: String, args: Map[String, String]): String = {
    var out = ""
    var i = 0
    while (i < template.length) {
      val open = Icu.find(template, "{", i)
      if (open < 0) {
        out = out + template.substring(i)
        i = template.length
      }
      else {
        out = out + template.substring(i, open)
        val end = closing(template, open)
        if (end < 0) {
          out = out + template.substring(open)
          i = template.length
        }
        else {
          out = out + argument(template.substring(open + 1, end), args)
          i = end + 1
        }
      }
    }
    out
  }

  /** The index of `s` in `text` at or after `from`, or -1. */
  def find(text: String, s: String, from: Int): Int = {
    var i = from
    var found = -1
    while (found < 0 && i + s.length <= text.length) {
      if (text.substring(i, i + s.length) == s) found = i
      i += 1
    }
    found
  }

  /** The index of the `}` closing the `{` at `open`, or -1. */
  private def closing(text: String, open: Int): Int = {
    var depth = 0
    var i = open
    var found = -1
    while (found < 0 && i < text.length) {
      val c = text.substring(i, i + 1)
      if (c == "{") depth += 1
      else if (c == "}") {
        depth -= 1
        if (depth == 0) found = i
      }
      i += 1
    }
    found
  }

  /** One `{…}`: a plain placeholder, or a plural or select with its cases. */
  private def argument(inner: String, args: Map[String, String]): String = {
    val comma = inner.indexOf(",")
    if (comma < 0) args.getOrElse(inner.trim, "{" + inner + "}")
    else {
      val name = inner.substring(0, comma).trim
      val rest = inner.substring(comma + 1)
      val second = rest.indexOf(",")
      val kind = (if (second < 0) rest else rest.substring(0, second)).trim
      val value = args.getOrElse(name, "").trim
      val options = if (second < 0) Map.empty[String, String] else cases(rest.substring(second + 1))
      if (kind == "plural") {
        val chosen = options.get("=" + value).orElse(options.get(category(value)))
          .orElse(options.get("other")).getOrElse("")
        format(chosen.replace("#", value), args)
      }
      else if (kind == "select") format(options.get(value).orElse(options.get("other")).getOrElse(""), args)
      else value
    }
  }

  /** The plural category a count falls in, in the current language: "other" for anything not a whole number. */
  private def category(value: String): String = {
    val code = Strings.language().code
    val language = if (code.contains("_")) code.substring(0, code.indexOf("_")) else code
    var whole = value.nonEmpty && value.length < 10
    var i = 0
    while (whole && i < value.length) {
      if ("0123456789".indexOf(value.substring(i, i + 1)) < 0) whole = false
      i += 1
    }
    val n = if (whole) value.toInt else -1
    if (n < 0 || language == "zh") "other"
    else if (language == "ar") {
      val last = n % 100
      if (n == 0) "zero" else if (n == 1) "one" else if (n == 2) "two"
      else if (last >= 3 && last <= 10) "few" else if (last >= 11) "many" else "other"
    }
    else if (n == 1 || (n == 0 && (language == "fr" || language == "pt"))) "one"
    else "other"
  }

  /** `key{text} key{text}` as a map. */
  private def cases(text: String): Map[String, String] = {
    var found = Map.empty[String, String]
    var i = 0
    while (i < text.length) {
      val open = Icu.find(text, "{", i)
      if (open < 0) i = text.length
      else {
        val end = closing(text, open)
        if (end < 0) i = text.length
        else {
          found = found + (text.substring(i, open).trim -> text.substring(open + 1, end))
          i = end + 1
        }
      }
    }
    found
  }
}
