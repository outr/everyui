package everyui.monaco

import fabric.*

/** An action of the app's in the editor: in its command palette, in its context menu when `contextMenuGroupId` is
  * given (`"navigation"`, `"1_modification"`, or one of the app's own), and run by its `keybindings` (MonacoKeyMod or'ed
  * with a MonacoKeyCode). `precondition` and `keybindingContext` are Monaco context-key expressions. Monaco's
  * `IActionDescriptor`. */
case class MonacoAction(id: String, label: String, keybindings: List[Int] = Nil, contextMenuGroupId: Option[String] = None,
                        contextMenuOrder: Option[Double] = None, precondition: Option[String] = None,
                        keybindingContext: Option[String] = None)(val run: String => Unit) {
  def toJson: Json = Wire.obj(
    "id" -> str(id), "label" -> str(label), "keybindings" -> arr(keybindings.map(k => num(k))*),
    "contextMenuGroupId" -> Wire.opt(contextMenuGroupId)(str), "contextMenuOrder" -> Wire.opt(contextMenuOrder)(num),
    "precondition" -> Wire.opt(precondition)(str), "keybindingContext" -> Wire.opt(keybindingContext)(str))
}
