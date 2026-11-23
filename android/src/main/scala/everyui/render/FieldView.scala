package everyui.render

import android.content.{ClipboardManager, Context}
import android.view.{KeyEvent, MotionEvent}
import android.view.inputmethod.{EditorInfo, InputConnection}
import android.widget.EditText
import everyui.component.types.PasteAction
import everyui.file.PickedFile

/** An `EditText` that is only a field while it is being edited. Until then it takes no focus and no touches, so
  * Android never hands it the focus by itself (a TV focuses the first focusable view as a window opens, and Google TV
  * brings the keyboard up with it) and a tap goes to the component around it, which starts editing; a read-only one's
  * touches are its own at once, to be scrolled, and a tap on it gives it the focus itself. It also says when its
  * on-screen keyboard is put away with Back, which the keyboard takes before the activity ever sees it.
  *
  * Its pastes are put to the app before they land (`pasting`): its menu's Paste and Paste as plain text, Ctrl+V on a
  * keyboard, and a soft keyboard's Paste key, which all come through `onTextContextMenuItem`; and a soft keyboard's
  * clipboard suggestion, which is typed (`PasteConnection`) and is taken as a paste where it is the clipboard's words
  * and more than a line. A read-only field takes no paste, and its app is never asked about one.
  *
  * Where it takes a paste's pictures and files (`pastingFiles`), a paste's documents (the clipboard's `content:`
  * addresses of a type other than words: a screenshot, a picture shared to the clipboard) are read and handed over,
  * its words still landing, and it tells the keyboard it takes pictures (`EditorInfo.contentMimeTypes`), which a
  * keyboard's GIFs and stickers then come as (`committed`, by `PasteConnection.commitContent`). */
final class FieldView(context: Context) extends EditText(context) {
  var done: () => Unit = () => ()
  // its action key pressed (Done, Search, Next), which also puts the keyboard away
  var action: () => Unit = () => ()
  /** What a paste becomes, asked with its words before they land (`InputView.pasted`). */
  var pasting: Option[String => PasteAction] = None
  /** Where a paste's pictures and files go, and whether the field takes them now (`InputView.pastedFiles`). */
  var pastingFiles: Option[(() => Boolean, List[PickedFile] => Unit)] = None
  /** What is chosen, told as it changes (`InputView.selected`). */
  var selecting: (Int, Int) => Unit = null
  private var editing = false

  setFocusable(false)

  def edit(on: Boolean): Unit = {
    // a field of a form put up since the autofill session began (a sign-in card shown in a chat well after launch)
    // starts a session of its own: a finished one, a password manager's answer to another form, answers no other
    if (on && !editing) FieldView.newSessionFor(this)
    editing = on
    setFocusable(on)
    setFocusableInTouchMode(on)
  }

  // whether it is read-only, and the input type it put by to be a field again
  private var fixed = false
  private var kind: Int = 0

  /** Read-only, or a field again. Read-only, it has no key listener, so it takes no typing, offers no paste and brings
    * up no keyboard, and TalkBack reads it as not editable; its words are still selectable, to be chosen and copied,
    * and its touches are its own at once (`dispatchTouchEvent`), so a drag scrolls it with no tap first. Being a field
    * again puts its input type back (`fieldType`: a secret's, an email address's, with its keys) and its caret's
    * movement. Either way it takes the focus only while it is being edited or touched. */
  def readOnly(on: Boolean): Unit = if (on != fixed) {
    if (on) {
      kind = getInputType
      fixed = true
      setKeyListener(null)
      setTextIsSelectable(true)
    } else {
      fixed = false
      setTextIsSelectable(false)
      setFieldType(kind)
      setMovementMethod(android.text.method.ArrowKeyMovementMethod.getInstance)
    }
    setShowSoftInputOnFocus(!on)
    edit(editing)
  }

  /** Whether it is read-only (`readOnly`): no keyboard is brought up for it. */
  def isReadOnly: Boolean = fixed

  /** Its input type as a field: while it is read-only (which has none, `TYPE_NULL`) the one it is a field again with. */
  def fieldType: Int = if (fixed) kind else getInputType

