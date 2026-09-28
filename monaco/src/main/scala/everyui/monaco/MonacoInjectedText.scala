package everyui.monaco

import fabric.*

/** Words drawn in the line before or after a decoration's range that are not part of the text (a blame note at the end
  * of a line), styled by `inlineClassName`. Monaco's `InjectedTextOptions`. */
case class MonacoInjectedText(content: String, inlineClassName: Option[String] = None) {
  def toJson: Json = Wire.obj("content" -> str(content), "inlineClassName" -> Wire.opt(inlineClassName)(str))
}
