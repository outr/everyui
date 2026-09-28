package everyui.monaco

/** A provider's suggestions. `incomplete` has Monaco ask again as more is typed, rather than filter these. */
case class MonacoCompletionList(suggestions: List[MonacoCompletionItem] = Nil, incomplete: Boolean = false)
