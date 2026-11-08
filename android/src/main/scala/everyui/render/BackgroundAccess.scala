package everyui.render

import android.content.{Context, Intent}
import android.content.pm.PackageManager
import android.os.{Bundle, Handler, Looper}
import everyui.SettingsPage
import everyui.activity.ServiceRuntime
import everyui.app.screen.ScreenManager
import everyui.async.Task
import everyui.background.{Background, BackgroundApp, RestartReason, WorkRequest}
import everyui.push.OngoingNotice
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*

/** What runs with the app closed (`everyui.background`) on Android: the app's `BackgroundApp`, loaded by the name its
  * manifest gives under the application's meta-data "everyui.background" (sbt-everyui writes it from
  * `everyuiBackground`, with `BackgroundReceiver` and `BackgroundJobs`), and made ready once a process; the handlers
  * it is asked to run, each held to a deadline; scheduled work (`BackgroundWork`), ongoing notices
  * (`OngoingNotices`) and the settings pages (`SettingsPages`).
  *
  * A receiver or a job runs with no activity, often in a process started for it alone, so nothing here needs one:
  * the application's context, its settings and its secrets, and the network made ready where no activity has. Each
  * handler is started on the main thread, as the app's own tasks are. */
object BackgroundAccess {
  /** An ongoing notice's action that runs in the background, as `BackgroundReceiver` hears it. */
  val noticeIntent = "everyui.background.NOTICE"
  /** An ongoing notice swiped away. */
  val dismissedIntent = "everyui.background.DISMISSED"
  val noticeExtra = "everyui.background.notice"
  val actionExtra = "everyui.background.action"
  val argsExtra = "everyui.background.args"
  /** An open tap's extra, which the activity's new intent is told by (`tapped`). */
  val openedExtra = "everyui.background.opened"
  private val appMeta = "everyui.background"

  /** How long a receiver's handler (a restart, a notice's action) has before the receiver lets go: Android's limit
    * for a broadcast in the background is a minute, and a short one keeps the process from being taken as hung. */
  val receiverDeadline: Long = 10000L
  /** How long work has before it is taken as needing another try: Android stops a job at ten minutes. */
  val jobDeadline: Long = 570000L

  private lazy val main = new Handler(Looper.getMainLooper)
  @volatile private var ready = false

  /** The app's `BackgroundApp` made ready in this process, once: the runtime, the network where no activity has made
    * it ready, and the app's object, loaded by name and prepared. */
  def start(context: Context): Unit = synchronized {
    ServiceRuntime.init(context)
    if (!ready) {
      ready = true
      if (Host.activity == null) everyui.network.NetworkBackend.init()
      app(context).foreach { a =>
        try Background.start(a)
        catch { case t: Throwable => Platform.logError("background: the app's BackgroundApp failed to prepare", t) }
      }
    }
  }

  private def named(context: Context): Option[String] = try {
    val info = context.getPackageManager.getApplicationInfo(context.getPackageName, PackageManager.GET_META_DATA)
    Option(info.metaData).flatMap(m => Option(m.getString(appMeta))).filter(_.nonEmpty)
  }
  catch { case _: Throwable => None }

  private def app(context: Context): Option[BackgroundApp] = named(context) match {
    case Some(name) =>
      try {
        // a Scala object: its class, and the one instance it holds
        val module = Class.forName(name + "$", true, context.getClassLoader).getField("MODULE$")
        Some(module.get(null).asInstanceOf[BackgroundApp])
      }
      catch {
        case t: Throwable =>
          Platform.logError("background: the app's BackgroundApp " + name + " could not be loaded", t)
          None
      }
    case None =>
      Platform.log("background: the manifest names no BackgroundApp (meta-data " + appMeta + ")")
      None
  }

  @volatile private var declared: Option[Boolean] = None

  /** Whether the app's manifest names a `BackgroundApp`, so its receiver and job service are there. */
  def supported(): Boolean = declared.getOrElse {
    val is = try named(ServiceRuntime.context).nonEmpty catch { case _: IllegalStateException => false }
    declared = Some(is)
    is
  }

