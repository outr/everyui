package everyui.sbt

/** The page's loader for an app built as WebAssembly (`WebTarget.Wasm` or `WebTarget.Both`), which WebScripts writes
  * as `everyui-loader.js`. It runs the WebAssembly build where the browser has what it needs (WasmGC, exception
  * handling with exnref, the JS string builtins); otherwise, or if the WebAssembly build fails to start, the JS build
  * when there is one. It says in the console which it loaded and why. `?wasm=false` in the page's URL forces the JS
  * build, `?wasm=true` the WebAssembly one; `window.everyuiBuild` and `Platform.build` say which runs. */
object WasmLoader {
  val fileName: String = "everyui-loader.js"

  /** The loader, for the JS build at `js` (`true` when it is an ES module), if any, and the WebAssembly build's
    * main.js at `wasm`. The detection validates three tiny modules: a GC struct type, a try_table with exnref, and
    * one that imports wasm:js-string "cast" with the wrong type, which is invalid only where the builtins are known. */
  def script(js: Option[(String, Boolean)], wasm: String, version: String): String = {
    val (jsSrc, jsModule) = js.getOrElse(("", false))
    s"""(() => {
       |  const js = ${if (js.isEmpty) "null" else quote(jsSrc)};
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
       |    if (!js) {
       |      window.everyuiBuild = null;
       |      say("error", "not running: this app is built only as WebAssembly, and there is no JS build to load (" + why + ")");
       |      return;
       |    }
       |    window.everyuiBuild = "js";
       |    say(level, "loading the JS build: " + why);
       |    const s = document.createElement("script");
       |    ${if (jsModule) "s.type = \"module\";\n       |    " else ""}s.src = js;
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
  }

  private def quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
