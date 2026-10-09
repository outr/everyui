package everyui.app

import everyui.async.Task
import everyui.dialog.Dialogs
import everyui.i18n.Words
import everyui.render.Platform
import reactify.*

/** What has the app asked before it is left (`Platform.leaveGuard`): the guards it holds, and the question. Leaving is
  * guarded while any guard answers true. Each platform asks in its own way: the web's page through the browser's own
  * "Leave site?", the desktop's window and Android's Back from the first screen with `ask`; a Roku and an Apple TV do
  * not ask. */
object LeaveGuards {
  private var asks: List[() => Boolean] = Nil
  private var flags: List[Val[Boolean]] = Nil
  private var asking = false

  /** Whether a guard might answer true now: one asked as it is left, whose answer is not known until then, or a flag
    * that is set. The web keeps its listener only while this holds. */
  val armed: Var[Boolean] = Var(false)

  /** `guard` asked whenever the app is about to be left; what removes it. */
  def add(guard: () => Boolean): () => Unit = {
    asks = asks :+ guard
    rearm()
    () => {
      asks = without(asks, guard)
      rearm()
    }
  }

  /** Guarded while `flag` is set; what removes it. */
  def addWhile(flag: Val[Boolean]): () => Unit = {
    val reaction = flag.attach(_ => rearm())
    flags = flags :+ flag
    rearm()
    () => {
      flag.reactions -= reaction
      flags = without(flags, flag)
      rearm()
    }
  }

  /** Whether leaving now should be asked: a guard answering true. One that fails answers false, so the app can always
    * be left. */
  def guarded(): Boolean = flags.exists(_()) || asks.exists { guard =>
    try guard() catch {
      case t: Throwable =>
        Platform.logError("A leave guard failed", t)
        false
    }
  }

  /** The question, Stay or Leave (`Words.leaveTitle` and `Words.leaveMessage`, which `Words.add` rewords), opening on
    * Stay: true to leave. Asked again while it is up, it answers false and puts up no other. */
  def ask(): Task[Boolean] = if (asking) Task.pure(false) else {
    asking = true
    Dialogs.confirm(Words.leaveTitle, Words.leaveMessage, Words.leave, danger = true, cancelLabel = Words.stay).map { leave =>
      asking = false
      leave
    }
  }

  private def rearm(): Unit = {
    val now = asks.nonEmpty || flags.exists(_())
    if (armed() != now) armed @= now
  }

  // the first of them that is `x` taken out, so one added twice is removed once for each remover
  private def without[T <: AnyRef](list: List[T], x: T): List[T] = {
    val at = list.indexWhere(_ eq x)
    if (at < 0) list else list.patch(at, Nil, 1)
  }
}
