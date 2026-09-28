package everyui.monaco

import fabric.*

/** A checkbox in the diff editor's modified gutter (`MonacoDiffController.checks`): on line `line`, for a whole change
  * (`line` = false: in the glyph margin, on the change's first line) or for that one line (`line` = true: beside the
  * line numbers), lit when `checked`, with a hover in Markdown. What a tick means is the app's to decide. */
case class MonacoDiffCheck(at: Int, checked: Boolean, line: Boolean = false, hover: Option[String] = None) {
  def toJson: Json = Wire.obj("line" -> num(at), "checked" -> bool(checked), "kind" -> str(if (line) "line" else "change"),
    "hover" -> Wire.opt(hover)(str))
}

object MonacoDiffCheck {
  /** The usual set: one per change (a deletion too), checked as `changeChecked` says, and, with `lines`, one per line
    * each change holds on the modified side, checked as `lineChecked` says. */
  def forChanges(changes: List[MonacoLineChange], changeChecked: MonacoLineChange => Boolean, lines: Boolean = false,
                 lineChecked: Int => Boolean = _ => true): List[MonacoDiffCheck] =
    changes.flatMap { c =>
      MonacoDiffCheck(c.anchorLine, changeChecked(c)) ::
        (if (lines) c.modifiedLines.toList.map(l => MonacoDiffCheck(l, lineChecked(l), line = true)) else Nil)
    }
}
