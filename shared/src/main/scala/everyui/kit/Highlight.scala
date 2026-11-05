package everyui.kit

import everyui.text.TextRun
import everyui.theme.Theme

/** Code coloured by a small lexer shared by the common languages: keywords, strings, comments, numbers and type names,
  * each a colour of the theme (`Theme.extra("code.keyword", …)` and the like override them: "code.keyword",
  * "code.string", "code.comment", "code.number", "code.type"). It reads the text once, left to right, without
  * parsing it, so a snippet still being written colours as well as it can. A diff ("diff", "patch") is coloured by
  * its lines instead: added, taken out, a hunk's header and the files' names (`diffRuns`).
  *
  * {{{
  *   new RichText { whiteSpace @= WhiteSpace.Pre; runs := Highlight.runs(source(), "scala") }
  * }}} */
object Highlight {
  private val cLike: Set[String] = Set("if", "else", "for", "while", "do", "return", "break", "continue", "switch",
    "case", "default", "new", "class", "interface", "enum", "extends", "implements", "import", "package", "public",
    "private", "protected", "static", "final", "abstract", "const", "try", "catch", "finally", "throw", "throws",
    "this", "super", "null", "true", "false", "void", "int", "long", "double", "float", "boolean", "char", "byte",
    "short", "var", "let", "function", "async", "await", "yield", "typeof", "instanceof", "in", "of", "export",
    "from", "as", "struct", "union", "typedef", "sizeof", "goto", "volatile", "extern", "unsigned", "signed", "auto",
    "namespace", "using", "template", "typename", "virtual", "override", "operator", "delete", "friend", "inline",
    "undefined", "readonly", "declare", "type", "implements", "keyof", "never", "unknown", "any", "string", "number")
  private val scalaWords: Set[String] = Set("abstract", "case", "catch", "class", "def", "do", "else", "enum", "extends",
    "export", "false", "final", "finally", "for", "given", "if", "implicit", "import", "lazy", "match", "new", "null",
    "object", "override", "package", "private", "protected", "return", "sealed", "super", "then", "this", "throw",
    "trait", "true", "try", "type", "using", "val", "var", "while", "with", "yield", "extension", "inline", "opaque",
    "fun", "when", "is", "companion", "data", "suspend", "internal", "open", "constructor", "init")
  private val pythonWords: Set[String] = Set("and", "as", "assert", "async", "await", "break", "class", "continue", "def",
    "del", "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
    "None", "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while", "with", "yield", "self",
    "match", "case", "print")
  private val rustWords: Set[String] = Set("as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
    "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod", "move", "mut", "pub",
    "ref", "return", "self", "Self", "static", "struct", "super", "trait", "true", "type", "unsafe", "use", "where",
    "while", "func", "go", "chan", "defer", "select", "package", "import", "var", "range", "map", "nil", "interface",
    "fallthrough", "default", "case", "switch")
  private val shellWords: Set[String] = Set("if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case",
    "esac", "in", "function", "return", "export", "local", "echo", "cd", "sudo", "set", "unset", "source", "exit")
  private val sqlWords: Set[String] = Set("select", "from", "where", "and", "or", "not", "insert", "into", "values",
    "update", "set", "delete", "create", "table", "drop", "alter", "index", "join", "left", "right", "inner", "outer",
    "on", "group", "by", "order", "having", "limit", "offset", "as", "distinct", "null", "is", "in", "like", "union",
    "primary", "key", "foreign", "references", "default", "case", "when", "then", "else", "end", "with", "count")
  private val jsonWords: Set[String] = Set("true", "false", "null")

  /** The keywords of `language` (a fence's word: "scala", "js", "py", …), and whether `#` starts a comment there. */
  private def words(language: String): Set[String] = language.toLowerCase match {
    case "scala" | "sc" | "kotlin" | "kt" | "kts" => scalaWords
    case "python" | "py" => pythonWords
    case "rust" | "rs" | "go" | "golang" => rustWords
    case "bash" | "sh" | "shell" | "zsh" | "fish" | "console" => shellWords
    case "sql" => sqlWords
    case "json" | "yaml" | "yml" | "toml" => jsonWords
    case "" | "text" | "txt" | "plain" | "markdown" | "md" => Set.empty
    case _ => cLike
  }

  private def hashComments(language: String): Boolean = language.toLowerCase match {
    case "python" | "py" | "bash" | "sh" | "shell" | "zsh" | "fish" | "console" | "yaml" | "yml" | "toml" | "ruby" |
         "rb" | "r" | "perl" | "dockerfile" | "makefile" | "conf" | "ini" => true
    case _ => false
  }

