package everyui.date

import everyui.component.types.Semantics
import everyui.component.{Container, IconButton, TextInput, TextView}
import everyui.event.Key
import everyui.font.MaterialIcon
import everyui.i18n.Strings
import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A time of day in a field, `width` wide: the time as the language writes it on its own clock ("3:30 PM", "15:30"),
  * what is typed read back (`ClockTime.read`: "14:30", "2:30 pm", "1430"), and a button beside it that opens a
  * `TimePicker` in a dialog, its wheels in `step` minutes. `value` is the time, both ways; the field emptied is None.
  * What is typed changes `value` once it is a time, and a field left holding anything else says so under it
  * (`error`); left holding a time, it is written again as the language writes it. Alt+Down opens the picker too.
  *
  * Where the app is worked by a remote (`Platform.remoteNavigation`, or `typing = false`), there is nothing to type:
  * the field shows the time, and OK on it opens the picker.
  *
  * {{{
  *   val arrived = Var(Option.empty[ClockTime])
  *   new TimeField("Arrived", arrived, step = 15)
  * }}} */
class TimeField(label: String = DateWords.time, val value: Var[Option[ClockTime]] = Var(None), step: Int = 5,
                width: Double = 320.0, button: Boolean = true, typing: Boolean = !Platform.remoteNavigation())
  extends Container {
  /** What is wrong with what the field holds, if anything: it is not a time. */
  val error: Var[Option[String]] = own(Var(None))
  /** A time typed in, chosen, or the field emptied, as it changes `value`: what the person did, apart from the app. */
  val edited: Channel[Option[ClockTime]] = Channel[Option[ClockTime]]
  private val across: Double = if (button) width - 44.0 else width
  /** The field typed into, where there is typing. */
  val input: Option[TextInput] = if (typing) Some(new TextInput(label, width = across) {
    override def keepsEvent(e: everyui.event.KeyEvent): Boolean =
      (e.key == Key.ArrowDown && e.alt) || super.keepsEvent(e)
  }) else None
  private val face = new Container
  private val opener = new IconButton(MaterialIcon.Schedule, DateWords.openClock, () => open())
  private val line = new Container
  private val warning =
    TextView.line(if (input.exists(_.editing())) "" else error().getOrElse(""), 12.0, Theme.danger())

  private def written(v: Option[ClockTime]): String = v.map(_.text()).getOrElse("")

  layout @= Some(VerticalLayout(4.0))
  line.layout @= Some(HorizontalLayout(12.0, middle = true))
  input match {
    case Some(field) =>
      field.value @= written(value())
      field.value.attach(t => read(t))
      field.editing.attach(e => if (!e) tidy())
      field.submitted.attach(_ => tidy())
      // wrong once it is left so, not while a time is half typed
      field.invalid := (if (field.editing()) "" else error().getOrElse(""))
      field.event.key.down.attach(k => if (k.key == Key.ArrowDown && k.alt) open())
      line.children += field
    case None =>
      // what a remote works: the time shown as a field shows it, and OK opens the picker
      val caption = TextView(label, 12.0, Theme.muted())
      val shown = TextView.bound(written(value()), 16.0)
      face.focusable @= true
      face.size.width @= across
      face.size.height @= 56.0
      face.radius @= 10.0
      face.borderWidth := (if (face.focusShown()) 2.5 else 1.0)
      face.borderColor := (if (face.focusShown()) Theme.accent() else Theme.edge())
      face.backgroundColor := (if (face.focusShown()) Theme.controlLit() else Theme.raised())
      face.semantics := Semantics.button(label + ", " + written(value()))
      caption.position.left @= 14.0
      caption.position.top @= 7.0
      shown.position.left @= 14.0
      shown.position.top @= 26.0
      face.children ++= List(caption, shown)
      face.event.click.attach { e =>
        e.consume()
        open()
      }
      line.children += face
  }
  if (button) line.children += opener
  children ++= List(line, warning)

  follow(value, (v: Option[ClockTime]) => input.foreach { field =>
    if (ClockTime.read(field.value()) != v) {
      field.value @= written(v)
      error @= None
    }
  })
  // written again on the clock of the language it changes to
  follow(Strings.language, (_: everyui.i18n.Language) => if (error().isEmpty) tidy())

  /** The picker, in a dialog, at the time in the field. What the button, Alt+Down and a remote's OK open. */
  def open(): Unit = TimePicker.pick(label, value(), step).map(_.foreach(t => set(t))).start()

  private def set(t: ClockTime): Unit = {
    error @= None
    if (value().contains(t)) tidy()
    else {
      value @= Some(t)
      edited @= Some(t)
    }
  }

  // what `text` means: a time, or None; `value` and `error` follow it
  private def read(text: String): Unit =
    if (text.trim.isEmpty) {
      error @= None
      if (value().nonEmpty) {
        value @= None
        edited @= None
      }
    }
    else ClockTime.read(text) match {
      case Some(t) =>
        error @= None
        if (!value().contains(t)) {
          value @= Some(t)
          edited @= Some(t)
        }
      case None => error @= Some(DateWords.invalidTime)
    }

  // a time the field holds, written as the language writes it
  private def tidy(): Unit = input.foreach { field =>
    if (error().isEmpty) {
      val w = written(value())
      if (field.value() != w) field.value @= w
    }
  }

  override def kind: String = "TimeField(" + label + ")"
}
