package everyui.chat

import everyui.Color
import everyui.event.KeyEvent
import everyui.text.TextArea

/** A [[Composer]]'s box of lines: a growing [[TextArea]] with no box of its own (the composer draws that), whose keys
  * are first asked of the composer (`keys`, as [[ComposerKeys]] rules): a key it acts on is kept from the platform's
  * field and handed to `act`. */
class ComposerField(keys: KeyEvent => ComposerAction, act: ComposerAction => Unit, grow: Boolean, minimum: Double,
                    maximum: Double, points: Double)
  extends TextArea("", 300.0, grow, minimum, maximum, points) {
  borderWidth @= 0.0
  backgroundColor @= Color.Clear
  event.key.down.attach { e =>
    val action = keys(e)
    if (action != ComposerAction.Pass) act(action)
  }

  override def keepsEvent(e: KeyEvent): Boolean = keys(e) != ComposerAction.Pass || super.keepsEvent(e)

  override def kind: String = "ComposerField"
}
