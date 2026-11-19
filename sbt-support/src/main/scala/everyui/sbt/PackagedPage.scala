package everyui.sbt

/** A web bundle's page made to run from its own files (`file://`), as a TV's package runs it (`WebOSPackage`; Tizen's
  * the same), where it is served by no one:
  *
  *  - a `<base href>` becomes `./`, and an address from the site's root (`src="/main.js"`) is made relative: from
  *    `file://` either would name the disk's root, and the page would stay blank;
  *  - the font preloads go (`<link rel="preload" as="font" crossorigin>`): from `file://` the preload's credentials
  *    mode never matches the face's own fetch, so each is fetched twice and warned of. The faces load from their
  *    `@font-face` rules as before, and the runtime waits for those at the start (`document.fonts.load`), as it does
  *    with preloads. */
object PackagedPage {
  private val base = """(?i)<base\b[^>]*>""".r
  private val preload = """(?i)[ \t]*<link\b(?=[^>]*\brel\s*=\s*["']?preload\b)(?=[^>]*\bas\s*=\s*["']?font\b)[^>]*>[ \t]*\r?\n?""".r
  private val rooted = """(?i)\b(src|href)\s*=\s*(["'])/(?!/)""".r

  def local(html: String): String = {
    val noPreloads = preload.replaceAllIn(html, "")
    val based = base.replaceAllIn(noPreloads, """<base href="./">""")
    rooted.replaceAllIn(based, m => scala.util.matching.Regex.quoteReplacement(m.group(1) + "=" + m.group(2)))
  }
}
