package everyui.monaco

/** Why suggestions were asked for, and the character typed that asked, if one did. */
case class MonacoCompletionContext(triggerKind: MonacoCompletionTriggerKind, triggerCharacter: Option[String] = None)
