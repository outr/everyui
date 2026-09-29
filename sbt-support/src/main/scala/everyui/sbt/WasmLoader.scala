package everyui.sbt

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, StandardCopyOption}
import java.security.MessageDigest

/** Ships an EveryUI web app as WebAssembly, falling down to its JavaScript build where the browser cannot run it. The
  * app is linked twice, once as it always was and once to WebAssembly (an ES module, `withUseWebAssembly(true)`), and
  * from its build, after its bundle is written (index.html loading main.js):
  *
  * {{{
  *   WasmLoader.write(out, (appWasm / Compile / fullLinkJSOutput).value)
  * }}}
  *
  * It copies the WebAssembly build into `out/wasm-<hash>/` (its own directory, since its main.js imports __loader.js
  * and fetches main.wasm beside itself, and a new build is a new URL), writes `everyui-loader.js` next to main.js, and
  * puts that in the page in place of main.js's script. The loader runs the WebAssembly build where the browser has
  * what it needs (WasmGC, exception handling with exnref, the JS string builtins), the JS build otherwise or if the
  * WebAssembly build fails to start, and says in the console which it chose and why. `?wasm=false` in the page's URL
  * forces the JS build, `?wasm=true` the WebAssembly one; `window.everyuiBuild` and `Platform.build` say which runs.
  * Plain Scala, for an sbt build to compile in (CONSUMING.md). */
object WasmLoader {
  val loaderName: String = "everyui-loader.js"

  /** main.js's script tag: its URL may carry a directory and a query (`/main.js?v=…`). */
  private val mainScript = """<script\s+src="(([^"]*?)main\.js(\?[^"]*)?)"\s*>\s*</script>""".r

  def write(out: File, wasm: File): Unit = {
    val version = hash(wasm)
    val dir = new File(out, s"wasm-$version")
    dir.mkdirs()
    wasm.listFiles().filter(_.isFile).foreach { f =>
      Files.copy(f.toPath, new File(dir, f.getName).toPath, StandardCopyOption.REPLACE_EXISTING)
    }
    val page = new File(out, "index.html")
    val html = new String(Files.readAllBytes(page.toPath), UTF_8)
    val m = mainScript.findFirstMatchIn(html).getOrElse(sys.error(s"$page has no <script src=\".../main.js\"></script> for the WebAssembly loader to replace"))
    val prefix = m.group(2)
    val loader = script(js = m.group(1), wasm = s"${prefix}wasm-$version/main.js", version = version)
    Files.write(new File(out, loaderName).toPath, loader.getBytes(UTF_8))
    val tag = s"""<script src="$prefix$loaderName?v=${hash(loader.getBytes(UTF_8))}"></script>"""
    Files.write(page.toPath, (html.substring(0, m.start) + tag + html.substring(m.end)).getBytes(UTF_8))
  }

  /** The first twelve hex digits of a SHA-1 of the build's files, in name order. */
  private def hash(dir: File): String = {
    val digest = MessageDigest.getInstance("SHA-1")
    dir.listFiles().filter(_.isFile).sortBy(_.getName).foreach(f => digest.update(Files.readAllBytes(f.toPath)))
    hex(digest.digest())
  }

  private def hash(bytes: Array[Byte]): String = hex(MessageDigest.getInstance("SHA-1").digest(bytes))

  private def hex(bytes: Array[Byte]): String = bytes.map(b => f"${b & 0xff}%02x").mkString.take(12)

  /** The loader. The detection validates three tiny modules: a GC struct type, a try_table with exnref, and one that
    * imports wasm:js-string "cast" with the wrong type, which is invalid only where the builtins are known. */
  def script(js: String, wasm: String, version: String): String =
    s"""(() => {
       |  const js = ${quote(js)};
       |  const wasm = ${quote(wasm)};
       |  const say = (level, message) => console[level]("[everyui] " + message);
       |  const bytes = b64 => Uint8Array.from(atob(b64), c => c.charCodeAt(0));
       |  const valid = (b64, options) => { try { return WebAssembly.validate(bytes(b64), options); } catch (e) { return false; } };
       |
       |  function missing() {
       |    if (typeof WebAssembly !== "object" || typeof WebAssembly.validate !== "function") return "WebAssembly not supported";
       |    if (!valid("AGFzbQEAAAABCQJfAX8AYAABbgMCAQEKCQEHAEEB+wAACw==")) return "WebAssembly GC not supported";
       |    if (!valid("AGFzbQEAAAABBAFgAAADAgEADQMBAAAKEQEPAAJpH0ABAwAIAAsACxoL") || typeof WebAssembly.JSTag !== "object")
       |      return "WebAssembly exception handling (exnref) not supported";
       |    if (valid("AGFzbQEAAAABBAFgAAACFwEOd2FzbTpqcy1zdHJpbmcEY2FzdAAA", { builtins: ["js-string"] }))
       |      return "WebAssembly JS string builtins not supported";
       |    return null;
       |  }
       |
       |  function loadJs(level, why) {
       |    window.everyuiBuild = "js";
       |    say(level, "loading the JS build: " + why);
       |    const s = document.createElement("script");
       |    s.src = js;
       |    s.onload = () => say("info", "running the JS build");
       |    s.onerror = () => say("error", "the JS build failed to load too: " + js);
       |    document.body.appendChild(s);
       |  }
       |
       |  function loadWasm(why) {
       |    window.everyuiBuild = "wasm";
       |    say("info", "loading the WebAssembly build v$version: " + why);
       |    import(new URL(wasm, document.baseURI).href).then(() => say("info", "running the WebAssembly build v$version"), e => {
       |      // It failed to start (main.wasm missing or damaged, an import the engine lacks): whatever it put on the page
       |      // before it threw is cleared, and the JS build starts on a clean page.
       |      const app = document.getElementById("app");
       |      if (app) app.replaceChildren();
       |      loadJs("warn", "the WebAssembly build failed (" + (e && e.message ? e.message : e) + ")");
       |    });
       |  }
       |
       |  // ?wasm=false or ?wasm=true chooses whatever the detection says, after a hash route (#/x?wasm=false) too.
       |  const query = location.search || (location.hash.includes("?") ? location.hash.slice(location.hash.indexOf("?")) : "");
       |  const forced = new URLSearchParams(query).get("wasm");
       |  if (forced === "false") loadJs("info", "forced by ?wasm=false");
       |  else if (forced === "true") loadWasm("forced by ?wasm=true");
       |  else {
       |    const why = missing();
       |    if (why) loadJs("info", why);
       |    else loadWasm("this browser has WasmGC, exception handling and JS string builtins");
       |  }
       |})();
       |""".stripMargin

  private def quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
