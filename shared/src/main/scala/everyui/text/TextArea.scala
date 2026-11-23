package everyui.text

import everyui.component.*
import everyui.component.feature.FontFeature
import everyui.component.types.{Autofill, PasteAction, Semantics, TextDirection, WhiteSpace}
import everyui.event.{Key, KeyEvent}
import everyui.file.PickedFile
import everyui.keymap.Chord
import everyui.markdown.MarkdownView
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A box of lines to type in: a commit message, a note. What it holds wraps to its width (`code` keeps each line
  * whole, scrolled sideways) and scrolls when it is longer than the box; `placeholder` shows while it is empty.
  * `value` is what it holds, both ways. Enter starts a new line, and Primary+Enter (Ctrl, or Cmd on a Mac) is heard
  * on `submitted`. With `grow`, the box is as tall as its lines, from `minHeight` up to `maxHeight`, and scrolls past
  * that.
  *
  * While it is being edited the keys it types with (Enter, the arrows, the page keys, Backspace) are its own, and the
  * focus layer makes no other use of them; Tab and Escape still move the focus and go back. Where the keyboard is on
  * the screen (a TV remote, a phone) the focus only lights it, and Enter or a tap starts editing, as a [[TextInput]]
  * does; where the platform has no field of its own (a Roku, an Apple TV) it shows what it holds, and Enter or a click
  * asks the platform's keyboard for new words.
  *
  * {{{
  *   val message = new TextArea(placeholder = "Commit message", width = 360.0, grow = true)
  *   message.value := draft()
  *   message.submitted.attach(text => commit(text))
  * }}} */
