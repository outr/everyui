package everyui.render

import javafx.scene.control.skin.TextAreaSkin
import javafx.scene.control.{Skin, TextArea}

/** A JavaFX `TextArea` (a box of lines) whose pastes, by the keys or the context menu, are put to the app before they
  * land (`Pasting`), and whose caret blinks only while it has the focus (`PastingTextField`). */
final class PastingTextArea extends TextArea {
  override def paste(): Unit = Pasting.paste(this, () => super.paste())

  override protected def createDefaultSkin(): Skin[?] = new TextAreaSkin(this) {
    override def setCaretAnimating(value: Boolean): Unit = super.setCaretAnimating(value && getSkinnable.isFocused)
  }
}
