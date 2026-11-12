package everyui.component

import everyui.chat.Completion
import everyui.component.types.{Autofill, PasteAction, Semantics, TextDirection}
import everyui.event.Key
import everyui.file.PickedFile
import everyui.render.Platform
import everyui.text.{CompletionSource, FieldCompletion}
import everyui.theme.Theme
import reactify.*

/** A labelled text field, `width` wide (bind `size.width` to follow a form): its label in the corner, what it holds
  * under it, and an edge that lights with the focus. `value` is what it holds, both ways; Enter while typing in it
  * fires `submitted`. `invalid` ("Enter an email address") marks what it holds wrong: its edge and its label in the
  * theme's danger, and a screen reader told so and why.
  *
  * Where the platform has a text field of its own (a browser, a desktop, a phone) the field is that. With a keyboard at
  * hand the focus moving onto it puts the caret there. Where the keyboard is on the screen (a TV remote, a phone) the
  * focus only lights the field, and Enter or a tap starts `editing` and brings the keyboard up, so moving across a form
  * never opens it; the keyboard's Done or Back ends it. Where it has none (a Roku, an Apple TV) the field shows its
  * value, and Enter or a click asks the platform's keyboard for a new one (`Platform.editText`); what is typed while it
  * has the focus (a Roku's phone app, its voice entry: `Container.typeInto`) goes straight in. */
class TextInput(label: String, secure: Boolean = false, width: Double = 320.0) extends Container {
  val value: Var[String] = own(Var(""))
  /** What the label says; bind it to change it (a language change, say). */
  val title: Var[String] = own(Var(label))
  val submitted: Channel[String] = Channel[String]
  /** Whether the caret is in it. */
  val editing: Var[Boolean] = own(Var(false))
  /** What it asks for, so the platform's autofill and a password manager can fill it (`Autofill`); None says nothing. */
  val autofill: Var[Option[Autofill]] = own(Var(None))
  /** A username field that also offers the site's passkeys in its autofill (`InputView.passkey`). */
  val passkey: Var[Boolean] = own(Var(false))
  /** Which of a form's groups it belongs to ("shipping", "billing": `InputView.section`). */
  val section: Var[String] = own(Var(""))
  /** Holds text meant as it is typed (a path, a command, code), never spell-checked, corrected or capitalized
    * (`InputView.literal`). */
  val literal: Var[Boolean] = own(Var(false))
  /** Holds digits (a PIN, a code), on a phone's number pad rather than its letters (`InputView.numeric`). */
  val numeric: Var[Boolean] = own(Var(false))
  /** Which way its words run (`InputView.direction`): by default the app's, left to right where `literal`. */
  val direction: Var[TextDirection] = own(Var(TextDirection.Inherit))
  /** Its name to autofill and password managers (`InputView.fieldName`); empty, it is named by its hint. */
  val fieldName: Var[String] = own(Var(""))
  /** Shows `value`, to be focused, chosen and copied, but not changed (`InputView.readOnly`); where the platform has
    * no field of its own (a Roku, an Apple TV) Enter or a click asks for no new value. */
  val readOnly: Var[Boolean] = own(Var(false))
  /** What a paste becomes before it lands (`InputView.pasted`); None lets it land. */
  val pasted: Var[Option[String => PasteAction]] = own(Var(None))
  /** Where a paste's pictures and files go (`InputView.pastedFiles`); None takes none. */
  val pastedFiles: Var[Option[PickedFile => Unit]] = own(Var(None))
  /** Where what is typed in it is completed from (`CompletionSource`): the piece being typed (the whole field, or what
    * lies between its separators) asked about as it changes, and the answers listed under the field, Down and Up
    * moving through them, Enter, Tab, a click or a tap putting the lit one in the piece's place (heard on `completed`),
    * and Escape closing the list alone. None, the default, offers nothing. Only where the platform has a field of its
    * own (a browser, a desktop, a phone): a remote has no typing to complete. */
  val completions: Var[Option[CompletionSource]] = own(Var(None))
  /** The completion chosen from its list, once it is in the field. */
  val completed: Channel[Completion] = Channel[Completion]
  /** Whether its list of completions shows. */
  val completing: Val[Boolean] = completingNow
  private lazy val completingNow: Var[Boolean] = own(Var(false))

