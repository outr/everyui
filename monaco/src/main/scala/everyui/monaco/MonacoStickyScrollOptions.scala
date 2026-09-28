package everyui.monaco

import fabric.*

/** The enclosing scopes' first lines kept at the top as the text scrolls (`stickyScroll`). `defaultModel` is
  * `"outlineModel"`, `"foldingProviderModel"` or `"indentationModel"`. */
case class MonacoStickyScrollOptions(enabled: Option[Boolean] = None, maxLineCount: Option[Int] = None,
                                     defaultModel: Option[String] = None, scrollWithEditor: Option[Boolean] = None,
                                     raw: Json = obj()) extends MonacoOptionSet
