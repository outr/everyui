package everyui.monaco

import fabric.*

/** Words drawn in the line before or after a decoration's range that are not part of the text (a blame note at the end
  * of a line), styled by `inlineClassName`. Monaco's `InjectedTextOptions`; the standalone editor draws no injected
  * text, so the bridge draws it as generated content (CSS `content`), which the class styles as `::after`. */
case class MonacoInjectedText(content: String, inlineClassName: Option[String] = None) {
  def toJson: Json = Wire.obj("content" -> str(content), "inlineClassName" -> Wire.opt(inlineClassName)(str))
}
