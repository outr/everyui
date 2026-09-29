package everyui.color

import everyui.Color
import everyui.async.Task
import everyui.dialog.{DialogAction, DialogPanel}
import everyui.i18n.Words
import everyui.ui
import reactify.*

/** A colour asked for in a dialog (everyui.dialog): a ColorPicker starting at `initial`, answered with the colour
  * chosen, or None for Cancel, Back, Escape or a click outside. The colour chosen is remembered among the recent ones.
  *
  * {{{
  *   ColorDialog.pick("Label colour", label.colour, presets = ColorPicker.material).map(_.foreach(c => label.colour @= c)).start()
  * }}} */
object ColorDialog {
  def pick(title: String, initial: Color, alpha: Boolean = false, presets: List[Color] = Nil,
           recent: Var[List[Color]] = ColorPicker.recent, okLabel: String = Words.ok,
           cancelLabel: String = Words.cancel): Task[Option[Color]] = {
    val value = Var(initial)
    var answer: Option[Color] = None
    var waiting: List[Option[Color] => Unit] = Nil
    var done = false
    val points = DialogPanel.textSize()
    val width = math.min(math.min(DialogPanel.width, ui.size.width() - 32.0) - points * 2.9 - 8.0, 360.0)
    val picker = new ColorPicker(value, alpha, presets, recent, width)
    var panel: DialogPanel = null
    val cancel = DialogAction(cancelLabel, () => panel.close())
    val ok = DialogAction(okLabel, () => {
      answer = Some(value())
      picker.remember()
      panel.close()
    }, default = true)
    panel = new DialogPanel(title, List(picker), List(cancel, ok), wide = math.min(DialogPanel.width, width + points * 2.9 + 8.0))
    panel.open(after = () => {
      done = true
      val all = waiting
      waiting = Nil
      all.foreach(f => f(answer))
    })
    Task.async[Option[Color]](f => if (done) f(answer) else waiting = waiting :+ f)
  }
}
