package everyui.render

import everyui.component.types.PasteAction
import javafx.scene.control.TextInputControl
import javafx.scene.input.Clipboard

/** A field's pastes put to the app before they land (`InputView.pasted`). JavaFX's Ctrl+V (Cmd+V on a Mac), Shift+Insert
  * and the context menu's Paste all call the control's `paste`, which the fields made here override (`PastingTextField`,
  * `PastingPasswordField`, `PastingTextArea`) to come here first. Words put in the paste's place replace the selection
  * as the paste would, and JavaFX's undo takes them back as one edit. A clipboard with no words (a picture, files)
  * pastes as it would, and a field that is not editable (read-only, or out of use) takes no paste at all. */
object Pasting {
  private val key = "everyui.pasted"

  /** `f` asked about each paste into `field`. */
  def hook(field: TextInputControl, f: String => PasteAction): Unit = field.getProperties.put(key, f)

  /** A paste into `field`: `own` is the control's own paste, for one that lands as it is. */
  def paste(field: TextInputControl, own: () => Unit): Unit = {
    val clipboard = Clipboard.getSystemClipboard
    val words = if (clipboard.hasString) Option(clipboard.getString).getOrElse("") else ""
    Option(field.getProperties.get(key)) match {
      // a field not editable (read-only, or out of use) takes no paste, and its app is not asked about one
      case _ if !field.isEditable => ()
      case Some(f: Function1[?, ?]) if words.nonEmpty =>
        f.asInstanceOf[String => PasteAction](words) match {
          case PasteAction.Insert => own()
          case PasteAction.Ignore => ()
          case PasteAction.InsertText(text) => field.replaceSelection(text)
        }
      case _ => own()
    }
  }
}