  /** `code` as runs, coloured for `language`; one plain run where the language is none or not known to be code. */
  def runs(code: String, language: String): List[TextRun] = {
    val known = words(language)
    if (known.isEmpty && language.trim.isEmpty) List(TextRun(code))
    else if (diff(language)) diffRuns(code)
    else {
      val keyword = Theme.extra("code.keyword", Theme.accent()).toHex
      val string = Theme.extra("code.string", Theme.success()).toHex
      val comment = Theme.extra("code.comment", Theme.muted()).toHex
      val number = Theme.extra("code.number", Theme.warning()).toHex
      val kind = Theme.extra("code.type", Theme.info()).toHex
      val hash = hashComments(language)
      val sql = language.toLowerCase == "sql"
      var out: List[TextRun] = Nil
      // where the uncoloured stretch being read began, -1 for none
      var plain = -1
      var k = 0
      while (k < code.length) {
        val c = code.charAt(k)
        val end =
          if ((c == '/' && k + 1 < code.length && code.charAt(k + 1) == '/') || (hash && c == '#') ||
            (sql && c == '-' && k + 1 < code.length && code.charAt(k + 1) == '-')) lineEnd(code, k)
          else if (c == '/' && k + 1 < code.length && code.charAt(k + 1) == '*') blockEnd(code, k)
          else if (c == '"' || c == '\'' || c == '`') quoteEnd(code, k)
          else if (c.isDigit && (k == 0 || !identifier(code.charAt(k - 1)))) numberEnd(code, k)
          else if (identifier(c) && !c.isDigit && (k == 0 || !identifier(code.charAt(k - 1)))) wordEnd(code, k)
          else -1
        if (end < 0) {
          if (plain < 0) plain = k
          k += 1
        }
        else {
          val token = code.substring(k, end)
          val colour =
            if (c == '/' || c == '#' || c == '-') comment
            else if (c == '"' || c == '\'' || c == '`') string
            else if (c.isDigit) number
            else if (known.contains(token) || (sql && known.contains(token.toLowerCase))) keyword
            else if (c.isUpper && token.length > 1) kind
            else ""
          if (colour.isEmpty) {
            if (plain < 0) plain = k
          }
          else {
            if (plain >= 0) {
              out = TextRun(code.substring(plain, k)) :: out
              plain = -1
            }
            out = TextRun(token, color = colour) :: out
          }
          k = end
        }
      }
      if (plain >= 0) out = TextRun(code.substring(plain)) :: out
      out.reverse
    }
  }

  /** A diff's lines as runs (`language` "diff" or "patch"): what was added in the theme's success, what was taken out
    * in its danger, a hunk's `@@` header muted, and the lines naming the files (`diff --git`, `---`, `+++`, `index`,
    * a file's mode) bold; the rest, the lines around a change, plain. Each line's colour is the theme's "code.added",
    * "code.removed" and "code.hunk" where it has them. */
  private def diffRuns(code: String): List[TextRun] = {
    val added = Theme.extra("code.added", Theme.success()).toHex
    val removed = Theme.extra("code.removed", Theme.danger()).toHex
    val hunk = Theme.extra("code.hunk", Theme.muted()).toHex
    var out: List[TextRun] = Nil
    // where the plain stretch being read began, -1 for none
    var plain = -1
    var k = 0
    while (k < code.length) {
      val nl = code.indexOf("\n", k)
      val end = if (nl < 0) code.length else nl + 1
      val line = code.substring(k, end)
      val file = line.startsWith("diff ") || line.startsWith("--- ") || line.startsWith("+++ ") ||
        line.startsWith("index ") || line.startsWith("new file mode") || line.startsWith("deleted file mode") ||
        line.startsWith("old mode") || line.startsWith("new mode") || line.startsWith("rename ") ||
        line.startsWith("similarity index")
      val run =
        if (file) Some(TextRun(line, weight = 700))
        else if (line.startsWith("@@")) Some(TextRun(line, color = hunk))
        else if (line.startsWith("+")) Some(TextRun(line, color = added))
        else if (line.startsWith("-")) Some(TextRun(line, color = removed))
        else None
      run match {
        case Some(r) =>
          if (plain >= 0) {
            out = TextRun(code.substring(plain, k)) :: out
            plain = -1
          }
          out = r :: out
        case None => if (plain < 0) plain = k
      }
      k = end
    }
    if (plain >= 0) out = TextRun(code.substring(plain)) :: out
    out.reverse
  }

  private def diff(language: String): Boolean = {
    val l = language.toLowerCase
    l == "diff" || l == "patch"
  }

  private def identifier(c: Char): Boolean = c.isLetterOrDigit || c == '_' || c == '$'

  private def lineEnd(code: String, from: Int): Int = {
    val nl = code.indexOf("\n", from)
    if (nl < 0) code.length else nl
  }

  private def blockEnd(code: String, from: Int): Int = {
    val close = code.indexOf("*/", from + 2)
    if (close < 0) code.length else close + 2
  }

  /** The end of the string opening at `from`: its closing quote (a backslash escapes one), a triple quote's triple, or
    * the line's end for an unclosed one. */
  private def quoteEnd(code: String, from: Int): Int = {
    val q = code.charAt(from)
    val three = q.toString + q.toString + q.toString
    if (code.startsWith(three, from)) {
      val close = code.indexOf(three, from + 3)
      if (close < 0) code.length else close + 3
    }
    else {
      var k = from + 1
      var end = -1
      while (end < 0 && k < code.length) {
        val c = code.charAt(k)
        if (c == '\\') k += 2
        else if (c == q) end = k + 1
        else if (c == '\n' && q != '`') end = k
        else k += 1
      }
      if (end < 0) code.length else end
    }
  }

  private def numberEnd(code: String, from: Int): Int = {
    var k = from
    while (k < code.length && (code.charAt(k).isLetterOrDigit || code.charAt(k) == '.' || code.charAt(k) == '_')) k += 1
    k
  }

  private def wordEnd(code: String, from: Int): Int = {
    var k = from
    while (k < code.length && identifier(code.charAt(k))) k += 1
    k
  }
}
