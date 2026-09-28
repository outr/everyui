package everyui.monaco

import fabric.*

/** Something the guest said (`MonacoBridge.listen`): its type (`editor.contentChange`) and what it carries. */
case class BridgeEvent(`type`: String, payload: Json) {
  def editorId: String = Wire.string(payload, "editorId")
  def diffId: String = Wire.string(payload, "diffId")
}
