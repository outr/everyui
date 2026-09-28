package everyui.monaco

import fabric.*

/** A parameter of a signature, with its documentation in Markdown. */
case class MonacoParameterInformation(label: String, documentation: Option[String] = None) {
  def toJson: Json = Wire.obj("label" -> str(label),
    "documentation" -> Wire.opt(documentation)(d => obj("value" -> str(d), "isTrusted" -> bool(false))))
}
