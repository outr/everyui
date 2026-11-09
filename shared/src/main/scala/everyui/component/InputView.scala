package everyui.component

import everyui.component.feature.FontFeature
import everyui.component.types.{Autofill, PasteAction, Prop, TextDirection, TextRange}
import everyui.file.PickedFile
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
  /** Holds digits (a PIN, a code, an amount of whole things), for which a phone shows its number pad rather than its
    * letters, whatever it is filled with: `autofill` is unchanged, so a parental PIN can be numeric and not offered a
    * text message's code (`Autofill.OneTimeCode` is numeric already), and a `secure` one stays hidden. The web says it
    * as `inputmode="numeric"`, Android as a number field (a password one where it is hidden), which keeps letters
    * out; elsewhere, and on the web, a keyboard or a paste can still type letters, so a field that must hold digits
    * alone checks them (`pasted`, the form's rules). The desktop has no keyboard of its own, and a Roku's and an
    * Apple TV's is the platform's. Its words run left to right, as a `literal` field's do. */
  val numeric: Var[Boolean] = own(Var(false))
  /** Which way its words run: by default (`TextDirection.Inherit`) the app's, except in a `literal` or `numeric` field
    * and one asking for a sign-in, an email address, a phone number, a web address or a code (`Autofill.leftToRight`),
    * whose words run left to right in every language, so "*.scala" or "name@host.com" reads as typed in Arabic too;
    * `TextDirection.Auto` runs them by their own first letter with a direction (a query in either script). The field
    * itself sits where the app's direction puts it, and while empty it reads as the app does, so its placeholder is
    * in the app's direction. A Roku's and an Apple TV's fields are only their words there, so it changes nothing
    * there. */
  val direction: Var[TextDirection] = own(Var(TextDirection.Inherit))
  /** Which way its words run in the end: `direction`, else left to right where `literal` or `asked` says so, and the
    * app's while it is empty. */
  val runs: Val[TextDirection] = own(Val {
    val chosen = if (direction() != TextDirection.Inherit) direction()
      else if (literal() || numeric() || asked().exists(Autofill.leftToRight)) TextDirection.LeftToRight
      else TextDirection.Inherit
    if (text().isEmpty) TextDirection.Inherit else chosen
  })
  /** Its name to autofill and password managers, the same on every visit ("email", "login-password"); empty, it is
    * named by its hint. */
  val fieldName: Var[String] = own(Var(""))
  /** What a paste becomes, asked with the words on the clipboard before they land: the keyboard's paste, the context
    * menu's, and a phone keyboard's where it says it is pasting (`PasteAction`: they land, other words in their place,
    * or nothing). None, the default, lets every paste land. A Roku and an Apple TV have no pasting, so it is never
    * asked there. */
  val pasted: Var[Option[String => PasteAction]] = own(Var(None))
  /** Where the pictures and files of a paste go, each handed over as a dropped file is (`PickedFile`): a picture copied
    * from a screenshot tool or an image viewer, a file copied in a file manager, and on Android a picture a keyboard
    * inserts (its GIFs and stickers). A picture with no name of its own is named "pasted-image-1.png" (the number
    * counting the field's pasted pictures, the extension its type's). The clipboard's words, where it holds some
    * beside them, still paste as words (`pasted` asked about them as ever). None, the default, takes no pictures or
    * files, and a paste of them does what the platform's field does with one (nothing, in a plain field). The web
    * reads them from the paste event, the desktop from JavaFX's clipboard (a picture encoded as a PNG), Android from
    * a paste of a `content:` address and a keyboard's content (`commitContent`); a Roku and an Apple TV have none. A
    * read-only field takes none. */
  val pastedFiles: Var[Option[PickedFile => Unit]] = own(Var(None))
  // the pictures with no name of their own pasted here so far, for the next one's number
  private var pictures = 0

  private def hint(): Unit =
    node.setAutofill(asked().map(Autofill.token).getOrElse(""), section(), fieldName(), passkey(), literal(), numeric())

  /** Choose all of what it holds, so what is typed next replaces it (a value being changed rather than added to). */
  def selectAll(): Unit = node.selectAllInput()

  /** Where the caret is, in characters from the start of `text` (where the platform has no caret, 0). */
  def caret: Int = node.getCaret()

  /** The caret put at `at`, in characters from the start of `text`, nothing chosen. */
  def moveCaret(at: Int): Unit = node.setCaret(at)

  /** What is chosen now, in characters of `text` (start before end); the caret alone where nothing is. Where the
    * platform has no caret of its own (a Roku, an Apple TV), the end of the words. */
  def selection: TextRange = {
    val (a, b) = (node.getSelectionStart(), node.getSelectionEnd())
    TextRange(math.min(a, b), math.max(a, b))
  }

  /** What is chosen, followed as the person or the app changes it: by the pointer, the keys, typing, or `select`. */
  val selected: Val[TextRange] = selectedNow
  private lazy val selectedNow: Var[TextRange] = own(Var(TextRange(0, 0)))

  /** `start` to `end` chosen, in characters of `text` (kept within it). */
  def select(start: Int, end: Int): Unit = {
    val n = text().length
    node.setSelection(math.max(0, math.min(start, n)), math.max(0, math.min(end, n)))
  }

  /** `start` to `end` replaced by `words`, the caret after them: as if typed, so the platform's undo takes it back
    * where it has one (the web's, the desktop's), and `text` follows as it does typing. */
  def replace(start: Int, end: Int, words: String): Unit = {
    val n = text().length
    val from = math.max(0, math.min(math.min(start, end), n))
    val to = math.max(from, math.min(math.max(start, end), n))
    node.replaceInput(from, to, words)
  }

  /** The platform's focus taken again by the field this is the caret of (Component.refocus): at once, where it is being
    * edited (`editing`); else, with a keyboard at hand (`direct`), by editing it again. */
  private[everyui] def retake(editing: Var[Boolean], direct: Boolean): Unit =
    if (editing()) node.focusInput(true) else if (direct) editing @= true

  text.attach(v => if (node.getValue() != v) node.setValue(v))
  node.onValue(v => if (text() != v) text @= v)
  node.onPaste(words => pasted().map(f => f(words)).getOrElse(PasteAction.Insert))
  node.onPasteFiles(() => pastedFiles().nonEmpty,
    files => pastedFiles().foreach(f => files.foreach(file => f(named(file)))))

  // a file as pasted, a picture with no name of its own named after its number and type
  private def named(file: PickedFile): PickedFile = if (file.name.nonEmpty) file else {
    pictures += 1
    file.copy(name = PickedFile.pastedName(file.mime, pictures))
  }
  node.onSelection((a, b) => {
    val now = TextRange(math.min(a, b), math.max(a, b))
    if (selectedNow() != now) selectedNow @= now
  })
  asked.attach(_ => hint())
  passkey.attach(_ => hint())
  section.attach(_ => hint())
  fieldName.attach(_ => hint())
  literal.attach(_ => hint())
  numeric.attach(_ => hint())
  runs.attach(d => node.setTextDirection(d.value))
  if (runs() != TextDirection.Inherit) node.setTextDirection(runs().value)
  // said at once where the app says for every field, so a field made after `Autofill.fallback` is set says it too
  if (asked().nonEmpty) hint()

  override def kind: String = "InputView"
}
