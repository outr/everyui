package everyui.sbt

/** What an EveryUI web app is built as (the sbt setting `everyuiWebTarget`). */
enum WebTarget {
  /** JavaScript only: `main.js`, as every browser runs it. */
  case Js
  /** WebAssembly only: faster, but a browser without WasmGC, exnref exception handling or the JS string builtins
    * cannot run the app at all (the console says what it lacks). */
  case Wasm
  /** WebAssembly where the browser runs it, the JavaScript build elsewhere or when the WebAssembly one fails to
    * start. Links the app twice. */
  case Both
}
