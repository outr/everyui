package everyui.monaco

import fabric.*

/** One signature: its label, documentation, parameters, and which parameter is being typed. */
case class MonacoSignatureInformation(label: String, documentation: Option[String] = None,
                                      parameters: List[MonacoParameterInformation] = Nil, activeParameter: Option[Int] = None) {
  def toJson: Json = Wire.obj("label" -> str(label),
    "documentation" -> Wire.opt(documentation)(d => obj("value" -> str(d), "isTrusted" -> bool(false))),
    "parameters" -> arr(parameters.map(_.toJson)*), "activeParameter" -> Wire.opt(activeParameter)(num))
}
