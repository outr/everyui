package everyui.component

import everyui.component.feature.FontFeature
import everyui.component.types.{Autofill, PasteAction, Prop}
import everyui.render.Nodes
import reactify.*

/** The platform's own text field as a component — an `<input>`, a JavaFX `TextField`, an Android `EditText` — so
  * typing, the caret, selection, the clipboard, autofill and an input method are the platform's. `text` is what it
  * holds, both ways. A `secure` one hides what is typed; a `multiline` one is a box of lines (a `<textarea>`, where the
  * platform has one). [[TextInput]] is the field a form uses. */
class InputView(secure: Boolean = false, multiline: Boolean = false)
  extends Component(if (multiline) Nodes.textArea() else Nodes.input(secure)) {
  val text: Var[String] = own(Var(""))
  val font: FontFeature = new FontFeature(this)
  /** What it says while empty. */
  lazy val placeholder: Prop[String] = own(new Prop[String]("", p => node.setPlaceholder(p)))
  /** Shows `text`, which can be focused, chosen and copied, but takes no typing and no paste: a value to read and copy
    * (a link to share, a key). Unlike `disabled` it is in use, and it looks as it did; a screen reader hears it is
    * read-only. A Roku's and an Apple TV's fields are only their words there, so it changes nothing there.
    * {{{
    * val link = new InputView
    * link.text @= shareUrl
    * link.readOnly @= true
    * }}} */
  lazy val readOnly: Prop[Boolean] = own(new Prop[Boolean](false, r => node.setReadOnly(r)))
  /** What the keyboard's enter key says it does, where the keyboard says: "done", "search", "next", "go" or "send". */
  lazy val action: Prop[String] = own(new Prop[String]("done", a => node.setInputAction(a)))
  /** What it asks for (`Autofill`: a sign-in, a name, an email address, a phone number, an address, a card; `Off` for
    * none of these), so the platform's autofill and a password manager know to fill it, and a phone shows the keyboard
    * for it. None, the default, says nothing, and the platform guesses, unless the app says for every such field
    * (`Autofill.fallback`). A password hint hides what is typed. */
  val autofill: Var[Option[Autofill]] = own(Var(None))
  /** What it asks for in the end: `autofill`, else the app's `Autofill.fallback`. */
  val asked: Val[Option[Autofill]] = own(Val(Autofill.resolve(autofill())))
  /** A username field that also offers the site's passkeys in its autofill (the web's `webauthn`), for a WebAuthn
    * request with conditional mediation to sign in with (the app's own, until EveryUI has one). */
  val passkey: Var[Boolean] = own(Var(false))
  /** Which of a form's groups it belongs to where one form asks twice ("shipping", "billing", or a name of the app's):
    * the delivery address kept apart from the billing one. */
  val section: Var[String] = own(Var(""))
  /** Holds text meant as it is typed (a path, a command, an identifier, a pattern, a host, code), which the platform
    * neither checks the spelling of, corrects nor capitalizes, whatever it is filled with (`autofill` is unchanged).
    * The web and Android do as it says; the desktop checks no spelling anyway, and a Roku's and an Apple TV's keyboard
    * is the platform's own. */
  val literal: Var[Boolean] = own(Var(false))
  /** Its name to autofill and password managers, the same on every visit ("email", "login-password"); empty, it is
    * named by its hint. */
  val fieldName: Var[String] = own(Var(""))
  /** What a paste becomes, asked with the words on the clipboard before they land: the keyboard's paste, the context
    * menu's, and a phone keyboard's where it says it is pasting (`PasteAction`: they land, other words in their place,
    * or nothing). None, the default, lets every paste land. A Roku and an Apple TV have no pasting, so it is never
    * asked there. */
  val pasted: Var[Option[String => PasteAction]] = own(Var(None))

  private def hint(): Unit =
    node.setAutofill(asked().map(Autofill.token).getOrElse(""), section(), fieldName(), passkey(), literal())

  /** Choose all of what it holds, so what is typed next replaces it (a value being changed rather than added to). */
  def selectAll(): Unit = node.selectAllInput()

  /** Where the caret is, in characters from the start of `text` (where the platform has no caret, 0). */
  def caret: Int = node.getCaret()

  /** The caret put at `at`, in characters from the start of `text`, nothing chosen. */
  def moveCaret(at: Int): Unit = node.setCaret(at)

  /** The platform's focus taken again by the field this is the caret of (Component.refocus): at once, where it is being
    * edited (`editing`); else, with a keyboard at hand (`direct`), by editing it again. */
  private[everyui] def retake(editing: Var[Boolean], direct: Boolean): Unit =
    if (editing()) node.focusInput(true) else if (direct) editing @= true

  text.attach(v => if (node.getValue() != v) node.setValue(v))
  node.onValue(v => if (text() != v) text @= v)
  node.onPaste(words => pasted().map(f => f(words)).getOrElse(PasteAction.Insert))
  asked.attach(_ => hint())
  passkey.attach(_ => hint())
  section.attach(_ => hint())
  fieldName.attach(_ => hint())
  literal.attach(_ => hint())
  // said at once where the app says for every field, so a field made after `Autofill.fallback` is set says it too
  if (asked().nonEmpty) hint()

  override def kind: String = "InputView"
}
