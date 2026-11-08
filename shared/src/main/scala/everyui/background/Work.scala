package everyui.background

import everyui.render.{BackgroundAccess, Platform}

/** Work the platform runs later, with the app closed, in the app's `BackgroundApp.onWork`: once (a message to send
  * when there is a network), or every so many minutes (a sync), each when its constraints hold. Work is unique by its
  * key, kept through a restart of the device, and runs until it answers Done or Failed, or again after its backoff
  * where it answers Retry.
  *
  * Android schedules it with JobScheduler, where the build names a `BackgroundApp` (`everyuiBackground`). The web,
  * the desktop, an Apple TV and a Roku run nothing with the app closed: there `supported` is false, nothing is
  * scheduled and `pending` is always false.
  *
  * {{{
  *   Work.once("send", WorkConstraints(network = WorkNetwork.Connected), data = Map("id" -> message.id))
  *   Work.every("sync", 60, WorkConstraints(network = WorkNetwork.Unmetered, charging = true))
  *   Work.cancel("sync")
  * }}} */
object Work {
  /** Whether this platform runs scheduled work with the app closed. */
  def supported: Boolean = BackgroundAccess.supported()

  /** Work `key` run once, after `delay` seconds, when `constraints` hold, with `data` handed to `onWork`. `expedited`
    * asks to run it as soon as it can (Android 12 and later; ordinary work before), and cannot wait for anything but
    * a network. `replace` (the default) puts it in place of work scheduled with the key; otherwise work already
    * scheduled with it is kept. Whether it was scheduled: not where the platform has none, or where it is wrong
    * (`WorkRequest.problems`, logged). */
  def once(key: String,
           constraints: WorkConstraints = WorkConstraints(),
           expedited: Boolean = false,
           backoff: WorkBackoff = WorkBackoff(),
           delay: Double = 0.0,
           data: Map[String, String] = Map.empty,
           replace: Boolean = true): Boolean =
    schedule(WorkRequest(key, 0, constraints, expedited, backoff, delay, data, replace))

  /** Work `key` run every `minutes` (at least 15, the platform's floor: fewer are raised to it) when `constraints`
    * hold, until it is cancelled, with `data` handed to `onWork` each time. In place of work scheduled with the key.
    * Whether it was scheduled. */
  def every(key: String,
            minutes: Int,
            constraints: WorkConstraints = WorkConstraints(),
            backoff: WorkBackoff = WorkBackoff(),
            data: Map[String, String] = Map.empty): Boolean = {
    if (minutes < WorkRequest.leastMinutes)
      Platform.log("work: " + key + " every " + minutes.toString + " minutes raised to " +
        WorkRequest.leastMinutes.toString)
    schedule(WorkRequest(key, math.max(WorkRequest.leastMinutes, minutes), constraints, expedited = false, backoff,
      0.0, data, replace = true))
  }

  /** Work `key` no longer scheduled, once or periodic; a run under way is stopped. */
  def cancel(key: String): Unit = if (supported) BackgroundAccess.cancel(key)

  /** Whether work `key` is scheduled and waiting to run. */
  def pending(key: String): Boolean = supported && BackgroundAccess.pending(key)

  private def schedule(request: WorkRequest): Boolean = {
    val wrong = request.problems
    if (wrong.nonEmpty) {
      wrong.foreach(w => Platform.log("work: " + w))
      false
    }
    else supported && BackgroundAccess.schedule(request)
  }
}
