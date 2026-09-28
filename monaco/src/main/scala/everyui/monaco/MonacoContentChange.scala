package everyui.monaco

import fabric.*

/** The text of a model changed (`onDidChangeModelContent`): its new version, and the edits, which all refer to the
  * text before them. `value` carries the whole new text where the edits alone could not be followed (the text
  * replaced whole, or its line endings changed). `external` is a change the app made itself (`setValue`), `undo` and
  * `redo` the editor's own. */
case class MonacoContentChange(versionId: Int, changes: List[MonacoTextChange], flush: Boolean = false,
                               external: Boolean = false, undo: Boolean = false, redo: Boolean = false,
                               value: Option[String] = None)

object MonacoContentChange {
  def fromJson(json: Json): MonacoContentChange = MonacoContentChange(
    versionId = Wire.int(json, "versionId"),
    changes = json.get("changes").filter(_.isArr).map(_.asVector.toList).getOrElse(Nil).map { c =>
      MonacoTextChange(Wire.int(c, "rangeOffset"), Wire.int(c, "rangeLength"), Wire.string(c, "text"))
    },
    flush = Wire.bool(json, "flush"), external = Wire.bool(json, "external"), undo = Wire.bool(json, "undo"),
    redo = Wire.bool(json, "redo"), value = json.get("value").filter(_.isStr).map(_.asString))
}
