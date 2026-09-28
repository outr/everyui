package everyui.dialog

import everyui.component.*
import everyui.component.types.FontWeight
import everyui.event.Key
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.kit.{Mirror, TextScroll}
import everyui.layout.{FlowLayout, VerticalLayout}
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A dialog: a title, a `body` that scrolls when it is taller than the screen leaves room for (or than `tallest`), and
  * its buttons at the end of the line (the right, or the left in a right-to-left language, the title then at the
  * right), `wide` across or as wide as the screen allows. It holds the focus while it is up; Back closes it, and the
  * focus goes back where it was.
  *
  * A body of words alone that is taller than its room takes the focus itself, and Up and Down read through it, since a
  * remote has no other way to reach its end. Down from the body goes to the buttons, onto the default one, and Up from
  * the buttons back into the body.
  *
  * {{{
  *   val panel = new DialogPanel("Publish", List(summary), List(
  *     DialogAction(Words.cancel, () => panel.close()),
  *     DialogAction.async("Publish", default = true) { done =>
  *       api.publish().map { ok => if (ok) { panel.close(); done(Right(())) } else done(Left("Publishing failed")) }.start()
  *     }))
  *   panel.open()
  * }}} */
class DialogPanel(title: String, body: List[Component], actions: List[DialogAction],
                  wide: Double = DialogPanel.width, tallest: Double = 100000.0) extends Container {
  private val points: Double = DialogPanel.textSize()
  private val margin: Double = points * 1.45
  private val across: Double = math.min(wide, ui.size.width() - 32.0)
  private val inner: Double = across - margin * 2.0
  private val heading = new TextView {
    content @= title
    font.size @= points * 1.3
    font.weight @= FontWeight.Bold
    color := Theme.text()
    maxWidth @= inner
  }
  private val stack = new Container {
    size.width @= inner
    layout @= Some(VerticalLayout(points * 0.7))
    children ++= body
  }
  private val plain = Focus.candidates(stack).isEmpty
  private val scroll = new TextScroll {
    content.size.height := stack.size.height() + slack * 2.0
    content.children += stack
  }
  // a body of controls has room round it inside the viewport, which clips: a focus ring stands out past a control's box
  private def slack: Double = if (plain) 0.0 else 12.0
  scroll.focusable := plain && stack.size.height() > scroll.size.height() + 1.0
  if (plain) {
    scroll.size.width @= inner + TextScroll.gutter
    scroll.position.left @= margin - TextScroll.gutter
  }
  else {
    scroll.size.width @= inner + slack * 2.0
    scroll.content.position.left @= 0.0
    scroll.content.size.width := scroll.size.width()
    scroll.position.left @= margin - slack
    stack.position.left @= slack
    stack.position.top @= slack
  }

  /** The buttons, in the order of the actions. */
  val buttons: List[DialogButton] = actions.map(a => new DialogButton(a, points))
  private val row = new Container {
    layout @= Some(FlowLayout(10.0, 8.0))
    children ++= buttons
  }

  size.width @= across
  backgroundColor := Theme.raised()
  radius @= points * 1.1
  // the title where the language starts, the buttons where it ends: in a right-to-left language the title at the right
  // and the buttons at the left, whose row mirrors itself, so the default action still ends the line
  Mirror.place(heading, () => size.width(), () => margin)
  heading.position.top @= margin
  private def top: Double = margin + (if (title.isEmpty) 0.0 else heading.size.height() + points * 0.85)
  private def actionsTall: Double = if (buttons.isEmpty) 0.0 else row.size.height() + margin
  scroll.position.top := top - slack
  scroll.size.height := math.min(math.min(stack.size.height(), tallest),
    ui.size.height() - 48.0 - top - actionsTall - margin) + slack * 2.0
  // the buttons at the right, on as few lines as they fit
  row.size.width := math.min(inner, buttons.map(_.size.width()).sum + 10.0 * math.max(0, buttons.size - 1) + 1.0)
  row.position.left := (if (Direction.rtl()) margin else margin + inner - row.size.width())
  row.position.top := top - slack + scroll.size.height() - slack + (if (body.isEmpty) 0.0 else margin)
  size.height := row.position.top() + actionsTall
  children ++= (if (title.isEmpty) Nil else List(heading)) ++ List(scroll, row)
  scroll.exitFocus(Key.ArrowDown, row)
  row.exitFocus(Key.ArrowUp, scroll)
  defaultButton.foreach(b => row.entryFocus(b))

  /** The button of the default action, if one is. */
  def defaultButton: Option[DialogButton] = buttons.find(_.action.default)

  /** Put it up, the focus on `focus`, else on the default button, else on the first thing it holds; `after` hears it
    * close, however it closes. `over` keeps the dialog it opens from, to go back to; one that is not `dismissible`
    * stays until what put it up takes it down. */
  def open(focus: Option[Component] = None, after: () => Unit = () => (), over: Boolean = false,
           dismissible: Boolean = true): Unit = {
    val target = focus.orElse(defaultButton).orElse(Focus.candidates(this).headOption).getOrElse(this)
    if (over) Modal.over(this, target, after, dismissible) else Modal.show(this, target, after, dismissible)
  }

  /** Take it down, wherever it is among the dialogs up. */
  def close(): Unit = Modal.remove(this)

  override def kind: String = "DialogPanel"
}

object DialogPanel {
  /** How wide a dialog is unless it says. */
  val width: Double = 480.0
  /** How big a dialog's words are: a television's app sets it larger once, before it opens any. */
  val textSize: Var[Double] = Var(14.0)
}
