package everyui.monaco

import fabric.*
import fabric.rw.*

/** A hover: blocks of Markdown, and the range lit while it shows (the word under the pointer when none). */
case class MonacoHover(contents: List[String], range: Option[MonacoRange] = None) {
  def toJson: Json = Wire.obj("contents" -> arr(contents.map(c => obj("value" -> str(c), "isTrusted" -> bool(false)))*),
    "range" -> Wire.opt(range)(_.json))
}
