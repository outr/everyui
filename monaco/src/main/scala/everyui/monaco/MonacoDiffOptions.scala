package everyui.monaco

import fabric.*

/** A diff editor's own options (Monaco's `IDiffEditorOptions`), over the editor options both its sides take
  * (`editor`). */
case class MonacoDiffOptions(renderSideBySide: Option[Boolean] = None,
                             useInlineViewWhenSpaceIsLimited: Option[Boolean] = None,
                             readOnly: Option[Boolean] = None, originalEditable: Option[Boolean] = None,
                             ignoreTrimWhitespace: Option[Boolean] = None, renderIndicators: Option[Boolean] = None,
                             renderMarginRevertIcon: Option[Boolean] = None, renderOverviewRuler: Option[Boolean] = None,
                             enableSplitViewResizing: Option[Boolean] = None, diffCodeLens: Option[Boolean] = None,
                             maxComputationTime: Option[Int] = None, raw: Json = obj()) extends MonacoOptionSet
