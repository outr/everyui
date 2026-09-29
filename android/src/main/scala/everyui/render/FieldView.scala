package everyui.render

import android.content.{ClipboardManager, Context}
import android.view.{KeyEvent, MotionEvent}
import android.view.inputmethod.{EditorInfo, InputConnection}
import android.widget.EditText
import everyui.component.types.PasteAction

/** An `EditText` that is only a field while it is being edited. Until then it takes no focus and no touches, so
  * Android never hands it the focus by itself (a TV focuses the first focusable view as a window opens, and Google TV
  * brings the keyboard up with it) and a tap goes to the component around it, which starts editing. It also says when
  * its on-screen keyboard is put away with Back, which the keyboard takes before the activity ever sees it.
  *
  * Its pastes are put to the app before they land (`pasting`): its menu's Paste and Paste as plain text, Ctrl+V on a
  * keyboard, and a soft keyboard's Paste key, which all come through `onTextContextMenuItem`; and a soft keyboard's
  * clipboard suggestion, which is typed (`PasteConnection`) and is taken as a paste where it is the clipboard's words
  * and more than a line. A read-only field takes no paste, and its app is never asked about one. */
final class FieldView(context: Context) extends EditText(context) {
  var done: () => Unit = () => ()
  // its action key pressed (Done, Search, Next), which also puts the keyboard away
  var action: () => Unit = () => ()
  /** What a paste becomes, asked with its words before they land (`InputView.pasted`). */
  var pasting: Option[String => PasteAction] = None
  private var editing = false

  setFocusable(false)

  def edit(on: Boolean): Unit = {
    editing = on
    setFocusable(on)
    setFocusableInTouchMode(on)
  }

  // what a read-only field put by, to be a field again
  private var fixed = false
  private var keys: android.text.method.KeyListener = null
  private var kind: Int = 0

  /** Read-only, or a field again. Read-only, it has no key listener, so it takes no typing, offers no paste and brings
    * up no keyboard, and TalkBack reads it as not editable; its words are still selectable, to be chosen and copied.
    * Being a field again puts its keys, its input type (a secret's, an email address's) and its caret's movement back.
    * Either way it takes the focus only while it is being edited, as before. */
  def readOnly(on: Boolean): Unit = if (on != fixed) {
    fixed = on
    if (on) {
      keys = getKeyListener
      kind = getInputType
      setKeyListener(null)
      setTextIsSelectable(true)
    } else {
      setTextIsSelectable(false)
      setKeyListener(keys)
      setRawInputType(kind)
      setMovementMethod(android.text.method.ArrowKeyMovementMethod.getInstance)
    }
    edit(editing)
  }

  override def dispatchTouchEvent(event: MotionEvent): Boolean = editing && super.dispatchTouchEvent(event)

  override def onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean = {
    if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction == KeyEvent.ACTION_UP) done()
    super.onKeyPreIme(keyCode, event)
  }

  override def onTextContextMenuItem(id: Int): Boolean = {
    val paste = id == android.R.id.paste || id == android.R.id.pasteAsPlainText
    val words = if (paste && pasting.nonEmpty && writable) clipboard() else ""
    if (words.isEmpty) super.onTextContextMenuItem(id)
    else pasting.get(words) match {
      case PasteAction.Insert => super.onTextContextMenuItem(id)
      case PasteAction.Ignore => true
      case PasteAction.InsertText(text) =>
        val edit = getText
        val from = math.max(0, math.min(getSelectionStart, getSelectionEnd))
        val to = math.max(0, math.max(getSelectionStart, getSelectionEnd))
        edit.replace(from, to, text)
        setSelection(math.min(from + text.length, edit.length))
        true
    }
  }

  override def onCreateInputConnection(info: EditorInfo): InputConnection = {
    val made = super.onCreateInputConnection(info)
    if (made == null) null else new PasteConnection(made, this)
  }

  /** What words a soft keyboard types become: a paste's action where they are the clipboard's words and more than a
    * line (a keyboard's clipboard suggestion), else they land as typed. A word typed is never read against the
    * clipboard, which Android tells the person of. */
  def typed(words: String): PasteAction = pasting match {
    case Some(f) if writable && words.length > 1 && (words.indexOf('\n') >= 0 || words.length > 80) && clipboard() == words => f(words)
    case _ => PasteAction.Insert
  }

  // a field that takes typing: not read-only (no key listener) nor out of use, where a paste lands and its app is asked
  private def writable: Boolean = getKeyListener != null && isEnabled

  // the clipboard's words, as Android's own paste joins them; empty where it holds none
  private def clipboard(): String = {
    val manager = getContext.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    val clip = if (manager == null) null else manager.getPrimaryClip
    if (clip == null) ""
    else (0 until clip.getItemCount).flatMap(i => Option(clip.getItemAt(i).coerceToText(getContext))).map(_.toString)
      .filter(_.nonEmpty).mkString("\n")
  }
}
