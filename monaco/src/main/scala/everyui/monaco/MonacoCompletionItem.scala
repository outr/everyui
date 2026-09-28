package everyui.monaco

import fabric.*
import fabric.rw.*

/** One suggestion: `label` in the list, `insertText` put in its place (a snippet with
  * `MonacoInsertTextRule.InsertAsSnippet`), `detail` beside it and `documentation` (Markdown) in its details. Without
  * a `range` it replaces the word at the cursor. Monaco's `CompletionItem`. */
case class MonacoCompletionItem(label: String, kind: MonacoCompletionKind, insertText: String, detail: Option[String] = None,
                                documentation: Option[String] = None, sortText: Option[String] = None,
                                filterText: Option[String] = None, insertTextRules: Int = MonacoInsertTextRule.None,
                                range: Option[MonacoRange] = None, preselect: Option[Boolean] = None,
                                commitCharacters: List[String] = Nil) {
  def toJson: Json = Wire.obj(
    "label" -> str(label), "kind" -> num(kind.wire), "insertText" -> str(insertText), "insertTextRules" -> num(insertTextRules),
    "detail" -> Wire.opt(detail)(str),
    "documentation" -> Wire.opt(documentation)(d => obj("value" -> str(d), "isTrusted" -> bool(false))),
    "sortText" -> Wire.opt(sortText)(str), "filterText" -> Wire.opt(filterText)(str), "range" -> Wire.opt(range)(_.json),
    "preselect" -> Wire.opt(preselect)(bool),
    "commitCharacters" -> (if (commitCharacters.isEmpty) Null else arr(commitCharacters.map(c => str(c))*)))
}
