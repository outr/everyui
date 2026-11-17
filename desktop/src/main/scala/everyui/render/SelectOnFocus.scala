package everyui.render

import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.scene.control.TextInputControl
import javafx.scene.input.MouseEvent

/** A text field whose words are all chosen as it takes the keyboard (`InputView.selectOnFocus`), as a browser's address
  * bar does. Tab, or the app putting the keyboard in it, chooses them at once. A press on it while it has not the
  * keyboard chooses them as its click lands, since JavaFX places the caret as the press goes down, after the focus;
  * a press that dragged out a range keeps that range, and a press once it has the keyboard places the caret as ever.
  * The keyboard given back rather than newly taken chooses nothing: the window come back to, or the field put back
  * after it moved (`Component.refocus`), still being typed in. */
final class SelectOnFocus(field: TextInputControl) {
  /** Whether it chooses all as it takes the keyboard. */
  var on: Boolean = false
  // a press that is giving it the keyboard, whose click chooses
  private var pressing = false
  // the keyboard lost only for now: the window left, or the field taken off the window
  private var kept = false

  field.addEventFilter(MouseEvent.MOUSE_PRESSED, (_: MouseEvent) => pressing = !field.isFocused)
  field.focusedProperty.addListener(new ChangeListener[java.lang.Boolean] {
    override def changed(o: ObservableValue[? <: java.lang.Boolean], was: java.lang.Boolean,
                         now: java.lang.Boolean): Unit =
      if (now) {
        if (on && !pressing && !kept) field.selectAll()
        kept = false
      } else {
        pressing = false
        kept = field.getScene == null || (field.getScene.getFocusOwner eq field)
      }
  })
  field.addEventHandler(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => if (pressing) {
    pressing = false
    if (on && field.getSelection.getLength == 0) field.selectAll()
  })
}
