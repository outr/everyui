package everyui.render

import android.content.Context
import android.view.{KeyEvent, MotionEvent}
import android.widget.EditText

/** An `EditText` that is only a field while it is being edited. Until then it takes no focus and no touches, so
  * Android never hands it the focus by itself (a TV focuses the first focusable view as a window opens, and Google TV
  * brings the keyboard up with it) and a tap goes to the component around it, which starts editing. It also says when
  * its on-screen keyboard is put away with Back, which the keyboard takes before the activity ever sees it. */
final class FieldView(context: Context) extends EditText(context) {
  var done: () => Unit = () => ()
  // its action key pressed (Done, Search, Next), which also puts the keyboard away
  var action: () => Unit = () => ()
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
}
