package everyui.i18n

/** The part of ICU MessageFormat app translations use: `{name}` placeholders, `{n, plural, =0{…} =1{…} one{…}
  * other{…}}` (a `#` inside is the number) and `{choice, select, a{…} other{…}}`, nested to any depth. Only substrings
  * and comparisons, so every backend's compiler carries it. */
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
        val chosen = options.get("=" + value).orElse(if (value == "1") options.get("one") else None)
          .orElse(options.get("other")).getOrElse("")
        format(chosen.replace("#", value), args)
      }
      else if (kind == "select") format(options.get(value).orElse(options.get("other")).getOrElse(""), args)
      else value
    }
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
