package everyui.monaco

import fabric.*
import fabric.rw.*

/** A place in a resource: where a definition is. */
case class MonacoLocation(uri: String, range: MonacoRange) {
  def toJson: Json = obj("uri" -> str(uri), "range" -> range.json)
}
