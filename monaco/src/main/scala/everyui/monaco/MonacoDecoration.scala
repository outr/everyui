package everyui.monaco

import fabric.*
import fabric.rw.*

/** A decoration: `options` over `range` (`MonacoController.deltaDecorations`). */
case class MonacoDecoration(range: MonacoRange, options: MonacoDecorationOptions) {
  def toJson: Json = obj("range" -> range.json, "options" -> options.toJson)
}

object MonacoDecoration {
  /** Whole lines `from` to `to` lit with `className` (a line-range highlight), marked in the overview ruler in
    * `ruler` when given. */
  def lines(from: Int, to: Int, className: String = "everyui-monaco-highlight", ruler: Option[String] = Some("#ffd54f")): MonacoDecoration =
    MonacoDecoration(MonacoRange(from, 1, to, 1), MonacoDecorationOptions(isWholeLine = Some(true),
      className = Some(className), overviewRuler = ruler.map(c => MonacoOverviewRuler(c))))

  /** `range` lit within its lines (a search hit, a symbol found): `everyui-monaco-highlight-inline` by default. */
  def highlight(range: MonacoRange, className: String = "everyui-monaco-highlight-inline",
                hover: Option[String] = None): MonacoDecoration =
    MonacoDecoration(range, MonacoDecorationOptions(inlineClassName = Some(className), hoverMessage = hover,
      overviewRuler = Some(MonacoOverviewRuler("#ffd54f"))))

  /** A mark in the gutter beside lines `from` to `to`, by the line numbers: `kind` one of "added", "modified",
    * "deleted" (a changed-line marker), with a hover in Markdown. */
  def gutter(from: Int, to: Int, kind: String, hover: Option[String] = None): MonacoDecoration =
    MonacoDecoration(MonacoRange(from, 1, to, 1), MonacoDecorationOptions(isWholeLine = Some(true),
      linesDecorationsClassName = Some("everyui-monaco-" + kind), hoverMessage = hover))

  /** A note after the end of `line`, not part of the text (who changed it last, and when), with a hover in Markdown. */
  def blame(line: Int, note: String, hover: Option[String] = None): MonacoDecoration =
    MonacoDecoration(MonacoRange(line, 1, line, Int.MaxValue), MonacoDecorationOptions(
      after = Some(MonacoInjectedText(note, Some("everyui-monaco-blame"))), hoverMessage = hover,
      stickiness = Some(MonacoDecorationStickiness.NeverGrowsWhenTypingAtEdges)))

  /** A glyph in the glyph margin (`glyphMargin` on) beside `line`: `"info"` or `"error"`, with a hover in Markdown. */
  def glyph(line: Int, kind: String, hover: Option[String] = None): MonacoDecoration =
    MonacoDecoration(MonacoRange(line, 1, line, 1), MonacoDecorationOptions(isWholeLine = Some(true),
      glyphMarginClassName = Some("everyui-monaco-" + kind + "-glyph"), glyphMarginHoverMessage = hover))
}
