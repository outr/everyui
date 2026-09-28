package everyui.monaco

/** A click on a checkbox in the diff editor's modified gutter: on `line`, a change's (`isLine` false) or a line's, and
  * the change that line belongs to, if any. */
case class MonacoDiffClick(line: Int, isLine: Boolean, change: Option[MonacoLineChange])
