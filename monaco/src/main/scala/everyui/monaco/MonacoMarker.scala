package everyui.monaco

import fabric.*

/** A diagnostic on the text (an error, a warning): a squiggle, a hover, a mark in the overview ruler. `source` says
  * where it came from, `code` what it is (a link when `codeHref` is given). Monaco's `IMarkerData`. */
case class MonacoMarker(range: MonacoRange, severity: MonacoMarkerSeverity, message: String, source: Option[String] = None,
                        code: Option[String] = None, codeHref: Option[String] = None, tags: List[MonacoMarkerTag] = Nil,
                        relatedInformation: List[MonacoRelatedInformation] = Nil) {
  def toJson: Json = Wire.obj(
    "startLine" -> num(range.startLine), "startColumn" -> num(range.startColumn), "endLine" -> num(range.endLine),
    "endColumn" -> num(range.endColumn), "severity" -> num(severity.wire), "message" -> str(message),
    "source" -> Wire.opt(source)(str),
    "code" -> Wire.opt(code)(c => codeHref.map(h => obj("value" -> str(c), "target" -> str(h))).getOrElse(str(c))),
    "tags" -> (if (tags.isEmpty) Null else arr(tags.map(t => num(t.wire))*)),
    "relatedInformation" -> (if (relatedInformation.isEmpty) Null else arr(relatedInformation.map(_.toJson)*)))
}
