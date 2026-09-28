package everyui.component

import everyui.event.Key
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A labelled text field, `width` wide (bind `size.width` to follow a form): its label in the corner, what it holds
  * under it, and an edge that lights with the focus. `value` is what it holds, both ways; Enter while typing in it
  * fires `submitted`.
  *
  * Where the platform has a text field of its own (a browser, a desktop, a phone) the field is that. With a keyboard at
  * hand the focus moving onto it puts the caret there. Where the keyboard is on the screen (a TV remote, a phone) the
  * focus only lights the field, and Enter or a tap starts `editing` and brings the keyboard up, so moving across a form
  * never opens it; the keyboard's Done or Back ends it. Where it has none (a Roku, an Apple TV) the field shows its
  * value, and Enter or a click asks the platform's keyboard for a new one (`Platform.editText`). */
class TextInput(label: String, secure: Boolean = false, width: Double = 320.0) extends Container {
  val value: Var[String] = own(Var(""))
  /** What the label says; bind it to change it (a language change, say). */
  val title: Var[String] = own(Var(label))
  val submitted: Channel[String] = Channel[String]
  /** Whether the caret is in it. */
  val editing: Var[Boolean] = own(Var(false))

  private val native = Platform.nativeInput()
  /** A keyboard at hand: the focus is the caret. */
  private val direct = Platform.keyboardSupport()
  private val caption = new TextView {
    content := title()
    font.size @= 12.0
    color := (if (TextInput.this.focused()) Theme.accent() else Theme.muted())
    position.left @= 14.0
    position.top @= 7.0
    // as wide as the field, so the label starts where the reading direction does
    size.width := TextInput.this.size.width() - 28.0
  }

  focusable @= true
  radius @= 10.0
  size.width @= width
  size.height @= 56.0
  borderWidth := (if (focused()) 2.5 else 1.0)
  borderColor := (if (focused()) Theme.accent() else Theme.border())
  backgroundColor := (if (focused()) Theme.controlLit() else Theme.raised())

  if (native) {
    val input = new InputView(secure) {
      font.size @= 16.0
      color := Theme.text()
      position.left @= 14.0
      position.top @= 26.0
      size.width := TextInput.this.size.width() - 28.0
      size.height @= 22.0
    }
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
    event.key.down.attach(k => if (k.key == Key.Enter && editing()) submitted @= value())
    children ++= List(caption, input)
  } else {
    val shown = TextView.bound(if (secure) TextInput.mask(value().length) else value(), 16.0)
    shown.position.left @= 14.0
    shown.size.width := size.width() - 28.0
    shown.position.top @= 26.0
    event.click.attach(_ => Platform.editText(title(), value(), secure, v => {
      value @= v
      submitted @= v
    }))
    children ++= List(caption, shown)
  }

  /** A field being edited types Enter and the keys that move along its line, which go on to the platform's field (kept
    * from it, as they were, Backspace deleted nothing and the arrows moved no caret); the rest still move the focus. One
    * that is only focused types nothing, so Enter clicks it, which starts editing. */
  override def typesEvent(e: everyui.event.KeyEvent): Boolean =
    native && editing() && (e.key == Key.Enter || e.key == Key.ArrowLeft || e.key == Key.ArrowRight || e.key == Key.Space ||
      e.key == Key.Backspace)
}

object TextInput {
  /** A field `width` wide whose label follows what it reads, bound both ways to `value`; Enter in it runs `submit`. */
  def bound(label: => String, value: Var[String], secure: Boolean = false, width: Double = 320.0)
           (submit: => Unit): TextInput = {
    val input = new TextInput("", secure, width)
    input.title := label
    input.value @= value()
    input.value.attach(v => if (value() != v) value @= v)
    value.attach(v => if (input.value() != v) input.value @= v)
    input.submitted.attach(_ => submit)
    input
  }

  /** `length` dots: a secret shown without being shown. */
  def mask(length: Int): String = {
    var dots = ""
    var i = 0
    while (i < length) {
      dots = dots + "•"
      i += 1
    }
    dots
  }
}
