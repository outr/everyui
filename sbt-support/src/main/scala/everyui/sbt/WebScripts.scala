package everyui.sbt

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, StandardCopyOption}
import java.security.MessageDigest

/** An EveryUI web app's linked scripts, as `everyuiWebScripts` gives them for its `everyuiWebTarget`: the JavaScript
  * build's `main.js` (`jsModule` when it was linked as an ES module), the WebAssembly build's directory (main.js,
  * __loader.js, main.wasm), or both. From the app's bundle task:
  *
  * {{{
  *   everyuiWebScripts.value.writeTo(out, baseDirectory.value / "index.html")
  * }}}
  *
  * The page loads the app with `<script src="main.js"></script>` (any directory before it, `/main.js` too, and any
  * other attributes, such as `defer`, which the tag that replaces it keeps). Each script is named by a hash of its
  * contents, so a new build is a new URL no browser serves from its cache. The page's viewport (`<meta
  * name="viewport">`) is moved ahead of its scripts, and `WebScripts.viewport` written at the top of its head where it
  * has none: a phone lays a page out 980 pixels wide until it has read one, and an app started on that width is laid
  * out for a laptop. Plain Scala, for an sbt build to compile in (CONSUMING.md). */
case class WebScripts(js: Option[File], jsModule: Boolean, wasm: Option[File]) {
  /** Writes the scripts into `out` and `page` as `out/index.html`, its main.js script replaced by what loads them:
    * main.js itself for JS alone, `everyui-loader.js` (WasmLoader) for WebAssembly, with or without main.js to fall
    * down to. Answers a hash of what the page now loads, which names this build (`WebApp.write`'s `version`). */
  def writeTo(out: File, page: File): String = {
    out.mkdirs()
    val html = WebScripts.viewportFirst(new String(Files.readAllBytes(page.toPath), UTF_8))
    val m = WebScripts.mainScript.findFirstMatchIn(html)
      .getOrElse(sys.error(s"$page has no <script src=\".../main.js\"></script> to load the app with"))
    val prefix = m.group(2)
    // the tag's other attributes (defer, async, …) stay; whether it is a module is this build's to say
    val attributes = (m.group(1) + m.group(3)).replaceAll("""\s+type="module"""", "")
    val jsSrc = js.map { f =>
      Files.copy(f.toPath, new File(out, "main.js").toPath, StandardCopyOption.REPLACE_EXISTING)
      s"${prefix}main.js?v=${WebScripts.hash(Files.readAllBytes(f.toPath))}"
    }
    val tag = wasm match {
      case None =>
        val src = jsSrc.getOrElse(sys.error("WebScripts with neither a JS nor a WebAssembly build"))
        s"""<script$attributes${if (jsModule) " type=\"module\"" else ""} src="$src"></script>"""
      case Some(dir) =>
        // The WebAssembly build has a directory of its own: its main.js imports __loader.js and fetches main.wasm
        // beside itself. Source maps stay behind; a browser asks for them only with its developer tools open.
        val files = dir.listFiles().filter(f => f.isFile && !f.getName.endsWith(".map")).sortBy(_.getName)
        val version = WebScripts.hash(files.flatMap(f => Files.readAllBytes(f.toPath)))
        val wasmDir = new File(out, s"wasm-$version")
        wasmDir.mkdirs()
        files.foreach(f => Files.copy(f.toPath, new File(wasmDir, f.getName).toPath, StandardCopyOption.REPLACE_EXISTING))
        val loader = WasmLoader.script(jsSrc.map(_ -> jsModule), s"${prefix}wasm-$version/main.js", version)
        Files.write(new File(out, WasmLoader.fileName).toPath, loader.getBytes(UTF_8))
        s"""<script$attributes src="$prefix${WasmLoader.fileName}?v=${WebScripts.hash(loader.getBytes(UTF_8))}"></script>"""
    }
    Files.write(new File(out, "index.html").toPath, (html.substring(0, m.start) + tag + html.substring(m.end)).getBytes(UTF_8))
    WebScripts.hash(tag.getBytes(UTF_8))
  }
}

object WebScripts {
  /** main.js's script tag: its URL may carry a directory and a query (`/main.js?v=…`), and the tag's other
    * attributes before and after it. */
  private val mainScript = """<script([^>]*?)\s+src="([^"]*?)main\.js(?:\?[^"]*)?"([^>]*)>\s*</script>""".r

  /** The viewport written into a page that has none: the device's own width, at its own scale. */
  val viewport: String = """<meta name="viewport" content="width=device-width, initial-scale=1">"""
  private val viewportTag = """(?i)<meta\s[^>]*name\s*=\s*["']?viewport(?=["'\s/>])[^>]*>""".r
  private val headTag = """(?i)<head(\s[^>]*)?>""".r
  private val scriptTag = """(?i)<script\b""".r

  /** `html` with its viewport ahead of every script: as it is where it already is, else its own (or `viewport`, where
    * it has none) moved to the top of its head, or where it has no head, just before its first script. */
  def viewportFirst(html: String): String = {
    val found = viewportTag.findFirstMatchIn(html)
    val firstScript = scriptTag.findFirstMatchIn(html).fold(html.length)(_.start)
    if (found.exists(_.start < firstScript)) html
    else {
      val rest = found.fold(html)(m => html.substring(0, m.start) + html.substring(m.end))
      val meta = found.fold(viewport)(_.matched)
      val at = headTag.findFirstMatchIn(rest).map(_.end)
        .getOrElse(scriptTag.findFirstMatchIn(rest).fold(rest.length)(_.start))
      rest.substring(0, at) + "\n  " + meta + rest.substring(at)
    }
  }

  /** The first twelve hex digits of a SHA-1. */
  def hash(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-1").digest(bytes).map(b => f"${b & 0xff}%02x").mkString.take(12)
}