  /** Its input type as a field set, its face kept (a password's would set its own): while it is read-only, kept for
    * when it is a field again, as setting it would give it its keys back. */
  def setFieldType(to: Int): Unit = if (fixed) kind = to else if (getInputType != to) {
    val face = getTypeface
    setInputType(to)
    setTypeface(face)
  }

  /** Whether a drag along it (1 down, 2 across) can still scroll its words `toward` (1 on, -1 back), where its touches
    * are its own while it is not edited (read-only): a scroll it is in leaves such a drag to it, and takes one at its
    * end (`NodeGroup.scrollUnder`). */
  def scrolls(along: Int, toward: Int): Boolean = fixed && getLayout != null && {
    val layout = getLayout
    if (along == 1) everyui.event.DragAxis.goes(getScrollY,
      layout.getHeight - (getHeight - getCompoundPaddingTop - getCompoundPaddingBottom), toward)
    else {
      val lines = 0 until layout.getLineCount
      val left = if (lines.isEmpty) 0f else lines.map(layout.getLineLeft).min
      val right = if (lines.isEmpty) 0f else lines.map(layout.getLineRight).max
      everyui.event.DragAxis.goes(getScrollX - left, right - left - (getWidth - getCompoundPaddingLeft -
        getCompoundPaddingRight), toward)
    }
  }

  // its words all chosen as it takes the focus (`selectOnFocus`); not as it is given the focus back after it moved
  // (`kept`); and whether it lost the focus just now, as a field taken off the screen does on its way off
  private var selectsAll = false
  private var kept = false
  private var blurred = false

  /** All of its words chosen as it takes the focus (`InputView.selectOnFocus`), Android's own way
    * (`setSelectAllOnFocus`), which leaves a tap on it once it has the focus to place the caret; but not as the focus is
    * given back to it, put back on the screen after it moved (`Component.refocus`), still being typed in. */
  def selectOnFocus(on: Boolean): Unit = {
    selectsAll = on
    setSelectAllOnFocus(on && !kept)
  }

  override def onFocusChanged(focused: Boolean, direction: Int, previous: android.graphics.Rect): Unit = {
    super.onFocusChanged(focused, direction, previous)
    if (focused && kept) {
      kept = false
      setSelectAllOnFocus(selectsAll)
    } else if (!focused) {
      blurred = true
      post(() => blurred = false)
      // a read-only field a touch gave the focus to (`touched`) takes none by itself again once it moves on
      if (fixed && !editing) post(() => if (!isFocused && !editing) edit(false))
    }
  }

  override def onDetachedFromWindow(): Unit = {
    if (selectsAll && (blurred || isFocused)) {
      kept = true
      setSelectAllOnFocus(false)
    }
    super.onDetachedFromWindow()
  }

  // where a touch on it went down, and whether it has since gone further than a tap may (a drag that scrolled it)
  private var downAt = (0f, 0f)
  private var strayed = false

  // a read-only field's touches are its own while it is not edited too: a drag scrolls it, and a tap or a long press
  // gives it the focus (`touched`)
  override def dispatchTouchEvent(event: MotionEvent): Boolean = (editing || fixed) && {
    if (event.getActionMasked == MotionEvent.ACTION_DOWN) {
      downAt = (event.getRawX, event.getRawY)
      strayed = false
    }
    else if (event.getActionMasked == MotionEvent.ACTION_MOVE) {
      val slop = android.view.ViewConfiguration.get(getContext).getScaledTouchSlop
      if (math.abs(event.getRawX - downAt._1) > slop || math.abs(event.getRawY - downAt._2) > slop) strayed = true
    }
    super.dispatchTouchEvent(event)
  }

  // a tap that did not scroll it
  override def performClick(): Boolean = {
    if (fixed && !editing && !strayed) touched()
    super.performClick()
  }

  // a long press, which chooses its words
  override def performLongClick(): Boolean = {
    if (fixed && !editing) touched()
    super.performLongClick()
  }

