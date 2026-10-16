package everyui.kit

import everyui.component.{Container, InputView, MaterialIconView, TextView}
import everyui.component.types.{Autofill, Semantics}
import everyui.event.{Key, KeyEvent}
import everyui.font.MaterialIcon
import everyui.list.ListRow
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A search box on one line, `SearchField.height` tall: a magnifier (or `glyph`) at its start, what it holds (or
  * `placeholder`, faint, while it is empty) and, once it holds something, a × that empties it. Escape empties it too;
  * an empty one lets Escape go on to the screen (Back). `value` is what it holds, both ways; Enter fires `submitted`. It
  * is one stop for the focus, whose edge it lights: the × is the pointer's, Escape the keys'.
  *
  * Where the platform has a text field of its own (a browser, a desktop, a phone) the words are typed into that, the
  * caret there as the focus arrives where a keyboard is at hand, and on Enter or a tap where the keyboard is on the
  * screen. Where it has none (a Roku, an Apple TV) Enter or a click asks the platform's keyboard for the words
  * (`Platform.editField`), and what is typed while it has the focus (a Roku's phone app: `Container.typeInto`) goes
  * straight in. The up and down arrows are not the field's: Down goes on to what is under it (a list).
  *
  * {{{
  *   val search = new SearchField()
  *   search.value.attach(words => results @= find(words))
  *   search.exitFocus(Key.ArrowDown, list)
  * }}} */
class SearchField(hint: String = everyui.i18n.Words.search, width: Double = 320.0,
                  glyph: MaterialIcon = MaterialIcon.Search) extends Container {
  val value: Var[String] = own(Var(""))
  /** What it says while empty. */
  val placeholder: Var[String] = own(Var(hint))
  val submitted: Channel[String] = Channel[String]
  /** Whether the caret is in it. */
  val editing: Var[Boolean] = own(Var(false))
  /** Holds words meant as they are typed (a pattern, a path, an identifier), never spell-checked, corrected or
    * capitalized (`InputView.literal`). */
  val literal: Var[Boolean] = own(Var(false))
  /** What it asks for, so the platform's autofill and a password manager know (`Autofill`): None says nothing, which
    * is the app's `Autofill.fallback`; `Some(Autofill.Off)` holds it out of them. */
  val autofill: Var[Option[Autofill]] = own(Var(None))

  private val native = Platform.nativeInput()
  private val direct = Platform.keyboardSupport()
  // the platform's field, where there is one: its focus taken again (`refocus`)
  private var platformField: Option[InputView] = None
  private val icon = new MaterialIconView(glyph, 18.0)
  icon.color := Theme.muted()
  private val clear = new MaterialIconView(MaterialIcon.Close, 18.0)
  clear.color := Theme.muted()
  clear.visible := value().nonEmpty
  clear.tooltip @= everyui.i18n.Words.clear
  clear.semantics @= Semantics.button(everyui.i18n.Words.clear)
  clear.tapSlop @= true
  clear.event.click.attach { e =>
    e.consume()
    empty()
  }

  focusable @= true
  radius @= 8.0
  size.width @= width
  size.height @= SearchField.height
  borderWidth := (if (focused()) 2.0 else 1.0)
  borderColor := (if (focused()) Theme.accent() else Theme.border())
  backgroundColor := (if (focused()) Theme.controlLit() else Theme.raised())
  semantics @= Semantics("search")

  // the words' room: past the magnifier, short of the ×
  private val wordsWidth: Val[Double] = own(Val(math.max(20.0, size.width() - SearchField.lead - SearchField.trail)))
  icon.position.left := ListRow.along(10.0, 18.0, size.width())
  icon.position.top := (size.height() - icon.size.height()) / 2.0
  clear.position.left := ListRow.along(size.width() - 28.0, 18.0, size.width())
  clear.position.top := (size.height() - clear.size.height()) / 2.0

  if (native) {
    val input = new InputView() {
      font.size @= 14.0
      color := Theme.text()
      size.height @= 20.0
    }
    input.size.width := wordsWidth()
    input.position.left := ListRow.along(SearchField.lead, wordsWidth(), size.width())
    input.position.top := (size.height() - 20.0) / 2.0
    input.placeholder := placeholder()
    input.literal := literal()
    input.autofill := autofill()
    input.semantics := Semantics(label = placeholder())
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
    children ++= List(icon, input, clear)
    platformField = Some(input)
  } else {
    val shown = new TextView {
      content := (if (value().isEmpty) placeholder() else value())
      color := (if (value().isEmpty) Theme.muted() else Theme.text())
      font.size @= 14.0
    }
    shown.maxWidth := wordsWidth()
    shown.position.left := ListRow.along(SearchField.lead, shown.size.width(), size.width())
    shown.position.top := (size.height() - shown.size.height()) / 2.0
    event.click.attach(_ => Platform.editField(placeholder(), value(), false,
      Autofill.resolve(autofill()).map(Autofill.token).getOrElse(""), v => {
      value @= v
      submitted @= v
    }))
    children ++= List(icon, shown, clear)
    // typed straight in as well, where something types without the platform's keyboard (a Roku's phone app, its voice
    // entry): after the words, Backspace taking off the last, Enter submitting
    typeInto(everyui.focus.TextEntry(value, () => submitted @= value()))
  }
  event.key.down.attach(k => if (k.key == Key.Escape && value().nonEmpty) value @= "")

  /** Emptied, as the × and Escape empty it, the caret kept in it. */
  def empty(): Unit = {
    value @= ""
    if (!focused()) focus()
    if (native && direct) editing @= true
  }

  override private[everyui] def refocus(): Unit = platformField.foreach(_.retake(editing, direct))

  /** Escape is its own while it holds something (it empties it); empty, Escape goes on. */
  override def keepsEvent(e: KeyEvent): Boolean = e.key == Key.Escape && value().nonEmpty

  /** Being edited, it types Enter and the keys that move along its line (the platform's field has them); the up and
    * down arrows move the focus on. */
  override def typesEvent(e: KeyEvent): Boolean =
    native && editing() && (e.key == Key.Enter || e.key == Key.ArrowLeft || e.key == Key.ArrowRight ||
      e.key == Key.Space || e.key == Key.Backspace)

  override def kind: String = "SearchField"
}

object SearchField {
  /** How tall it is: one line. */
  val height: Double = 34.0
  /** The room before the words (the magnifier) and after them (the ×). */
  val lead: Double = 36.0
  val trail: Double = 34.0
}
