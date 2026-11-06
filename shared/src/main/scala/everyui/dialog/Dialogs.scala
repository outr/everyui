package everyui.dialog

import everyui.async.Task
import everyui.component.*
import everyui.component.types.WhiteSpace
import everyui.i18n.Words
import everyui.theme.Theme
import reactify.*

/** The questions an app asks most, each put up at once and answered as a Task, which completes once the dialog has
  * closed and the focus is back where it was. Back, Escape or a click outside is the answer that changes nothing.
  *
  * {{{
  *   Dialogs.confirm("Delete notes.txt?", "It cannot be undone.", Words.delete, danger = true).map { yes =>
  *     if (yes) delete()
  *   }.start()
  *   Dialogs.saveChanges("Save notes.txt?", "Your changes are lost if you don't save them.").map {
  *     case SaveChoice.Save => save().map(_ => closeTab())
  *     case SaveChoice.Discard => closeTab()
  *     case SaveChoice.Cancel => ()
  *   }.start()
  *   Dialogs.prompt("Rename", file.name, validate = n => api.exists(n).map(t => if (t) Some("Already taken") else None))
  *     .map(_.foreach(rename)).start()
  * }}} */
object Dialogs {
  /** Whether to go ahead: true for `confirmLabel`. A `danger`ous one is drawn so, and opens on Cancel. */
  def confirm(title: String, message: String, confirmLabel: String = Words.ok, danger: Boolean = false,
              cancelLabel: String = Words.cancel): Task[Boolean] =
    choose(title, message, List(cancelLabel -> false, confirmLabel -> true), false,
      default = if (danger) 0 else 1, danger = if (danger) 1 else -1)

  /** One of `options`, each a label and what choosing it answers, in the order they stand (the last at the right);
    * `dismissed` where none is chosen. The one at `default` has the focus, and the one at `danger` is drawn so. */
  def choose[T](title: String, message: String, options: List[(String, T)], dismissed: T, default: Int = -1,
                danger: Int = -1): Task[T] = {
    val reply = new Reply[T]
    var panel: DialogPanel = null
    val at = if (default < 0) options.size - 1 else default
    val actions = options.zipWithIndex.map { case ((label, value), i) =>
      DialogAction(label, () => {
        reply.set(value)
        panel.close()
      }, default = i == at, danger = i == danger)
    }
    panel = new DialogPanel(title, words(message), actions)
    panel.open(after = () => reply.finish(dismissed))
    reply.task
  }

  /** Unsaved work being left: Save, Don't save or Cancel, with Save the default. */
  def saveChanges(title: String, message: String, save: String = Words.save, discard: String = Words.discard,
                  cancel: String = Words.cancel): Task[SaveChoice] =
    choose(title, message, List(discard -> SaveChoice.Discard, cancel -> SaveChoice.Cancel, save -> SaveChoice.Save),
      SaveChoice.Cancel)

  /** Words from the user, starting at `initial`, or None for Cancel. `validate` checks them when OK (or Enter) is
    * pressed and says what is wrong, if anything; while it works OK spins, and what it says shows under the field until
    * the words change. */
  def prompt(title: String, initial: String = "", label: String = "",
             validate: String => Task[Option[String]] = _ => Task.pure(None),
             okLabel: String = Words.ok, cancelLabel: String = Words.cancel, secure: Boolean = false): Task[Option[String]] = {
    val reply = new Reply[Option[String]]
    var panel: DialogPanel = null
    val width = DialogPanel.width - DialogPanel.textSize() * 2.9 - 24.0
    val field = new TextInput(label, secure, width)
    field.value @= initial
    val problem: Var[String] = Var("")
    val error = TextView.line(problem(), DialogPanel.textSize() * 0.9, Theme.dangerText(), widest = width)
    field.value.attach(_ => problem @= "")
    val ok = DialogAction.async(okLabel, default = true) { done =>
      val words = field.value()
      validate(words).map {
        case Some(wrong) =>
          problem @= wrong
          done(Left(""))
        case None =>
          reply.set(Some(words))
          done(Right(()))
          panel.close()
      }.start()
    }
    panel = new DialogPanel(title, List(field, error), List(DialogAction(cancelLabel, () => panel.close()), ok))
    field.submitted.attach(_ => panel.buttons(1).press())
    panel.open(Some(field), () => reply.finish(None))
    reply.task
  }

  /** A dialog of `body` with the `actions` made for it, which are handed what closes it; answered with nothing once
    * it has closed. */
  def show(title: String, body: List[Component], actions: (() => Unit) => List[DialogAction]): Task[Unit] = {
    val reply = new Reply[Unit]
    var panel: DialogPanel = null
    panel = new DialogPanel(title, body, actions(() => panel.close()))
    panel.open(after = () => reply.finish(()))
    reply.task
  }

  /** A message as a body: words wrapped to the dialog's width. */
  private def words(message: String): List[Component] = if (message.isEmpty) Nil else List(new TextView {
    whiteSpace @= WhiteSpace.Normal
    size.width @= DialogPanel.width - DialogPanel.textSize() * 2.9
    font.size @= DialogPanel.textSize()
    color := Theme.text()
    content @= message
  })
}
