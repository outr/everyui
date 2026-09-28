package everyui.monaco

import fabric.*

/** How a decoration looks: CSS classes (defined by the app's own stylesheet, or by `MonacoEditor.css`) on the text,
  * the line, the margins; a hover in Markdown; marks in the overview ruler and the minimap. `raw` adds any other of
  * Monaco's `IModelDecorationOptions`, winning over the typed fields. */
case class MonacoDecorationOptions(className: Option[String] = None, inlineClassName: Option[String] = None,
                                   glyphMarginClassName: Option[String] = None, marginClassName: Option[String] = None,
                                   linesDecorationsClassName: Option[String] = None,
                                   firstLineDecorationClassName: Option[String] = None,
                                   afterContentClassName: Option[String] = None, beforeContentClassName: Option[String] = None,
                                   isWholeLine: Option[Boolean] = None, stickiness: Option[MonacoDecorationStickiness] = None,
                                   zIndex: Option[Int] = None, hoverMessage: Option[String] = None,
                                   glyphMarginHoverMessage: Option[String] = None, overviewRuler: Option[MonacoOverviewRuler] = None,
                                   minimap: Option[MonacoMinimapDecoration] = None, showIfCollapsed: Option[Boolean] = None,
                                   before: Option[MonacoInjectedText] = None, after: Option[MonacoInjectedText] = None,
                                   raw: Json = obj()) {
  def toJson: Json = Wire.merge(Wire.obj(
    "className" -> Wire.opt(className)(str), "inlineClassName" -> Wire.opt(inlineClassName)(str),
    "glyphMarginClassName" -> Wire.opt(glyphMarginClassName)(str), "marginClassName" -> Wire.opt(marginClassName)(str),
    "linesDecorationsClassName" -> Wire.opt(linesDecorationsClassName)(str),
    "firstLineDecorationClassName" -> Wire.opt(firstLineDecorationClassName)(str),
    "afterContentClassName" -> Wire.opt(afterContentClassName)(str),
    "beforeContentClassName" -> Wire.opt(beforeContentClassName)(str),
    "isWholeLine" -> Wire.opt(isWholeLine)(bool), "stickiness" -> Wire.opt(stickiness)(s => num(s.wire)),
    "zIndex" -> Wire.opt(zIndex)(num), "hoverMessage" -> Wire.opt(hoverMessage)(m => obj("value" -> str(m))),
    "glyphMarginHoverMessage" -> Wire.opt(glyphMarginHoverMessage)(m => obj("value" -> str(m))),
    "overviewRuler" -> Wire.opt(overviewRuler)(_.toJson), "minimap" -> Wire.opt(minimap)(_.toJson),
    "showIfCollapsed" -> Wire.opt(showIfCollapsed)(bool), "before" -> Wire.opt(before)(_.toJson),
    "after" -> Wire.opt(after)(_.toJson)), raw)
}
