package everyui.render

import scala.scalajs.js

/** What a browser API rejected with, read: a DOMException's name ("AbortError", "NotAllowedError") and its words, or
  * whatever else was thrown, as text. */
object WebErrors {
  /** The error's name, or "" when it has none. */
  def name(e: Any): String = e match {
    case d: js.Object if !js.isUndefined(d.asInstanceOf[js.Dynamic].name) => String.valueOf(d.asInstanceOf[js.Dynamic].name)
    case _ => ""
  }

  /** "Name: message", or the error as text where it is not an error object. */
  def message(e: Any): String = {
    val kind = name(e)
    if (kind.isEmpty) String.valueOf(e)
    else {
      val words = e.asInstanceOf[js.Dynamic].message
      if (js.isUndefined(words) || String.valueOf(words).isEmpty) kind else kind + ": " + String.valueOf(words)
    }
  }
}
