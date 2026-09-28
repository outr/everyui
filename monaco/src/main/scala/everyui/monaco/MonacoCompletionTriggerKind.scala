package everyui.monaco

/** What asked for suggestions: Monaco's `CompletionTriggerKind`. */
enum MonacoCompletionTriggerKind(val wire: Int) {
  case Invoke extends MonacoCompletionTriggerKind(0)
  case TriggerCharacter extends MonacoCompletionTriggerKind(1)
  case TriggerForIncompleteCompletions extends MonacoCompletionTriggerKind(2)
}

object MonacoCompletionTriggerKind {
  def fromWire(value: Int): MonacoCompletionTriggerKind = values.find(_.wire == value).getOrElse(Invoke)
}
