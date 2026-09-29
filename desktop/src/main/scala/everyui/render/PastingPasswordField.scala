package everyui.render

import javafx.scene.control.PasswordField

/** A JavaFX `PasswordField` (a secret) whose pastes, by the keys or the context menu, are put to the app before they land
  * (`Pasting`). */
final class PastingPasswordField extends PasswordField {
  override def paste(): Unit = Pasting.paste(this, () => super.paste())
}
