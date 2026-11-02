package everyui.render

import org.scalajs.dom

import scala.scalajs.js

/** LiveKit's client in the page, for a call (LiveKitEngine): loaded by `<script src>` from the files served next to
  * the page, once, the first time a call connects, and never before. The files come in everyui-web
  * (`everyui-livekit/<version>/`), and sbt-everyui's `everyuiWebGuests` copies them out beside the page, so a
  * Content-Security-Policy of `script-src 'self'` holds (a call also needs `connect-src` to reach the LiveKit server's
  * `wss:` address).
  *
  * {{{
  *   LiveKitScript.root = "/static/everyui-livekit/" + LiveKitScript.version   // served somewhere else
  * }}}
  */
object LiveKitScript {
  /** The version of livekit-client bundled (project/LiveKitAssets.scala). */
  val version: String = "2.23.0"

  /** Where its files are: relative to the page's base (its `<base href>`, else its address), under a sealed bundle's
    * (WebAssets), or a whole address. */
  var root: String = "everyui-livekit/" + version

  /** The client's module (`LivekitClient`: `Room`, `RoomEvent`, ...), once the script has loaded. */
  def client: js.Dynamic = dom.window.asInstanceOf[js.Dynamic].LivekitClient

  /** `f` with whether the client is in the page, as `PageScript.load`. */
  def load(f: Boolean => Unit): Unit = script.load(f)

  private val script = new PageScript("call", () => !js.isUndefined(client),
    () => PageScript.file(root, "livekit-client.umd.js"))
}
