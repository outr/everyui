package everyui.render

import android.os.Bundle
import android.view.inputmethod.{InputConnection, InputConnectionWrapper, InputContentInfo}
import everyui.component.types.PasteAction

/** A soft keyboard's link to a `FieldView`, whose typed words are first asked about (`FieldView.typed`): a keyboard's
  * clipboard suggestion is typed rather than pasted, and is taken as a paste where it is the clipboard's words. A
  * picture the keyboard inserts (a GIF, a sticker: `commitContent`, Android 7.1 on) goes to the field
  * (`FieldView.committed`), and on to the field's own connection where the field does not take it. */
final class PasteConnection(target: InputConnection, field: FieldView) extends InputConnectionWrapper(target, true) {
  override def commitText(text: CharSequence, newCursorPosition: Int): Boolean =
    if (text == null) super.commitText(text, newCursorPosition)
    else field.typed(text.toString) match {
      case PasteAction.Insert => super.commitText(text, newCursorPosition)
      case PasteAction.Ignore => true
      case PasteAction.InsertText(other) => super.commitText(other, newCursorPosition)
    }

  override def commitContent(content: InputContentInfo, flags: Int, opts: Bundle): Boolean =
    field.committed(content, flags) || super.commitContent(content, flags, opts)
}
