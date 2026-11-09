package everyui.background

import everyui.async.Task
import everyui.render.BackgroundAccess

/** What runs with the app closed: the app's `BackgroundApp`, which the platform loads by name and makes ready once a
  * process, and whose handlers it calls as work comes due, an ongoing notice's action is tapped or the device starts
  * again. Android has it where the build names one (`everyuiBackground`); the web, the desktop, an Apple TV and a Roku
  * run nothing with the app closed, so `supported` is false there, and the app `register`s it as it starts: its
  * scheduled work then runs while the app is open (`Work`), and no other handler is called. */
object Background {
  @volatile private var started: Option[BackgroundApp] = None

  /** Whether this platform runs the app's handlers with the app closed: Android, where the build names a
    * `BackgroundApp`. */
  def supported: Boolean = BackgroundAccess.supported()

  /** The app's `BackgroundApp`, once the platform has made it ready in this process. */
  def app: Option[BackgroundApp] = started

  /** The app's `BackgroundApp`, registered as the app starts: made ready (once), and the work scheduled to run while
    * the app is open timed from now, what came due while it was closed at once (`Work`). On Android, where the
    * build names it, the platform makes it ready by itself as well; registering it there changes nothing. */
  def register(app: BackgroundApp): Unit = {
    start(app)
    Work.resume()
  }

  /** `app` made ready, once a process: the platform's, where the app's `main` may not run. */
  private[everyui] def start(app: BackgroundApp): Unit = synchronized {
    if (!started.exists(_ eq app)) {
      started = Some(app)
      app.prepare()
    }
  }

  /** None made ready: for specs, which start several in one process. */
  private[everyui] def forget(): Unit = synchronized {
    started = None
  }

  /** Work `key` run by the app, or Failed where no `BackgroundApp` was made ready. */
  private[everyui] def work(key: String, data: Map[String, String]): Task[WorkResult] = started match {
    case Some(a) => a.onWork(key, data)
    case None =>
      everyui.render.Platform.log("background: no BackgroundApp for the work " + key)
      Task.pure(WorkResult.Failed)
  }

  private[everyui] def noticeAction(notice: String, action: String, args: Map[String, String]): Task[Unit] =
    started.map(_.onNoticeAction(notice, action, args)).getOrElse(Task.unit)

  private[everyui] def noticeDismissed(notice: String): Task[Unit] =
    started.map(_.onNoticeDismissed(notice)).getOrElse(Task.unit)

  private[everyui] def pushAction(action: everyui.push.PushAction): Task[Unit] =
    started.map(_.onPushAction(action)).getOrElse(Task.unit)

  private[everyui] def restart(reason: RestartReason): Task[Unit] =
    started.map(_.onRestart(reason)).getOrElse(Task.unit)
}
