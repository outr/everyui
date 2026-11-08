package everyui.background

import everyui.async.Task

/** The app's code that runs with no screen: an object the platform loads by name where the app is not running (on
  * Android, the manifest's meta-data "everyui.background", which sbt-everyui writes from `everyuiBackground`), whose
  * handlers hear what the platform delivers in the background: scheduled work, an ongoing notice's actions, the device
  * started again or the app updated.
  *
  * A handler runs where there may be no window: it reads the settings, the secrets, the store and the network, and
  * never touches a component. It is started on the thread that draws, as the app's own tasks are, so heavy work goes
  * `Task.off`. The platform holds its process until the task answers or a deadline passes: about ten seconds for
  * an action or a restart (one that needs longer schedules `Work.once` and answers), and nine and a half minutes for
  * work.
  *
  * {{{
  *   object Background extends BackgroundApp {
  *     override def prepare(): Unit = Network.origin @= "https://example.com"
  *     override def onWork(key: String, data: Map[String, String]): Task[WorkResult] =
  *       if (key == "sync") Sync.run().map(ok => if (ok) WorkResult.Done else WorkResult.Retry)
  *       else Task.pure(WorkResult.Done)
  *     override def onRestart(reason: RestartReason): Task[Unit] = Task(Tracking.showNotice())
  *   }
  * }}} */
trait BackgroundApp {
  /** Made ready before any handler, once a process, where the app's `main` may not have run: the origin its server
    * is reached at, anything its handlers read. Nothing by default. */
  def prepare(): Unit = ()

  /** Scheduled work whose time has come (`Work.once`, `Work.every`): its key and the data it was scheduled with.
    * Done, Retry to run it again after its backoff, or Failed. Done by default. */
  def onWork(key: String, data: Map[String, String]): Task[WorkResult] = Task.pure(WorkResult.Done)

  /** An ongoing notice's action tapped (`NoticeTap.RunAction`), with the app left closed: the notice's key, and the
    * action's name and args. */
  def onNoticeAction(notice: String, action: String, args: Map[String, String]): Task[Unit] = Task.unit

  /** An ongoing notice swiped away, by its key: Android 14 and later let the person do so. The app may show it again,
    * or take the swipe as a wish to stop. */
  def onNoticeDismissed(notice: String): Task[Unit] = Task.unit

  /** The device started again, or the app was updated: what the platform forgot then (an ongoing notice) is put back
    * here. Scheduled work is kept through both and needs nothing. It may be heard with no restart (Android 15 tells
    * an app that leaves its stopped state that the device started), so what it does is safe to do twice. */
  def onRestart(reason: RestartReason): Task[Unit] = Task.unit
}
