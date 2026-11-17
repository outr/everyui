package everyui.render

import javafx.scene.control.skin.TextFieldSkin
import javafx.scene.control.{Skin, TextField}

/** A JavaFX `TextField` (a line of text) whose pastes, by the keys or the context menu, are put to the app before they
  * land (`Pasting`), and whose caret blinks only while it has the focus. JavaFX's skin starts the caret's blinking,
  * a timeline that never ends, as the caret moves (words set, typed or chosen) and as a press on it is let go, the
  * focus or not: in a field with no focus (no window, or one not in front) it went on blinking unseen, keeping JavaFX
  * pulsing sixty times a second for as long as the field lived, out of the screen too, until it was collected. What
  * is chosen in it is kept as the focus moves between other nodes (`KeptSelection`). */
final class PastingTextField extends TextField {
  override def paste(): Unit = Pasting.paste(this, () => super.paste())

  // its choice kept as the focus moves between other nodes (`KeptSelection`); null while JavaFX's constructor runs
  private val kept = new KeptSelection(this)

  override def selectRange(anchor: Int, caretPosition: Int): Unit =
    if (kept == null || !(kept.held && anchor == 0 && caretPosition == 0)) super.selectRange(anchor, caretPosition)

  override protected def createDefaultSkin(): Skin[?] = {
    val skin = new TextFieldSkin(this) {
      override def setCaretAnimating(value: Boolean): Unit = super.setCaretAnimating(value && getSkinnable.isFocused)
    }
    kept.follow()
    skin
  }
}
