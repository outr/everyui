package everyui.render

import javafx.scene.control.TextArea

/** A JavaFX `TextArea` (a box of lines) whose pastes, by the keys or the context menu, are put to the app before they land
  * (`Pasting`). */
final class PastingTextArea extends TextArea {
  override def paste(): Unit = Pasting.paste(this, () => super.paste())
}
