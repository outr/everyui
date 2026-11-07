package everyui.time

import everyui.component.TextView
import everyui.dialog.{DialogAction, DialogPanel}
import everyui.i18n.Words
import everyui.render.Platform
import everyui.theme.Theme

/** A time an app gives somebody before something ends (a sign-in that lapses, a held seat let go), that they are told
  * of before it ends and can have more of (WCAG 2.2.1): `start` it, and `expired` runs once `seconds` pass with
  * nothing done. `warning` before the end (20 seconds, longer where people asked for more time, `TimeLimits`), a
  * dialog asks whether they are still there, and "Keep going" starts it again; left alone, the dialog goes as the time
  * ends. `extend` starts it again from now (on whatever shows the person is still there: a key, a click), and `stop`
  * ends it with nothing run.
  *
  * {{{
  *   val session = new TimeLimit(15 * 60, () => signOut())
  *   session.start()
  *   ui.event.pointer.down.attach(_ => session.extend())
  * }}} */
class TimeLimit(seconds: Double, expired: () => Unit, warning: Double = 20.0) {
  // which start this is, so a timer from an earlier one does nothing; and the warning up, where it is
  private var run = 0
  private var running = false
  private var asking: Option[DialogPanel] = None

  /** Whether it is counting down. */
  def isRunning: Boolean = running

  /** Counting from now: the warning `warning` before the end, then the end. */
  def start(): Unit = {
    run += 1
    running = true
    closeWarning()
    val mine = run
    val warnFor = math.min(TimeLimits.scaled(warning), seconds)
    TimeLimit.after(math.max(0.0, seconds - warnFor), () => if (mine == run) warn(mine))
    TimeLimit.after(math.max(seconds, warnFor), () => if (mine == run) end())
  }

  /** Counting again from now, as `start` does: more time. */
  def extend(): Unit = if (running) start()

  /** Ended, with nothing run. */
  def stop(): Unit = {
    run += 1
    running = false
    closeWarning()
  }

  private def warn(mine: Int): Unit = {
    val words = TextView.paragraph(TimeLimit.almostUp, DialogPanel.width - 60.0, 16.0, Theme.text())
    val panel = new DialogPanel(TimeLimit.stillThere, List(words),
      List(DialogAction(TimeLimit.keepGoing, () => if (mine == run) start(), default = true)))
    asking = Some(panel)
    panel.open()
    everyui.access.LiveRegions.say(TimeLimit.stillThere + " " + TimeLimit.almostUp,
      everyui.component.types.Live.Assertive)
  }

  private def end(): Unit = {
    running = false
    closeWarning()
    expired()
  }

  private def closeWarning(): Unit = {
    asking.foreach(_.close())
    asking = None
  }
}

object TimeLimit {
  /** How its times are waited out: the platform's timer, or a spec's own clock, which runs what falls due exactly when
    * the spec says time has passed, however late a busy machine would run a real timer (as `AsyncPress.after`). */
  private[everyui] var after: (Double, () => Unit) => Unit = (seconds, f) => Platform.after(seconds, f)

  /** The warning's title, words and button, in each language. */
  def stillThere: String = Words("time.stillThere")
  def almostUp: String = Words("time.almostUp")
  def keepGoing: String = Words("time.keepGoing")
}