  /** `task` started on the main thread, and `done` with its answer, or with `otherwise` once `deadline` passes or
    * where it fails; once either way. */
  def run[T](what: String, task: => Task[T], deadline: Long, otherwise: T)(done: T => Unit): Unit = {
    val once = new AtomicBoolean(false)
    val answer = (t: T) => if (once.compareAndSet(false, true)) {
      try done(t) catch { case e: Throwable => Platform.logError("background: " + what + " answered", e) }
    }
    main.postDelayed(() => if (!once.get) {
      Platform.log("background: " + what + " had no answer in " + (deadline / 1000L).toString + " s")
      answer(otherwise)
    }, deadline)
    main.post(() =>
      try task.map(t => answer(t)).start()
      catch {
        case e: Throwable =>
          Platform.logError("background: " + what + " failed", e)
          answer(otherwise)
      })
  }

  /** What `BackgroundReceiver` heard: the device started, the app updated, a notice's action or its dismissal.
    * `finish` once the handler has answered or its deadline has passed. */
  def received(context: Context, intent: Intent, finish: () => Unit): Unit = {
    start(context)
    val action = Option(intent).flatMap(i => Option(i.getAction)).getOrElse("")
    val notice = Option(intent).flatMap(i => Option(i.getStringExtra(noticeExtra))).getOrElse("")
    val handled: Option[(String, () => Task[Unit])] = action match {
      case Intent.ACTION_BOOT_COMPLETED => Some("restart (booted)" -> (() => Background.restart(RestartReason.Booted)))
      case Intent.ACTION_MY_PACKAGE_REPLACED =>
        Some("restart (updated)" -> (() => Background.restart(RestartReason.Updated)))
      case `noticeIntent` =>
        val name = Option(intent.getStringExtra(actionExtra)).getOrElse("")
        val args = Option(intent.getBundleExtra(argsExtra)).map(b =>
          b.keySet.asScala.toList.flatMap(k => Option(b.getString(k)).map(k -> _)).toMap)
          .getOrElse(Map.empty[String, String])
        Some(("notice " + notice + " action " + name) -> (() => Background.noticeAction(notice, name, args)))
      case `dismissedIntent` => Some(("notice " + notice + " dismissed") -> (() => Background.noticeDismissed(notice)))
      case other =>
        Platform.log("background: nothing to do for " + other)
        None
    }
    handled match {
      case Some((what, handler)) =>
        Platform.log("background: " + what)
        run(what, handler(), receiverDeadline, ())(_ => finish())
      case None => finish()
    }
  }

  /** `args` as an intent's bundle of strings. */
  def bundle(args: Map[String, String]): Bundle = {
    val b = new Bundle
    args.foreach((k, v) => b.putString(k, v))
    b
  }

  /** The activity's new intent: an ongoing notice's open tap while the app runs, to its path (a launch has it as
    * `Host.path`). Where the app's screens are not running yet, the path is where they start. */
  def tapped(intent: Intent): Unit =
    if (intent != null && intent.getBooleanExtra(openedExtra, false))
      Option(intent.getStringExtra("path")).filter(_.nonEmpty).foreach { p =>
        if (ScreenManager.running.nonEmpty) ScreenManager.navigate(p)
        else Host.path = p
      }

  // --- the shared objects' calls ---------------------------------------------------------------------------------

  def schedule(request: WorkRequest): Boolean = BackgroundWork.schedule(request)

  def cancel(key: String): Unit = BackgroundWork.cancel(key)

  def pending(key: String): Boolean = BackgroundWork.pending(key)

  def ongoingSupported(): Boolean = OngoingNotices.supported()

  def showOngoing(notice: OngoingNotice): Boolean = OngoingNotices.show(notice)

  def clearOngoing(key: String): Unit = OngoingNotices.clear(key)

  def openSettings(page: SettingsPage, done: Boolean => Unit): Unit = SettingsPages.open(page, done)

  def unusedAppRestrictions(done: Boolean => Unit): Unit = SettingsPages.unusedAppRestrictions(done)
}
