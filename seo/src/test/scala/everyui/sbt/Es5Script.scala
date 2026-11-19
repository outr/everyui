package everyui.sbt

/** What in a script is newer than ECMAScript 5.1, the language of the oldest engine EveryUI runs on (webOS 3's
  * Chromium 38): read by its tokens, its strings, comments and regular expressions passed over, it finds arrow
  * functions, template literals, spread and rest, `let`, `const`, `class`, modules, `async` and `await`, `for … of`,
  * default parameters and destructuring. Each finding with the line it is on; none for an ES5.1 script. */
object Es5Script {
  def newer(script: String): List[String] = {
    val tokens = scan(script)
    def at(i: Int): String = if (i >= 0 && i < tokens.length) tokens(i)._1 else ""
    tokens.indices.toList.flatMap { i =>
      val (t, line) = tokens(i)
      def found(what: String) = Some(s"line $line: $what")
      t match {
        case "=>" => found("an arrow function")
        case "`" => found("a template literal")
        case "..." => found("spread or rest")
        case "let" | "const" | "class" | "import" | "export" | "async" | "await" | "yield" if at(i - 1) != "." =>
          found("`" + t + "`")
        case "of" if forHead(tokens.map(_._1), i) => found("for … of")
        case "{" | "[" if Set("var", "let", "const").contains(at(i - 1)) => found("destructuring")
        case "=" if inParameters(tokens.map(_._1), i) => found("a default parameter")
        case _ => None
      }
    }
  }

  // whether token i is in the head of a `for (`, its parentheses not yet closed
  private def forHead(t: IndexedSeq[String], i: Int): Boolean = {
    var depth = 0
    var j = i - 1
    while (j >= 0) {
      t(j) match {
        case ")" => depth += 1
        case "(" if depth == 0 => return j > 0 && t(j - 1) == "for"
        case "(" => depth -= 1
        case _ => ()
      }
      j -= 1
    }
    false
  }

  // whether token i is directly in a function's parameter list
  private def inParameters(t: IndexedSeq[String], i: Int): Boolean = {
    var depth = 0
    var j = i - 1
    while (j >= 0) {
      t(j) match {
        case ")" | "]" | "}" => depth += 1
        case "(" if depth == 0 =>
          return (j > 0 && t(j - 1) == "function") || (j > 1 && t(j - 2) == "function")
        case "(" | "[" | "{" if depth == 0 => return false
        case "(" | "[" | "{" => depth -= 1
        case _ => ()
      }
      j -= 1
    }
    false
  }

  // the script's tokens (words, numbers, punctuation; a template literal's backtick as one), each with its line
  private def scan(s: String): IndexedSeq[(String, Int)] = {
    val out = IndexedSeq.newBuilder[(String, Int)]
    var line = 1
    var at = 0
    var last = ""
    def add(t: String): Unit = { out += ((t, line)); last = t }
    def skipTo(end: Char): Unit = {
      while (at < s.length && s.charAt(at) != end) {
        if (s.charAt(at) == '\\') at += 1
        else if (s.charAt(at) == '\n') line += 1
        at += 1
      }
      at += 1
    }
    // a slash starts a regular expression where a value is expected, else it divides
    def regexAllowed: Boolean = last.isEmpty || "(,=:[!&|?{};+-*%<>~^".contains(last.last) ||
      Set("return", "typeof", "case", "do", "else", "in", "instanceof", "new", "delete", "void", "throw").contains(last)
    while (at < s.length) {
      val c = s.charAt(at)
      if (c == '\n') { line += 1; at += 1 }
      else if (c.isWhitespace) at += 1
      else if (s.startsWith("//", at)) { while (at < s.length && s.charAt(at) != '\n') at += 1 }
      else if (s.startsWith("/*", at)) {
        val end = s.indexOf("*/", at + 2)
        val stop = if (end < 0) s.length else end + 2
        line += s.substring(at, stop).count(_ == '\n')
        at = stop
      }
      else if (c == '"' || c == '\'') { at += 1; skipTo(c); add("\"\"") }
      else if (c == '`') { add("`"); at += 1; skipTo('`') }
      else if (c == '/' && regexAllowed) {
        at += 1
        var inClass = false
        while (at < s.length && (s.charAt(at) != '/' || inClass) && s.charAt(at) != '\n') {
          if (s.charAt(at) == '\\') at += 1
          else if (s.charAt(at) == '[') inClass = true
          else if (s.charAt(at) == ']') inClass = false
          at += 1
        }
        at += 1
        while (at < s.length && s.charAt(at).isLetter) at += 1
        add("/re/")
      }
      else if (c.isLetterOrDigit || c == '_' || c == '$') {
        val start = at
        while (at < s.length && (s.charAt(at).isLetterOrDigit || s.charAt(at) == '_' || s.charAt(at) == '$')) at += 1
        add(s.substring(start, at))
      }
      else {
        val three = Set("...", "===", "!==", ">>>")
        val two = Set("=>", "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", ">>", "<<")
        val t = if (three.exists(s.startsWith(_, at))) s.substring(at, at + 3)
          else if (two.exists(s.startsWith(_, at))) s.substring(at, at + 2) else c.toString
        add(t)
        at += t.length
      }
    }
    out.result()
  }

  /** The inline scripts of an HTML page: those with no `src`, and no type but JavaScript's. */
  def inline(html: String): List[String] =
    """(?s)<script\b([^>]*)>(.*?)</script>""".r.findAllMatchIn(html)
      .filter(m => !m.group(1).contains("src=") &&
        !"""type\s*=\s*["']?(application/ld\+json|module|importmap)""".r.findFirstIn(m.group(1)).isDefined)
      .map(_.group(2)).toList
}
