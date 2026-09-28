package everyui.monaco

/** What asked for signature help: Monaco's `SignatureHelpTriggerKind`. */
enum MonacoSignatureHelpTriggerKind(val wire: Int) {
  case Invoke extends MonacoSignatureHelpTriggerKind(1)
  case TriggerCharacter extends MonacoSignatureHelpTriggerKind(2)
  case ContentChange extends MonacoSignatureHelpTriggerKind(3)
}

object MonacoSignatureHelpTriggerKind {
  def fromWire(value: Int): MonacoSignatureHelpTriggerKind = values.find(_.wire == value).getOrElse(Invoke)
}
