package everyui.monaco

import fabric.*
import fabric.rw.*

/** A symbol in a document, for the outline and Go to Symbol: its name, what it is, the range it spans and the range
  * of its name, and the symbols inside it. */
case class MonacoDocumentSymbol(name: String, kind: MonacoSymbolKind, range: MonacoRange, selectionRange: Option[MonacoRange] = None,
                                detail: String = "", children: List[MonacoDocumentSymbol] = Nil) {
  def toJson: Json = obj("name" -> str(name), "detail" -> str(detail), "kind" -> num(kind.wire), "range" -> range.json,
    "selectionRange" -> selectionRange.getOrElse(range).json, "children" -> arr(children.map(_.toJson)*))
}
