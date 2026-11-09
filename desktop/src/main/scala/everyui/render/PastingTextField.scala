package everyui.render

import javafx.scene.control.skin.TextFieldSkin
import javafx.scene.control.{Skin, TextField}

/** A JavaFX `TextField` (a line of text) whose pastes, by the keys or the context menu, are put to the app before they
  * land (`Pasting`), and whose caret blinks only while it has the focus. JavaFX's skin starts the caret's blinking,
  * a timeline that never ends, as the caret moves (words set, typed or chosen) and as a press on it is let go, the
  * focus or not: in a field with no focus (no window, or one not in front) it went on blinking unseen, keeping JavaFX
  * pulsing sixty times a second for as long as the field lived, out of the screen too, until it was collected. */
final class PastingTextField extends TextField {
  override def paste(): Unit = Pasting.paste(this, () => super.paste())

  override protected def createDefaultSkin(): Skin[?] = new TextFieldSkin(this) {
    override def setCaretAnimating(value: Boolean): Unit = super.setCaretAnimating(value && getSkinnable.isFocused)
  }
}
