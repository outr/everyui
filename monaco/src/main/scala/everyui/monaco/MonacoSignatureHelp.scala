package everyui.monaco

import fabric.*

/** Signature help: the signatures that fit, and which one and which parameter the cursor is in. */
case class MonacoSignatureHelp(signatures: List[MonacoSignatureInformation], activeSignature: Int = 0, activeParameter: Int = 0) {
  def toJson: Json = obj("signatures" -> arr(signatures.map(_.toJson)*), "activeSignature" -> num(activeSignature),
    "activeParameter" -> num(activeParameter))
}
