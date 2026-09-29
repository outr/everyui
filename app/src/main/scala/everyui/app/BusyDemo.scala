package everyui.app

import everyui.component.TextView
import everyui.render.Platform
import everyui.ui

/** The app at work (`AppBusy`), round and round: idle, busy with no progress, "deliver" from 0 to 100%, idle again.
  * Each change is logged ("[ui] busy (42% deliver)", "[ui] busy idle") and written on the screen. `title=1` puts it
  * in the title too, `spin=<seconds>` holds the busy with no progress that long (4 by default), and
  * `still=<seconds>` sets `AppBusy.stillAfter` and holds the progress at 60% for six seconds past it. Where the
  * platform asks first (Android 13 on), the demo asks as it starts. */
object BusyDemo {
  private val words = new TextView

  def run(): Unit = {
    words.position.left @= 40.0
    words.position.top @= 40.0
    ui.children += words
    AppBusy.inTitle @= Platform.launchArg("title") == "1"
    AppBusy.stillAfter @= Platform.launchArg("still").toDoubleOption
    if (AppBusy.supported && !AppBusy.permitted) AppBusy.permit().start()
    idle()
  }

  private def set(busy: Option[Busy]): Unit = {
    AppBusy.state @= busy
    val said = busy.fold("idle")(b => "(" + (if (b.summary.isEmpty) "…" else b.summary) + ")")
    words.content @= "Busy: " + said + (if (AppBusy.supported) "" else " — not shown on this platform")
    Platform.log("busy " + said)
  }

  private def idle(): Unit = {
    set(None)
    Platform.after(3.0, () => spinning())
  }

  private def spinning(): Unit = {
    set(Some(Busy.indeterminate))
    Platform.after(Platform.launchArg("spin").toDoubleOption.getOrElse(4.0), () => deliver(0))
  }

  private def deliver(step: Int): Unit = if (step > 20) idle() else {
    set(Some(Busy(Some(step / 20.0), Some("deliver"))))
    // with still=<seconds>, the progress stands at 60% long enough for its orbit to go round
    val hold = AppBusy.stillAfter().filter(_ => step == 12).map(_ + 6.0).getOrElse(0.4)
    Platform.after(hold, () => deliver(step + 1))
  }
}
