package everyui.render

import android.app.{Notification, NotificationChannel, NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.os.{Build, Handler, Looper, SystemClock}
import everyui.app.Busy

/** The app at work (`everyui.app.AppBusy`), as an ongoing notification of its own with a progress bar (a moving one
  * where there is no progress), which goes once the app is idle. It is on a quiet channel of its own
  * ("everyui.busy": no sound, no heads-up), apart from any other notification the app posts, and a tap on it brings
  * the app back.
  *
  * Android 13 and later ask the person before an app posts one (`AppBusy.permit`); without that the notification is
  * simply not shown. An Android TV shows no notifications of this kind, so there it is not `supported` and nothing is
  * posted.
  *
  * The system drops an app's notifications that change faster than a few times a second, so a change is posted at
  * most every quarter second, the last one always. A notification outlives the app's process when the process is
  * killed, so each is posted to time out after a minute and a half, and posted again every thirty seconds while the app
  * is still busy. */
object BusyDisplay {
  private val channel = "everyui.busy"
  private val id = 0x6275   // "bu"
  private val gap = 250L
  private val refresh = 30000L
  private lazy val handler = new Handler(Looper.getMainLooper)

  private var busy: Option[Busy] = None
  private var posted = 0L
  private var waiting = false
  private var channelMade = false
  private val keepAlive: Runnable = () => if (busy.nonEmpty) post()
  private val trailing: Runnable = () => {
    waiting = false
    post()
  }

  private def context: Context = Host.activity

  private def manager: NotificationManager = context.getSystemService(classOf[NotificationManager])

  def supported(): Boolean = context != null && !tv()

  def permitted(): Boolean = supported() && manager.areNotificationsEnabled &&
    (Build.VERSION.SDK_INT < 33 || Permissions.held("android.permission.POST_NOTIFICATIONS"))

  def permit(done: Boolean => Unit): Unit =
    if (!supported()) done(false)
    else if (Build.VERSION.SDK_INT < 33) done(manager.areNotificationsEnabled)
    else Permissions.ask("android.permission.POST_NOTIFICATIONS")(granted => done(granted && manager.areNotificationsEnabled))

  /** `still` (AppBusy.stillAfter) is the web's: a notification's bar shows no orbit. */
  def show(next: Option[Busy], inTitle: Boolean, still: Option[Double]): Unit = if (supported()) {
    busy = next
    val since = SystemClock.elapsedRealtime() - posted
    if (next.isEmpty || since >= gap) post()
    else if (!waiting) {
      waiting = true
      handler.postDelayed(trailing, gap - since)
    }
  }

  private def post(): Unit = {
    handler.removeCallbacks(keepAlive)
    if (waiting) {
      handler.removeCallbacks(trailing)
      waiting = false
    }
    posted = SystemClock.elapsedRealtime()
    busy match {
      case None => manager.cancel(id)
      case Some(b) =>
        if (permitted()) {
          ensureChannel()
          manager.notify(id, notification(b))
        }
        handler.postDelayed(keepAlive, refresh)
    }
  }

  private def notification(b: Busy): Notification = {
    val info = context.getApplicationInfo
    val launch = Option(context.getPackageManager.getLaunchIntentForPackage(context.getPackageName))
      .getOrElse(new Intent(context, context.getClass))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    val builder = new Notification.Builder(context, channel)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle(b.name.getOrElse(context.getPackageManager.getApplicationLabel(info).toString))
      .setProgress(100, b.percent.getOrElse(0), b.percent.isEmpty)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setCategory(Notification.CATEGORY_PROGRESS)
      .setTimeoutAfter(refresh * 3)
      .setContentIntent(PendingIntent.getActivity(context, id, launch,
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
    b.percent.foreach(p => builder.setContentText(p.toString + "%"))
    builder.build()
  }

  private def ensureChannel(): Unit = if (!channelMade) {
    val made = new NotificationChannel(channel, "In progress", NotificationManager.IMPORTANCE_LOW)
    made.setShowBadge(false)
    manager.createNotificationChannel(made)
    channelMade = true
  }

  private def tv(): Boolean =
    context.getSystemService(Context.UI_MODE_SERVICE).asInstanceOf[android.app.UiModeManager].getCurrentModeType ==
      android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
      context.getPackageManager.hasSystemFeature("android.software.leanback")
}