class TextArea(placeholder: String = "", width: Double = 360.0, grow: Boolean = false, val minHeight: Double = 72.0,
               val maxHeight: Double = 240.0, points: Double = 14.0) extends Container {
  val value: Var[String] = own(Var(""))
  /** Primary+Enter, with what it holds. */
  val submitted: Channel[String] = Channel[String]
  /** Whether the caret is in it. */
  val editing: Var[Boolean] = own(Var(false))
  /** What it says while empty; bind it to change it. */
  val hint: Var[String] = own(Var(placeholder))
  /** What a screen reader names the field (and a test finds it by): its placeholder (`hint`) unless this says
    * otherwise, as a TextInput is named by its label. */
  val label: Var[String] = own(Var(""))
  /** What it asks for, so the platform's autofill and a password manager can fill it (`Autofill`); None says nothing. */
  val autofill: Var[Option[Autofill]] = own(Var(None))
  /** Which of a form's groups it belongs to ("shipping", "billing": `InputView.section`). */
  val section: Var[String] = own(Var(""))
  /** Its name to autofill and password managers (`InputView.fieldName`); empty, it is named by its hint. */
  val fieldName: Var[String] = own(Var(""))
  /** Shows `value`, to be focused, chosen and copied, but not changed (`InputView.readOnly`); where the platform has
    * no field of its own (a Roku, an Apple TV) Enter or a click asks for no new words. */
  /** Holds text meant as it is typed (code, a script), never spell-checked, corrected or capitalized
    * (`InputView.literal`). */
  val literal: Var[Boolean] = own(Var(false))
  /** Code, to read or to edit: in EveryUI's monospaced face (`TextArea.codeFace`), each line kept whole and scrolled
    * sideways rather than wrapped, a tab's stops `TextArea.codeTabs` columns apart, `literal`, and on the wash a
    * Markdown code block is drawn on (`MarkdownView.codeColour`), as one looks. Where the platform has no field of its
    * own (a Roku, an Apple TV) its tabs are spaces to the next stop, and a line too long for the box wraps. */
  val code: Var[Boolean] = own(Var(false))
  /** Which way its words run (`InputView.direction`): by default the app's, left to right where `literal`. */
  val direction: Var[TextDirection] = own(Var(TextDirection.Inherit))
  val readOnly: Var[Boolean] = own(Var(false))
  /** What a paste becomes before it lands (`InputView.pasted`); None lets it land. */
  val pasted: Var[Option[String => PasteAction]] = own(Var(None))
  /** Where a paste's pictures and files go (`InputView.pastedFiles`); None takes none. */
  val pastedFiles: Var[Option[PickedFile => Unit]] = own(Var(None))
  /** Where what is typed in it is completed from (`CompletionSource`), as a [[everyui.component.TextInput]]'s is: the
    * piece at the caret (the whole box, or what lies between its separators) asked about as it changes, and the
    * answers listed under the box. While they show, Down and Up move through them, Enter and Tab (a click, a tap) put
    * the lit one in the piece's place (heard on `completed`), and Escape closes the list alone; without them Enter is
    * a new line again. None, the default, offers nothing. Only where the platform has a field of its own. */
  val completions: Var[Option[CompletionSource]] = own(Var(None))
  /** The completion chosen from its list, once it is in the box. */
  val completed: Channel[everyui.chat.Completion] = Channel[everyui.chat.Completion]
  /** Whether its list of completions shows. */
  val completing: Val[Boolean] = completingNow
  private lazy val completingNow: Var[Boolean] = own(Var(false))

  private val native = Platform.nativeInput()
  private val direct = Platform.keyboardSupport()
  // its list of completions, made the first time it is given a source
  private var completer: Option[FieldCompletion] = None
  private val pad = 10.0
  // the lines as they wrap and as Enter broke them, measured out of sight: how tall the box grows. Code in the
  // platform's field is not wrapped: as many lines as it is written as, each measured as one letter
  private val ruler = new TextView {
    whiteSpace @= WhiteSpace.PreWrap
    font.size @= points
    opacity @= 0.0
    content := (if (code() && native) TextArea.rows(value()) else value()) + " "
    size.width := TextArea.this.size.width() - pad * 2.0
  }
  look(ruler, ruler.font, whole = false)
  /** How tall its lines ask it to be, with its padding: what `grow` follows, between `minHeight` and `maxHeight`; with
    * room under code's for the bar that scrolls it sideways. */
  val lines: Val[Double] = own(Val(ruler.size.height() + pad * 2.0 + (if (code() && native) TextArea.barRoom else 0.0)))
  /** The platform's own field it wraps, where the platform has one (not a Roku's or an Apple TV's): for its caret. */
  val field: Option[InputView] = if (native) Some(new InputView(false, multiline = true)) else None

  focusable @= true
  clickFocus @= true
  radius @= 8.0
  size.width @= width
  size.height := (if (grow) math.max(minHeight, math.min(maxHeight, lines())) else minHeight)
  borderWidth := (if (focused()) 2.5 else 1.0)
  borderColor := (if (focused()) Theme.accent() else Theme.edge())
  backgroundColor := (if (focused()) Theme.controlLit() else if (code()) MarkdownView.codeColour() else Theme.raised())
  children += ruler

  field.foreach { input =>
    input.font.size @= points
    input.color := Theme.text()
    input.position.left @= pad
    input.position.top @= pad
    input.size.width := size.width() - pad * 2.0
    input.size.height := size.height() - pad * 2.0
    input.placeholder := hint()
    input.semantics := Semantics(label = if (label().nonEmpty) label() else hint())
    input.autofill := autofill()
    input.section := section()
    input.fieldName := fieldName()
    input.readOnly := readOnly()
    input.pasted := pasted()
    input.pastedFiles := pastedFiles()
    input.literal := literal() || code()
    look(input, input.font)
    input.direction := direction()
    input.text.attach(v => if (value() != v) value @= v)
    value.attach(v => if (input.text() != v) input.text @= v)
    focused.attach(f => if (direct || !f) editing @= f)
    editing.attach(e => input.node.focusInput(e))
    input.node.onInputFocus { () =>
      focus()
      editing @= true
    }
    input.node.onInputDone(() => editing @= false)
    event.click.attach(_ => editing @= true)
    // a key the list of completions takes is heard by it alone
    event.key.down.attach(k => completer.foreach(_.key(k)))
    completions.attach(s => if (s.nonEmpty && completer.isEmpty) completer = Some(new FieldCompletion(this, input,
      value, editing, completions, completingNow, completed, points, () => if (label().nonEmpty) label() else hint())))
    children += input
  }
  if (!native) {
    val shown = new TextView {
      font.size @= points
      color := (if (value().isEmpty) Theme.muted() else Theme.text())
      content := (if (value().isEmpty) hint() else if (code()) TextArea.spaced(value(), TextArea.codeTabs) else value())
      position.left @= pad
      position.top @= pad
      size.width := TextArea.this.size.width() - pad * 2.0
    }
    // (a long line wrapped: a Roku's words unwrapped are one line, cut short)
    look(shown, shown.font, whole = false)
    clipTo(width, minHeight)
    size.height.attach(h => clipTo(size.width(), h))
    event.click.attach(_ => if (!readOnly())
      Platform.editField(hint(), value(), false, Autofill.resolve(autofill()).map(Autofill.token).getOrElse(""), false,
        v => value @= v))
    children += shown
  }
  event.key.down.attach(k => if (editing() && TextArea.submit.matches(k)) submitted @= value())

  // its words in code's face, with code's tab stops and each line kept `whole` while it is `code`, else as a note's
  private def look(words: Component, font: FontFeature, whole: Boolean = true): Unit = {
    val plain = font.family()
    font.family := (if (code()) TextArea.codeFace else plain)
    words.whiteSpace := (if (code() && whole) WhiteSpace.Pre else WhiteSpace.PreWrap)
    words.tabSize := (if (code()) TextArea.codeTabs else 8)
  }

  /** Primary+Enter is its own, and the platform's field never sees it; and while its list of completions shows, the
    * keys the list takes (`CompletionKey`). */
  override def keepsEvent(e: KeyEvent): Boolean =
    (native && editing() && TextArea.submit.matches(e)) || completer.exists(_.keeps(e))

  override def dispose(): Unit = {
    completer.foreach(_.dispose())
    completer = None
    super.dispose()
  }

  override private[everyui] def refocus(): Unit = field.foreach(_.retake(editing, direct))

  /** While editing, the keys a box of lines types with and moves its caret by go on to the platform's field. */
  override def typesEvent(e: KeyEvent): Boolean = native && editing() && TextArea.typed(e.key)

  override def takesTyping: Boolean = true

  override def kind: String = "TextArea"
}

