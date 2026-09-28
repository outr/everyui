package everyui.list

import everyui.component.{Container, InputView, TextView}
import everyui.event.{Key, KeyEvent}
import everyui.theme.Theme
import reactify.*

/** The field a row's name is typed in, over its label: Enter submits, Escape cancels, and leaving it (the focus going
  * elsewhere, a click away) is heard as `left`, which submits too. A message the app gives back for a name it will not
  * take shows under it (over it, `above`, near the list's foot) until the text changes. The arrows move along the text
  * rather than out of the field. */
class RowEditor(initial: String) extends Container {
  val input: InputView = new InputView {
    font.size @= 14.0
    color := Theme.text()
    position.left @= 6.0
    size.width := RowEditor.this.size.width() - 12.0
    size.height @= 20.0
    position.top := (RowEditor.this.size.height() - 20.0) / 2.0
  }
  val error: Var[String] = own(Var(""))
  val above: Var[Boolean] = own(Var(false))
  val submitted: Channel[String] = Channel[String]
  val cancelled: Channel[Unit] = Channel[Unit]
  val left: Channel[String] = Channel[String]

  private val message = new Container {
    private val words = new TextView {
      content := error()
      font.size @= 13.0
      color := Theme.onAccent()
      position.left @= 8.0
      position.top @= 4.0
    }
    backgroundColor := Theme.danger()
    radius @= 4.0
    size.width := math.max(RowEditor.this.size.width(), words.size.width() + 16.0)
    size.height := words.size.height() + 8.0
    position.top := (if (above()) -size.height() - 2.0 else RowEditor.this.size.height() + 2.0)
    visible := error().nonEmpty
    children += words
  }

  focusable @= true
  stopFocus(Key.ArrowUp, Key.ArrowDown, Key.ArrowLeft, Key.ArrowRight)
  backgroundColor := Theme.surface()
  borderWidth @= 1.0
  borderColor := (if (error().nonEmpty) Theme.danger() else Theme.accent())
  radius @= 3.0
  input.text @= initial
  children ++= List(input, message)

  override def keepsEvent(e: KeyEvent): Boolean = e.key == Key.Enter || e.key == Key.Escape

  event.key.down.attach { e =>
    if (e.key == Key.Enter) submitted @= input.text()
    else if (e.key == Key.Escape) cancelled @= (())
  }
  focused.attach(on => if (!on) left @= input.text())
  input.node.onInputDone(() => left @= input.text())
  input.node.onInputFocus(() => if (!focused()) focus())
  input.text.attach(_ => error @= "")

  /** The focus and the caret in it, the text chosen to be typed over. */
  def start(): Unit = {
    focus()
    input.node.focusInput(true)
    input.selectAll()
  }

  /** The caret out of it, as it goes. */
  def stop(): Unit = input.node.focusInput(false)

  override def kind: String = "RowEditor"
}
