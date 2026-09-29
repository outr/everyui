package everyui.text

import everyui.component.*
import everyui.component.types.{Autofill, WhiteSpace}
import everyui.event.{Key, KeyEvent}
import everyui.keymap.Chord
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A box of lines to type in: a commit message, a note. What it holds wraps to its width and scrolls when it is longer
  * than the box; `placeholder` shows while it is empty. `value` is what it holds, both ways. Enter starts a new line,
  * and Primary+Enter (Ctrl, or Cmd on a Mac) is heard on `submitted`. With `grow`, the box is as tall as its lines,
  * from `minHeight` up to `maxHeight`, and scrolls past that.
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
  /** What it asks for, so the platform's autofill and a password manager can fill it (`Autofill`); None says nothing. */
  val autofill: Var[Option[Autofill]] = own(Var(None))
  /** Which of a form's groups it belongs to ("shipping", "billing": `InputView.section`). */
  val section: Var[String] = own(Var(""))
  /** Its name to autofill and password managers (`InputView.fieldName`); empty, it is named by its hint. */
  val fieldName: Var[String] = own(Var(""))

  private val native = Platform.nativeInput()
  private val direct = Platform.keyboardSupport()
  private val pad = 10.0
  // the lines as they wrap, measured out of sight: how tall the box grows
  private val ruler = new TextView {
    whiteSpace @= WhiteSpace.Normal
    font.size @= points
    opacity @= 0.0
    content := value() + " "
    size.width := TextArea.this.size.width() - pad * 2.0
  }
  /** How tall its lines ask it to be, with its padding: what `grow` follows, between `minHeight` and `maxHeight`. */
  val lines: Val[Double] = own(Val(ruler.size.height() + pad * 2.0))
  /** The platform's own field it wraps, where the platform has one (not a Roku's or an Apple TV's): for its caret. */
  val field: Option[InputView] = if (native) Some(new InputView(false, multiline = true)) else None

  focusable @= true
  radius @= 8.0
  size.width @= width
  size.height := (if (grow) math.max(minHeight, math.min(maxHeight, lines())) else minHeight)
  borderWidth := (if (focused()) 2.5 else 1.0)
  borderColor := (if (focused()) Theme.accent() else Theme.border())
  backgroundColor := (if (focused()) Theme.controlLit() else Theme.raised())
  children += ruler

  field.foreach { input =>
    input.font.size @= points
    input.color := Theme.text()
    input.position.left @= pad
    input.position.top @= pad
    input.size.width := size.width() - pad * 2.0
    input.size.height := size.height() - pad * 2.0
    input.placeholder := hint()
    input.autofill := autofill()
    input.section := section()
    input.fieldName := fieldName()
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
    children += input
  }
  if (!native) {
    val shown = new TextView {
      whiteSpace @= WhiteSpace.Normal
      font.size @= points
      color := (if (value().isEmpty) Theme.muted() else Theme.text())
      content := (if (value().isEmpty) hint() else value())
      position.left @= pad
      position.top @= pad
      size.width := TextArea.this.size.width() - pad * 2.0
    }
    node.setClip(width, minHeight)
    size.height.attach(h => node.setClip(size.width(), h))
    event.click.attach(_ => Platform.editField(hint(), value(), false, autofill().map(Autofill.token).getOrElse(""), v => value @= v))
    children += shown
  }
  event.key.down.attach(k => if (editing() && TextArea.submit.matches(k)) submitted @= value())

  /** Primary+Enter is its own, and the platform's field never sees it. */
  override def keepsEvent(e: KeyEvent): Boolean = native && editing() && TextArea.submit.matches(e)

  /** While editing, the keys a box of lines types with and moves its caret by go on to the platform's field. */
  override def typesEvent(e: KeyEvent): Boolean = native && editing() && TextArea.typed(e.key)

  override def kind: String = "TextArea"
}

object TextArea {
  /** What sends it: Primary+Enter. */
  val submit: Chord = Chord("Enter", primary = true)

  /** The keys a box of lines types with or moves its caret by, which the focus layer would otherwise use. */
  def typed(key: Key): Boolean = key == Key.Enter || key == Key.ArrowUp || key == Key.ArrowDown ||
    key == Key.ArrowLeft || key == Key.ArrowRight || key == Key.PageUp || key == Key.PageDown || key == Key.Space ||
    key == Key.Backspace
}
