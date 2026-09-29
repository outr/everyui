package everyui.render

import org.scalajs.dom

import scala.scalajs.js

/** The clipboard read (`everyui.clipboard.Clipboard`) through `navigator.clipboard.readText`, which a browser answers
  * only for a page in front, from a click or a key, and after asking the person where it asks (Chromium's
  * "clipboard-read" permission; Firefox and Safari show a Paste button of their own). A refusal, or a clipboard with
  * no text, is None. */
object ClipboardAccess {
  def readable(): Boolean = {
    val clipboard = dom.window.navigator.asInstanceOf[js.Dynamic].clipboard
    !js.isUndefined(clipboard) && js.typeOf(clipboard.readText) == "function"
  }

  def read(done: Option[String] => Unit): Unit =
    if (!readable()) done(None)
    else dom.window.navigator.asInstanceOf[js.Dynamic].clipboard.readText().asInstanceOf[js.Promise[String]]
      .`then`[Unit]((text: String) => done(Option(text).filter(_.nonEmpty)))
      .`catch`[Unit] { (e: Any) =>
        Platform.log("clipboard not read: " + WebErrors.message(e))
        done(None)
      }
}
