package everyui.background

import everyui.render.{BackgroundAccess, Platform}

/** Work run later in the app's `BackgroundApp.onWork`: once (a message to send when there is a network), or every so
  * many minutes (a sync), each when its constraints hold. Work is unique by its key, kept through a restart, and runs
  * until it answers Done or Failed, or again after its backoff where it answers Retry.
  *
  * Android runs it with the app closed, on WorkManager, where the build names a `BackgroundApp`
  * (`everyuiBackground`; `inBackground` true). Everywhere else (the web, the desktop, an Apple TV, a Roku, and
  * Android with no `BackgroundApp`) it runs while the app is open (WorkRunner): what is scheduled is kept in the
  * app's settings, and what came due while it was closed runs as it starts again, once the app has registered its
  * `BackgroundApp` (`Background.register`). There the network is waited for where the platform can tell there is
  * none; charging, the battery and the device idle are taken as met, and an unmetered network is any network.
  *
  * {{{
  *   Work.once("send", WorkConstraints(network = WorkNetwork.Connected), data = Map("id" -> message.id))
  *   Work.every("sync", 60, WorkConstraints(network = WorkNetwork.Unmetered, charging = true))
  *   Work.cancel("sync")
  * }}} */
object Work {
  /** Whether scheduled work runs here: everywhere, with the app closed where `inBackground`, else while it is open. */
  def supported: Boolean = true

  /** Whether scheduled work runs with the app closed (Android, where the build names a `BackgroundApp`). */
  def inBackground: Boolean = BackgroundAccess.supported()

  /** The work run while the app is open, kept in its settings. */
  private[everyui] lazy val inApp: WorkRunner = new WorkRunner(() => System.currentTimeMillis(),
    (seconds, f) => Platform.after(seconds, f), name => Platform.setting(name), (name, value) =>
      Platform.saveSetting(name, value), () => Platform.online(), (key, data) => Background.work(key, data))

  /** The work run while the app is open timed from now, as the app registers its `BackgroundApp`. */
  private[everyui] def resume(): Unit = if (!inBackground) inApp.resume()

  /** Work `key` run once, after `delay` seconds, when `constraints` hold, with `data` handed to `onWork`. `expedited`
    * asks to run it as soon as it can (Android 12 and later; ordinary work before), and cannot wait for anything but
    * a network. `replace` (the default) puts it in place of work scheduled with the key; otherwise work already
    * scheduled with it is kept. Whether it was scheduled: not where it is wrong
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

  /** Work `key` no longer scheduled, once or periodic; a run under way is stopped (on Android) or its answer let go. */
  def cancel(key: String): Unit = if (inBackground) BackgroundAccess.cancel(key) else inApp.cancel(key)

  /** Whether work `key` is scheduled and waiting to run. */
  def pending(key: String): Boolean = if (inBackground) BackgroundAccess.pending(key) else inApp.pending(key)

  private def schedule(request: WorkRequest): Boolean = {
    val wrong = request.problems
    if (wrong.nonEmpty) {
      wrong.foreach(w => Platform.log("work: " + w))
      false
    }
    else if (inBackground) BackgroundAccess.schedule(request)
    else inApp.schedule(request)
  }
}