  // a secret, or asked for as a password
  private def hidden: Boolean = secure || autofill().exists(Autofill.secret)
  private val native = Platform.nativeInput()
  /** A keyboard at hand: the focus is the caret. */
  private val direct = Platform.keyboardSupport()
  // the platform's field, where there is one: its focus taken again (`refocus`)
  private var platformField: Option[InputView] = None
  // its list of completions, made the first time it is given a source
  private var completer: Option[FieldCompletion] = None
  /** The platform's own field it wraps, where the platform has one (not a Roku's or an Apple TV's): for its caret and
    * what is chosen in it (`InputView.selection`, `select`, `replace`, `selected`). */
  def field: Option[InputView] = platformField
  private val caption = new TextView {
    content := title()
    font.size @= 12.0
    color := (if (TextInput.this.invalid().nonEmpty) Theme.dangerText()
      else if (TextInput.this.focused()) Theme.accentText() else Theme.muted())
    position.left @= 14.0
    position.top @= 7.0
    // as wide as the field, so the label starts where the reading direction does
    size.width := TextInput.this.size.width() - 28.0
  }

  focusable @= true
  clickFocus @= true
  radius @= 10.0
  size.width @= width
  size.height @= 56.0
  borderWidth := (if (focused()) 2.5 else 1.0)
  borderColor := (if (invalid().nonEmpty) Theme.danger() else if (focused()) Theme.accent() else Theme.edge())
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
    input.autofill := autofill()
    input.passkey := passkey()
    input.section := section()
    input.fieldName := fieldName()
    input.readOnly := readOnly()
    input.literal := literal()
    input.numeric := numeric()
    input.direction := direction()
    input.pasted := pasted()
    input.pastedFiles := pastedFiles()
    // named by its label, which is drawn beside it rather than being its own; a combo box where it offers completions,
    // saying whether their list shows
    input.semantics := Semantics(if (completions().nonEmpty) "combobox" else "", label = title(),
      expanded = if (completions().nonEmpty) Some(completing()) else None)
    // and described as it is, since the focus a screen reader follows is the platform's field
    input.accessibleDescription := accessibleDescription()
    // and said to be wrong, and why, on the field a screen reader is on
    input.invalid := invalid()
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
    // a key the list of completions takes (Enter choosing from it) is not the field's
    event.key.down.attach(k => if (!completer.exists(_.key(k)) && k.key == Key.Enter && editing()) submitted @= value())
    completions.attach(s => if (s.nonEmpty && completer.isEmpty) completer = Some(new FieldCompletion(this, input,
      value, editing, completions, completingNow, completed, 16.0, () => title())))
    children ++= List(caption, input)
    platformField = Some(input)
  } else {
    val shown = TextView.bound(if (hidden) TextInput.mask(value().length) else value(), 16.0)
    shown.position.left @= 14.0
    shown.size.width := size.width() - 28.0
    shown.position.top @= 26.0
    event.click.attach(_ => if (!readOnly())
      Platform.editField(title(), value(), hidden, Autofill.resolve(autofill()).map(Autofill.token).getOrElse(""),
        numeric(), v => {
        value @= v
        submitted @= v
      }))
    children ++= List(caption, shown)
    // typed straight in as well, where something types without the platform's keyboard (a Roku's phone app, its voice
    // entry): after the words, Backspace taking off the last, Enter submitting
    typeInto(new everyui.focus.TextEntry {
      def typed(s: String): Unit = if (!readOnly()) value @= value() + s
      def erase(): Unit = if (!readOnly() && value().nonEmpty) value @= value().substring(0, value().length - 1)
      def enter(): Unit = submitted @= value()
    })
  }

  override private[everyui] def refocus(): Unit = platformField.foreach(_.retake(editing, direct))

  /** The keys its list of completions takes while it shows (`CompletionKey`): Down, Up, Enter, Tab and Escape, and
    * Shift+Delete where the lit row has an action that removes it. */
  override def keepsEvent(e: everyui.event.KeyEvent): Boolean = completer.exists(_.keeps(e)) || super.keepsEvent(e)

  override def dispose(): Unit = {
    completer.foreach(_.dispose())
    completer = None
    super.dispose()
  }

  /** A field being edited types Enter and the keys that move along its line, which go on to the platform's field (kept
    * from it, as they were, Backspace deleted nothing and the arrows moved no caret); the rest still move the focus. With
    * a keyboard at hand (a browser, a desktop) the up and down arrows are the field's too, as they are in any text field
    * there, and where the browser's or a password manager's autofill menu under the field is worked by them; Tab moves
    * on. One that is only focused types nothing, so Enter clicks it, which starts editing. */
  override def typesEvent(e: everyui.event.KeyEvent): Boolean =
    native && editing() && (e.key == Key.Enter || e.key == Key.ArrowLeft || e.key == Key.ArrowRight || e.key == Key.Space ||
      e.key == Key.Backspace || (direct && (e.key == Key.ArrowUp || e.key == Key.ArrowDown)))

  override def takesTyping: Boolean = true
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