object TextArea {
  /** What sends it: Primary+Enter. */
  val submit: Chord = Chord("Enter", primary = true)
  /** The face code is set in (`code`): EveryUI's monospaced one, a Markdown code block's. */
  val codeFace: String = "Roboto Mono"
  /** How many columns apart a tab's stops are in code. */
  val codeTabs: Int = 4
  // the room a bar that scrolls code sideways takes under its lines (the desktop's is 12, a browser's thin one less)
  private val barRoom: Double = 16.0

  /** `text` as one letter for each of its lines: measured, as tall as it unwrapped. */
  def rows(text: String): String = {
    var out = "M"
    var at = text.indexOf("\n")
    while (at >= 0) {
      out = out + "\nM"
      at = text.indexOf("\n", at + 1)
    }
    out
  }

  /** `text` with each tab spaces to the next stop, `columns` apart: where a tab is not set out (a Roku's words). */
  def spaced(text: String, columns: Int): String = if (text.indexOf("\t") < 0) text else {
    var out = ""
    var from = 0
    var tab = text.indexOf("\t")
    while (tab >= 0) {
      out = out + text.substring(from, tab)
      val line = out.lastIndexOf("\n") + 1
      out = out + " " * (columns - (out.length - line) % columns)
      from = tab + 1
      tab = text.indexOf("\t", from)
    }
    out + text.substring(from)
  }

  /** The keys a box of lines types with or moves its caret by, which the focus layer would otherwise use. */
  def typed(key: Key): Boolean = key == Key.Enter || key == Key.ArrowUp || key == Key.ArrowDown ||
    key == Key.ArrowLeft || key == Key.ArrowRight || key == Key.PageUp || key == Key.PageDown || key == Key.Space ||
    key == Key.Backspace
}
