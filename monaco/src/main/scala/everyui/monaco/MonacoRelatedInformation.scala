package everyui.monaco

import fabric.*

/** Somewhere else a marker concerns, shown in its hover. */
case class MonacoRelatedInformation(resource: String, range: MonacoRange, message: String) {
  def toJson: Json = obj("resource" -> str(resource), "message" -> str(message), "startLine" -> num(range.startLine),
    "startColumn" -> num(range.startColumn), "endLine" -> num(range.endLine), "endColumn" -> num(range.endColumn))
}