  // a read-only field not edited given the focus by a touch, which starts editing it (`RenderNode.onInputFocus`), with no
  // keyboard; a bare one, which nothing edits, keeps it till it moves on (`onFocusChanged`)
  private def touched(): Unit = {
    setFocusableInTouchMode(true)
    if (!isFocused) requestFocus()
  }

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow()
    FieldView.attached(this)
  }

  // Told to autofill as the field it is: focusable, which it is the moment it is touched (it is only kept from taking
  // the focus by itself, on a TV), and where it is in the window, which its translation places it (its own left and top
  // are 0, the layout being EveryUI's), and as large as it is drawn there: laid out in points and scaled up by the views
  // it is in, its own width and height alone told a field 22 pixels high. Told unfocusable at 0, 0, a password manager
  // answered a sign-in's first request with nothing, Android finished the session there, and touching the fields after
  // asked for nothing more.
  override def onProvideAutofillStructure(structure: android.view.ViewStructure, flags: Int): Unit = {
    super.onProvideAutofillStructure(structure, flags)
    structure.setFocusable(true)
    val at = new Array[Int](2)
    getLocationInWindow(at)
    var scaleX = 1.0
    var scaleY = 1.0
    var view: android.view.View = this
    while (view != null) {
      scaleX *= view.getScaleX
      scaleY *= view.getScaleY
      view = view.getParent match {
        case parent: android.view.View => parent
        case _ => null
      }
    }
    structure.setDimens(at(0), at(1), 0, 0, math.round(getWidth * scaleX).toInt, math.round(getHeight * scaleY).toInt)
    // what it asks for, as a stock field's hint tells it, where its label is drawn beside it rather than in it
    if ((getHint == null || getHint.length == 0) && getContentDescription != null) {
      structure.setHint(getContentDescription)
    }
  }

  // told by EditText from its own constructor too, before `selecting` is set
  override def onSelectionChanged(start: Int, end: Int): Unit = {
    super.onSelectionChanged(start, end)
    if (selecting != null) selecting(start, end)
  }

  override def onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean = {
    if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction == KeyEvent.ACTION_UP) done()
    super.onKeyPreIme(keyCode, event)
  }

  override def onTextContextMenuItem(id: Int): Boolean = {
    val paste = id == android.R.id.paste || id == android.R.id.pasteAsPlainText
    val files = if (paste && writable) clipboardFiles() else Nil
    val words = if (paste && (pasting.nonEmpty || files.nonEmpty) && writable) clipboard(files.nonEmpty) else ""
    files.foreach(_())
    if (words.isEmpty) files.nonEmpty || super.onTextContextMenuItem(id)
    else pasting.map(_(words)).getOrElse(PasteAction.Insert) match {
      case PasteAction.Insert if files.isEmpty => super.onTextContextMenuItem(id)
      case PasteAction.Insert => replaced(words)
      case PasteAction.Ignore => true
      case PasteAction.InsertText(text) => replaced(text)
    }
  }

  // `text` in place of the selection, the caret after it
  private def replaced(text: String): Boolean = {
    val edit = getText
    val from = math.max(0, math.min(getSelectionStart, getSelectionEnd))
    val to = math.max(0, math.max(getSelectionStart, getSelectionEnd))
    edit.replace(from, to, text)
    setSelection(math.min(from + text.length, edit.length))
    true
  }

  override def onCreateInputConnection(info: EditorInfo): InputConnection = {
    val made = super.onCreateInputConnection(info)
    // a keyboard offers its pictures (GIFs, stickers) where the field says it takes them
    if (made != null && android.os.Build.VERSION.SDK_INT >= 25 && takesFiles) info.contentMimeTypes =
      Array("image/*")
    if (made == null) null else new PasteConnection(made, this)
  }

  /** A keyboard's picture (`InputConnection.commitContent`, Android 7.1 on): read and handed to `pastingFiles` where
    * the field takes it, with the permission the keyboard grants asked for while it is read; whether it was taken. */
  def committed(content: android.view.inputmethod.InputContentInfo, flags: Int): Boolean =
    if (!writable || !takesFiles || android.os.Build.VERSION.SDK_INT < 25) false
    else {
      val granted = (flags & android.view.inputmethod.InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
      val asked = !granted || (try { content.requestPermission(); true } catch { case _: Exception => false })
      val description = content.getDescription
      val mime = if (description != null && description.getMimeTypeCount > 0) description.getMimeType(0) else ""
      if (asked) FileAccess.pasted(content.getContentUri, mime, () => if (granted) content.releasePermission())(file =>
        pastingFiles.foreach(_._2(List(file))))
      asked
    }

  // whether the field takes a paste's pictures and files now
  private def takesFiles: Boolean = pastingFiles.exists(_._1())

  // what hands over each of the clipboard's documents (its items with a `content:` address of a type not words),
  // where the field takes them
  private def clipboardFiles(): List[() => Unit] = if (!takesFiles) Nil else {
    val manager = getContext.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    val clip = if (manager == null) null else manager.getPrimaryClip
    if (clip == null) Nil
    else (0 until clip.getItemCount).toList.flatMap(i => Option(clip.getItemAt(i).getUri))
      .filter(uri => uri.getScheme == "content").flatMap { uri =>
        val mime = Option(getContext.getContentResolver.getType(uri)).getOrElse("")
        if (mime.isEmpty || mime.startsWith("text/")) None
        else Some(() => FileAccess.pasted(uri, mime, () => ())(file => pastingFiles.foreach(_._2(List(file)))))
      }
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

  // the clipboard's words, as Android's own paste joins them; empty where it holds none; `bare`, those of its items
  // with no address (their documents going as files)
  private def clipboard(bare: Boolean = false): String = {
    val manager = getContext.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    val clip = if (manager == null) null else manager.getPrimaryClip
    if (clip == null) ""
    else (0 until clip.getItemCount).map(clip.getItemAt).filter(item => !bare || item.getUri == null)
      .flatMap(item => Option(item.coerceToText(getContext))).map(_.toString)
      .filter(_.nonEmpty).mkString("\n")
  }
}

object FieldView {
  // the fields on the screen as the autofill session began, by identity, held no longer than they are; and whether that
  // is known yet (Android begins its session as the first fields are laid out)
  private val inSession =
    java.util.Collections.newSetFromMap(new java.util.WeakHashMap[FieldView, java.lang.Boolean]())
  private var noted = false
  private var entering = false

  /** The fields there as the first are laid out: those Android's session, begun then, was asked about. */
  private[render] def attached(field: FieldView): Unit = if (!noted) {
    noted = true
    field.post(() => note(field))
  }

  /** A fresh autofill request where `field` came since the last session began: that one cancelled (finished as it is
    * once a password manager answers with nothing, it hears no field again) and `field` asked about by hand, so a
    * form shown later in the one activity (a sign-in card in a chat, long after launch) is asked about as the first
    * was. A field there as the session began keeps it, its fill dialog's answer with it. Once for each field come
    * since, and never within itself, whatever the cancel sets off: it cannot go round. */
  private[render] def newSessionFor(field: FieldView): Unit = if (android.os.Build.VERSION.SDK_INT >= 26 &&
      !entering && !inSession.contains(field)) {
    entering = true
    try {
      val fresh = noted
      note(field)
      val manager = field.getContext.getSystemService(classOf[android.view.autofill.AutofillManager])
      if (fresh && manager != null && manager.isEnabled) {
        manager.cancel()
        // given an autofill id of its own, new to Android, before the focus comes to it: Android entered every field on
        // the screen as the card came (the request it makes as fields are laid out), and once that session finished, a
        // focus on any of them was ignored as "already entered", which no cancel undoes. A field Android has not met is
        // asked about as its focus comes, as an ordinary request (one asked by hand, a password manager answered with
        // nothing). An id is given only to a view out of its window: taken out and put back where it was, as it was.
        if (android.os.Build.VERSION.SDK_INT >= 28 && !field.hasFocus) {
          val id = manager.getNextAutofillId
          if (id != null) field.getParent match {
            case parent: android.view.ViewGroup =>
              val at = parent.indexOfChild(field)
              val params = field.getLayoutParams
              parent.removeView(field)
              field.setAutofillId(id)
              parent.addView(field, at, params)
            case _ => ()
          }
        }
      }
    } finally entering = false
  }

  // the fields on the screen now as those of the session
  private def note(field: FieldView): Unit = {
    noted = true
    inSession.clear()
    shown(field.getRootView).foreach(inSession.add)
    inSession.add(field)
  }

  // the fields under `view`
  private def shown(view: android.view.View): List[FieldView] = view match {
    case f: FieldView => List(f)
    case g: android.view.ViewGroup => (0 until g.getChildCount).toList.flatMap(i => shown(g.getChildAt(i)))
    case _ => Nil
  }
}
