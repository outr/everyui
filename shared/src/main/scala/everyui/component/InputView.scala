package everyui.component

import everyui.component.feature.FontFeature
import everyui.component.types.{Autofill, Prop}
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
    * for it. None, the default, says nothing, and the platform guesses. A password hint hides what is typed. */
  val autofill: Var[Option[Autofill]] = own(Var(None))
  /** A username field that also offers the site's passkeys in its autofill (the web's `webauthn`), for a WebAuthn
    * request with conditional mediation to sign in with (the app's own, until EveryUI has one). */
  val passkey: Var[Boolean] = own(Var(false))
  /** Which of a form's groups it belongs to where one form asks twice ("shipping", "billing", or a name of the app's):
    * the delivery address kept apart from the billing one. */
  val section: Var[String] = own(Var(""))
  /** Its name to autofill and password managers, the same on every visit ("email", "login-password"); empty, it is
    * named by its hint. */
  val fieldName: Var[String] = own(Var(""))

  private def hint(): Unit =
    node.setAutofill(autofill().map(Autofill.token).getOrElse(""), section(), fieldName(), passkey())

  /** Choose all of what it holds, so what is typed next replaces it (a value being changed rather than added to). */
  def selectAll(): Unit = node.selectAllInput()

  /** Where the caret is, in characters from the start of `text` (where the platform has no caret, 0). */
  def caret: Int = node.getCaret()

  /** The caret put at `at`, in characters from the start of `text`, nothing chosen. */
  def moveCaret(at: Int): Unit = node.setCaret(at)

  text.attach(v => if (node.getValue() != v) node.setValue(v))
  node.onValue(v => if (text() != v) text @= v)
  autofill.attach(_ => hint())
  passkey.attach(_ => hint())
  section.attach(_ => hint())
  fieldName.attach(_ => hint())

  override def kind: String = "InputView"
}
