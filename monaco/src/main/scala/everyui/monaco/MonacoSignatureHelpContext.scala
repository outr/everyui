package everyui.monaco

/** Why signature help was asked for. */
case class MonacoSignatureHelpContext(triggerKind: MonacoSignatureHelpTriggerKind, triggerCharacter: Option[String] = None,
                                      isRetrigger: Boolean = false)
