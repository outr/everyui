package everyui.render

import javafx.scene.control.TextField

/** A JavaFX `TextField` (a line of text) whose pastes, by the keys or the context menu, are put to the app before they land
  * (`Pasting`). */
final class PastingTextField extends TextField {
  override def paste(): Unit = Pasting.paste(this, () => super.paste())
}
