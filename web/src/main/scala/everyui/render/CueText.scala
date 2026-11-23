package everyui.render

import everyui.text.TextRun

/** A WebVTT cue's words as the page draws them where EveryUI draws the subtitles ([[WebCues]]), plain Scala (so the
  * desktop's specs read it as the page does): `<i>`, `<b>` and `<u>` (and SubRip's, which pass into WebVTT as they
  * are) as italic, bold and underlined runs, a class of colour (`<c.yellow>`) or SubRip's `<font color>` as the run's
  * colour, a voice (`<v Name>`), a language, a class and ruby kept as their words (a ruby's reading, `<rt>`, left out),
  * a timestamp and any other tag dropped, the entities read, and each line break a `\n` in its run. A SubStation
  * override that SubRip carries (`{\an8}`) is taken out, and its place said by [[anchor]]. */
object CueText {
  /** The colours WebVTT names as classes. */
  val colours: Map[String, String] = Map("white" -> "#ffffff", "lime" -> "#00ff00", "cyan" -> "#00ffff",
    "red" -> "#ff0000", "yellow" -> "#ffff00", "magenta" -> "#ff00ff", "blue" -> "#0000ff", "black" -> "#000000")

  /** The cue's words as runs, each run's style the tags open around it. */
  def runs(text: String): List[TextRun] = {
    val out = List.newBuilder[TextRun]
    val words = new StringBuilder
    // the tags open, innermost first, each with the run it styles
    var open: List[(String, TextRun => TextRun)] = Nil
    def style: TextRun = open.foldRight(TextRun(""))((t, r) => t._2(r))
    // inside a ruby's reading, which is left out
    def reading: Boolean = open.exists(_._1 == "rt")
    def flush(): Unit = if (words.nonEmpty) {
      out += style.copy(text = words.toString)
      words.clear()
    }
    val clean = overrides.replaceAllIn(text.replace("\r\n", "\n").replace('\r', '\n'), "")
    var i = 0
    while (i < clean.length) {
      val c = clean.charAt(i)
      if (c == '<' && clean.indexOf('>', i) > i) {
        val end = clean.indexOf('>', i)
        val tag = clean.substring(i + 1, end).trim
        i = end + 1
        flush()
        if (tag.startsWith("/")) {
          val name = tag.drop(1).trim.toLowerCase
          val at = open.indexWhere(_._1 == name)
          if (at >= 0) open = open.drop(at + 1)
        }
        else {
          val name = tag.takeWhile(ch => ch != '.' && ch != ' ' && ch != '\t').toLowerCase
          val classes = tag.drop(name.length).takeWhile(ch => ch != ' ' && ch != '\t').split('.').toList
            .filter(_.nonEmpty)
          val coloured: TextRun => TextRun = classes.flatMap(colours.get).headOption
            .fold((r: TextRun) => r)(hex => (r: TextRun) => r.copy(color = hex))
          name match {
            case "i" => open = ("i", (r: TextRun) => coloured(r.copy(italic = true))) :: open
            case "b" => open = ("b", (r: TextRun) => coloured(r.copy(weight = 700))) :: open
            case "u" => open = ("u", (r: TextRun) => coloured(r.copy(underline = true))) :: open
            case "font" =>
              open = ("font", fontColour(tag).fold(coloured)(hex => (r: TextRun) => r.copy(color = hex))) :: open
            case "c" | "v" | "lang" | "ruby" | "rt" | "span" => open = (name, coloured) :: open
            case _ => ()
          }
        }
      }
      else if (c == '&') {
        val end = clean.indexOf(';', i)
        val named = if (end > i && end - i <= 10) entity(clean.substring(i + 1, end)) else None
        named match {
          case Some(s) =>
            if (!reading) words.append(s)
            i = end + 1
          case None =>
            if (!reading) words.append(c)
            i += 1
        }
      }
      else {
        if (!reading) words.append(c)
        i += 1
      }
    }
    flush()
    TextRun.merged(out.result())
  }

  /** The words alone, as they read. */
  def plain(text: String): String = runs(text).map(_.text).mkString

  /** Where a SubStation override SubRip carries puts the cue, as a numeric keypad: `{\an8}` is 8, top centre; 2, the
    * foot of the picture, where none says. */
  def anchor(text: String): Int =
    "\\{[^}]*\\\\an([1-9])".r.findFirstMatchIn(text).map(_.group(1).toInt).getOrElse(2)

  // a SubStation override block, `{\an8}` or `{\i1}`, which SubRip files carry and a browser shows as words
  private val overrides = "\\{\\\\[^}]*\\}".r

  private def fontColour(tag: String): Option[String] =
    "(?i)color\\s*=\\s*[\"']?(#?[0-9a-zA-Z]+)".r.findFirstMatchIn(tag).map(_.group(1)).flatMap { c =>
      if (c.startsWith("#") && (c.length == 7 || c.length == 4)) Some(c.toLowerCase)
      else colours.get(c.toLowerCase)
    }

  private def entity(name: String): Option[String] = name match {
    case "amp" => Some("&")
    case "lt" => Some("<")
    case "gt" => Some(">")
    case "quot" => Some("\"")
    case "apos" => Some("'")
    case "nbsp" => Some("\u00a0")
    case "lrm" => Some("\u200e")
    case "rlm" => Some("\u200f")
    case n if n.startsWith("#x") || n.startsWith("#X") =>
      scala.util.Try(Integer.parseInt(n.drop(2), 16)).toOption.filter(Character.isValidCodePoint)
        .map(cp => new String(Character.toChars(cp)))
    case n if n.startsWith("#") =>
      n.drop(1).toIntOption.filter(Character.isValidCodePoint).map(cp => new String(Character.toChars(cp)))
    case _ => None
  }
}
