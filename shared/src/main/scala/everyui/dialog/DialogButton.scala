package everyui.dialog

import everyui.Color
import everyui.component.*
import everyui.component.types.FontWeight
import everyui.kit.{AsyncPress, Spinner}
import everyui.theme.Theme
import reactify.*

/** A dialog's button: its label on an outlined plate, the default one's in the accent and a dangerous one's in the
  * danger colour, lit and ringed under the focus. An async action spins in place while its work runs. */
class DialogButton(val action: DialogAction, points: Double = 14.0) extends Container {
  private val tint: () => Color = () =>
    if (action.danger) Theme.danger() else if (action.default) Theme.accent() else Theme.text()
  private val text = new TextView {
    content @= action.label
    font.size @= points
    font.weight @= FontWeight.SemiBold
    color := tint()
  }
  // the spinner leads the label while work runs, and takes no room until then
  private val spinner = new Spinner(points + 2.0, tint)
  private val line = Row(8.0, middle = true)(spinner, text)
  private val async = new AsyncPress(show => {
    spinner.visible @= show
    spinner.size.width @= (if (show) points + 2.0 else 0.0)
  })
  private val tall = points * 1.5 + 18.0

  focusable @= true
  size.width := line.size.width() + points * 2.5
  size.height @= tall
  radius @= points * 0.7
  borderWidth := (if (focused()) 2.5 else 1.0)
  borderColor := (if (focused()) Theme.accent() else if (action.danger) Theme.danger() else Theme.border())
  backgroundColor := (if (focused() || event.pointer.over()) Theme.controlLit() else Color.Clear)
  line.position.left := (size.width() - line.size.width()) / 2.0
  line.position.top := (tall - line.size.height()) / 2.0
  spinner.visible @= false
  spinner.size.width @= 0.0
  children += line
  tapSlop @= true
  event.click.attach { e =>
    e.consume()
    press()
  }

  /** What a click does: the action, or its work unless it is already running. */
  def press(): Unit = action.run match {
    case Some(work) => async.press(work)
    case None => action.pressed()
  }

  /** Whether its work is running. */
  def running: Boolean = async.running

  override def kind: String = "DialogButton(" + action.label + ")"
}
