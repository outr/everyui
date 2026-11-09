package everyui.render

import javafx.scene.control.skin.TextFieldSkin
import javafx.scene.control.{PasswordField, Skin}

/** A JavaFX `PasswordField` (a secret) whose pastes, by the keys or the context menu, are put to the app before they
  * land (`Pasting`), and whose caret blinks only while it has the focus (`PastingTextField`). */
final class PastingPasswordField extends PasswordField {
  override def paste(): Unit = Pasting.paste(this, () => super.paste())

  override protected def createDefaultSkin(): Skin[?] = new TextFieldSkin(this) {
    override def setCaretAnimating(value: Boolean): Unit = super.setCaretAnimating(value && getSkinnable.isFocused)
  }
}
