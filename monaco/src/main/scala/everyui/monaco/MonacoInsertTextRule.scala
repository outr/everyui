package everyui.monaco

/** Flags for a suggestion's `insertTextRules`: `InsertAsSnippet` reads its text as a snippet (`$1`, `${1:name}`,
  * `${1|one,two|}`). */
object MonacoInsertTextRule {
  val None: Int = 0
  val KeepWhitespace: Int = 1
  val InsertAsSnippet: Int = 4
}
