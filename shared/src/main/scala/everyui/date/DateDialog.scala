package everyui.date

import everyui.async.Task
import everyui.component.{Component, Container, Modal}
import everyui.dialog.{DialogAction, DialogPanel}
import everyui.theme.Theme
import everyui.{Color, Shadow, ui}

/** How the pickers are put up: in a dialog with OK and Cancel (`ask`), or in a panel dropped from a field (`drop`). */
private[date] object DateDialog {
  /** How wide a picker can be in a dialog now: the dialog's room less its margins, and no wider than a calendar. */
  def width(): Double = {
    val points = DialogPanel.textSize()
    math.min(math.min(DialogPanel.width, ui.size.width() - 32.0) - points * 2.9 - 8.0, Calendar.width)
  }

  /** `body` in a dialog under `title`, answered with what `answer` says once OK is pressed, or None for Cancel, Back,
    * Escape or a click outside, once the dialog has closed and the focus is back. `focus` is where it starts. */
  def ask[T](title: String, body: Component, width: Double, focus: () => Option[Component], answer: () => Option[T],
             okLabel: String, cancelLabel: String): Task[Option[T]] = {
    var said: Option[T] = None
    var waiting: List[Option[T] => Unit] = Nil
    var done = false
    val points = DialogPanel.textSize()
    var panel: DialogPanel = null
    val cancel = DialogAction(cancelLabel, () => panel.close())
    val ok = DialogAction(okLabel, () => {
      said = answer()
      panel.close()
    }, default = true)
    panel = new DialogPanel(title, List(body), List(cancel, ok),
      wide = math.min(DialogPanel.width, width + points * 2.9 + 8.0))
    panel.open(focus(), after = () => {
      done = true
      val all = waiting
      waiting = Nil
      all.foreach(f => f(said))
    })
    Task.async[Option[T]](f => if (done) f(said) else waiting = waiting :+ f)
  }

  /** `body` in a panel under `anchor` (over it where below has no room), kept on the screen, with the focus on
    * `focus`, over any dialog up (the field's): Back, Escape or a click outside closes it alone, as `close` does. */
  def drop(anchor: Component, body: Component, focus: () => Option[Component], closed: () => Unit = () => ()): Container = {
    val p = panel(body)
    place(p, anchor)
    Modal.over(p, focus().getOrElse(body), closed, dim = false, centre = false)
    p
  }

  /** `body` on a raised card of its own, as a dropped calendar is drawn. */
  def panel(body: Component): Container = {
    val panel = new Container
    val inset = 12.0
    body.position.left @= inset
    body.position.top @= inset
    panel.children += body
    panel.size.width := body.size.width() + inset * 2.0
    panel.size.height := body.size.height() + inset * 2.0
    panel.backgroundColor := Theme.raised()
    panel.radius @= 14.0
    panel.borderWidth @= 1.0
    panel.borderColor := Theme.border()
    panel.shadow @= Some(Shadow(0.0, 6.0, 24.0, Color.Black.withAlpha(0.45)))
    panel
  }

  /** `panel` under `anchor`, lined up with its start, or above it where there is no room below. */
  def place(panel: Container, anchor: Component): Unit = {
    val left = anchor.absoluteLeft
    val top = anchor.absoluteTop
    // lined up with the anchor's start: its right edge in a right-to-left language
    panel.position.left := math.max(8.0, math.min(
      if (everyui.i18n.Direction.rtl()) left + anchor.size.width() - panel.size.width() else left,
      ui.size.width() - panel.size.width() - 8.0))
    panel.position.top := {
      val under = top + anchor.size.height() + 4.0
      if (under + panel.size.height() <= ui.size.height() - 8.0) under
      else math.max(8.0, top - panel.size.height() - 4.0)
    }
  }
}
