package everyui.text

import everyui.markdown.Entities

/** What a piece of HTML says, as plain text: its words with a line for each `br`, and each block (`p`, `div`, `li`,
  * `tr`, a heading…) on lines of its own, a list's items marked "• ", and `links`, each link's words with its `href`,
  * in the order they come. Script, style and the head are left out, as is every tag; character references are what
  * they stand for, and runs of white space are one space, as a browser draws them (but in `pre`). No DOM: the same on
  * every platform, for HTML from somewhere else (a calendar event's description) shown as words and buttons.
  *
  * {{{
  *   val read = HtmlText.read("Join <a href=\"https://meet.example.com/x\">the call</a><br>Agenda: TBD")
  *   read.text    // "Join the call\nAgenda: TBD"
  *   read.links   // List("the call" -> "https://meet.example.com/x")
  * }}} */
case class HtmlText(text: String, links: List[(String, String)])

object HtmlText {
  // what is never drawn, its contents with it
  private val unseen = Set("script", "style", "head", "title", "template", "noscript", "iframe", "object", "svg")
  // what stands on lines of its own
  private val blocks = Set("p", "div", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "table", "thead",
    "tbody", "tfoot", "blockquote", "section", "article", "header", "footer", "nav", "aside", "main", "pre", "dl", "dt",
    "dd", "hr", "address", "figure", "figcaption", "form", "fieldset", "details", "summary", "center")
  private val cells = Set("td", "th")

  /** `html` read as words and links. */
  def read(html: String): HtmlText = {
    val reader = new Reader(html)
    reader.run()
    HtmlText(reader.result(), reader.found.reverse)
  }

  private class Reader(html: String) {
    private val lower = html.toLowerCase
    private var lines: List[String] = Nil
    private var line: String = ""
    // a space owed before the next word, and whether a line has been ended with nothing on it (a `br` after a `br`)
    private var space = false
    private var pre = 0
    private var linkHref: Option[String] = None
    private var linkWords: String = ""
    var found: List[(String, String)] = Nil

    def run(): Unit = {
      var i = 0
      var from = 0
      while (i < html.length) {
        if (html.charAt(i) == '<' && i + 1 < html.length && opens(html.charAt(i + 1))) {
          add(html.substring(from, i))
          i = tag(i)
          from = i
        }
        else i += 1
      }
      add(html.substring(math.min(from, html.length)))
      linkEnd()
    }

    def result(): String = {
      val all = (line :: lines).reverse.map(trimEnd)
      // no more than one empty line in a row, and none at either end
      var out: List[String] = Nil
      var empty = 0
      all.foreach { l =>
        if (l.isEmpty) empty += 1 else empty = 0
        if (empty <= 1) out = l :: out
      }
      out.dropWhile(_.isEmpty).reverse.dropWhile(_.isEmpty).mkString("\n")
    }

    private def opens(c: Char): Boolean =
      c == '/' || c == '!' || c == '?' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')

    // the tag at `at` acted on, and where reading goes on after it
    private def tag(at: Int): Int =
      if (html.startsWith("<!--", at)) {
        val end = html.indexOf("-->", at + 4)
        if (end < 0) html.length else end + 3
      }
      else {
        val end = close(at)
        val body = html.substring(at + 1, math.max(at + 1, end - 1))
        val closing = body.startsWith("/")
        val name = body.stripPrefix("/").takeWhile(ch => ch.isLetterOrDigit || ch == '-').toLowerCase
        if (name.isEmpty || body.startsWith("!") || body.startsWith("?")) end
        else if (!closing && unseen.contains(name) && !body.endsWith("/")) skip(name, end)
        else {
          if (name == "br") newLine()
          else if (blocks.contains(name)) {
            breakLine()
            if (name == "pre") pre = math.max(0, pre + (if (closing) -1 else 1))
            if (name == "li" && !closing) line = "• "
          }
          else if (cells.contains(name) && !closing && line.trim.nonEmpty) space = true
          else if (name == "a") {
            if (closing) linkEnd()
            else attribute(body, "href").foreach { href =>
              linkEnd()
              linkHref = Some(href)
            }
          }
          end
        }
      }

    // just past the `>` that ends the tag at `at`, a quoted value's `>` not ending it
    private def close(at: Int): Int = {
      var i = at + 1
      var quote: Char = 0
      var done = false
      while (!done && i < html.length) {
        val c = html.charAt(i)
        if (quote != 0) { if (c == quote) quote = 0 }
        else if (c == '"' || c == '\'') quote = c
        else if (c == '>') done = true
        i += 1
      }
      i
    }

    // past the end of `name`, whose contents are never drawn
    private def skip(name: String, from: Int): Int = {
      val end = lower.indexOf("</" + name, from)
      if (end < 0) html.length else close(end)
    }

    private def attribute(body: String, name: String): Option[String] = {
      val low = body.toLowerCase
      var at = low.indexOf(name)
      var value: Option[String] = None
      while (value.isEmpty && at > 0) {
        val before = low.charAt(at - 1)
        var i = at + name.length
        while (i < body.length && body.charAt(i) == ' ') i += 1
        if ((before == ' ' || before == '\t' || before == '\n') && i < body.length && body.charAt(i) == '=') {
          i += 1
          while (i < body.length && body.charAt(i) == ' ') i += 1
          val q = if (i < body.length) body.charAt(i) else ' '
          val raw =
            if (q == '"' || q == '\'') {
              val e = body.indexOf(q, i + 1)
              body.substring(i + 1, if (e < 0) body.length else e)
            }
            else body.substring(i).takeWhile(ch => ch != ' ' && ch != '\t' && ch != '\n')
          value = Some(Entities.decode(raw).trim)
        }
        at = low.indexOf(name, at + 1)
      }
      value
    }

    private def add(raw: String): Unit = if (raw.nonEmpty) {
      val text = Entities.decode(raw).replace(' ', ' ')
      if (pre > 0) {
        val parts = text.split("\n", -1)
        parts.indices.foreach { k =>
          if (k > 0) newLine()
          put(parts(k))
        }
      }
      else {
        var k = 0
        while (k < text.length) {
          val c = text.charAt(k)
          if (c == ' ' || c == '\n' || c == '\t' || c == '\r' || c == '\f') space = true
          else {
            var e = k
            while (e < text.length && !" \n\t\r\f".contains(text.charAt(e))) e += 1
            if (space && line.nonEmpty && !line.endsWith(" ")) put(" ")
            space = false
            put(text.substring(k, e))
            k = e - 1
          }
          k += 1
        }
      }
    }

    private def put(s: String): Unit = {
      line = line + s
      if (linkHref.nonEmpty) linkWords = linkWords + s
    }

    // a `br`: the line ends, even an empty one
    private def newLine(): Unit = {
      lines = line :: lines
      line = ""
      space = false
    }

    // a block's edge: the line ends where it holds anything
    private def breakLine(): Unit = {
      if (line.trim.nonEmpty) newLine()
      else line = ""
      space = false
    }

    private def linkEnd(): Unit = linkHref.foreach { href =>
      val words = linkWords.trim
      if (href.nonEmpty) found = ((if (words.isEmpty) href else words) -> href) :: found
      linkHref = None
      linkWords = ""
    }

    private def trimEnd(s: String): String = {
      var e = s.length
      while (e > 0 && s.charAt(e - 1) == ' ') e -= 1
      s.substring(0, e)
    }
  }
}
