package everyui.dialog

import everyui.kit.AsyncPress

/** One button of a dialog: the `default` one takes the focus when the dialog opens; a `danger` one is drawn in the
  * danger colour (what cannot be undone).
  *
  * A plain action runs `pressed`. One made with [[DialogAction.async]] runs work that takes a while: its button spins
  * while it runs (only once it has run a moment, so quick work looks instant), ignores presses meanwhile, and on a
  * failure is pressable again and toasts the message. */
case class DialogAction(label: String, pressed: () => Unit, default: Boolean = false, danger: Boolean = false,
                        run: Option[AsyncPress.Done => Unit] = None)

object DialogAction {
  /** An action whose work reports back through the `AsyncPress.Done` it is handed, once: `Right(())` when it
    * succeeded (the work has already done what follows, such as closing the dialog), `Left(message)` when it failed,
    * which is toasted (nothing for ""). */
  def async(label: String, default: Boolean = false, danger: Boolean = false)
           (run: AsyncPress.Done => Unit): DialogAction =
    DialogAction(label, () => (), default, danger, Some(run))
}
