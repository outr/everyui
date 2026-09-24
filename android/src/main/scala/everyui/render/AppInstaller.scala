package everyui.render

import android.app.PendingIntent
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.content.pm.PackageInstaller

/** An app installing a newer build of itself, for a copy no store installed (Platform.installApp): the APK fetched to
  * the cache, then handed to the system's PackageInstaller, which asks the viewer itself — nothing installs without
  * their say. A store install never does this: Play forbids it, and Amazon expects its own updates.
  *
  * The confirmation is not shown by committing the session. The system answers the commit with "pending user action"
  * and the screen to show, which the app has to start; left alone, the download ends in silence (the Flutter app's
  * "it says downloading but it doesn't install", 2026-08-17). The answer comes to a receiver made for the one install. */
object AppInstaller {
  private val Answer = "everyui.INSTALL_RESULT"

  /** Whether it may install at all: Android 8's per-app "install unknown apps", which the viewer gives in Settings. */
  def allowed: Boolean =
    android.os.Build.VERSION.SDK_INT < 26 || Host.activity.getPackageManager.canRequestPackageInstalls()

  /** The Settings screen where that is given, for this app. */
  def openSettings(): Unit = {
    val intent =
      if (android.os.Build.VERSION.SDK_INT >= 26)
        new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
          android.net.Uri.parse("package:" + Host.activity.getPackageName))
      else new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
    try Host.activity.startActivity(intent)
    catch { case t: Throwable => Platform.logError("install: no settings screen", t) }
  }

  /** The APK at `url` fetched (`progress` from 0 to 1 while it comes) and handed to the installer; `done` hears
    * nothing when the system has it, or what went wrong. Everything the viewer sees is on the main thread. */
  def install(url: String, progress: Double => Unit, done: Option[String] => Unit): Unit = {
    def main(f: => Unit): Unit = Host.activity.runOnUiThread(() => f)
    new Thread(() => {
      val file = new java.io.File(Host.activity.getCacheDir, "update.apk")
      val failure: Option[String] =
        try {
          val connection = new java.net.URL(url).openConnection().asInstanceOf[java.net.HttpURLConnection]
          connection.setConnectTimeout(15000)
          connection.setReadTimeout(30000)
          if (connection.getResponseCode != 200) Some("The download failed (" + connection.getResponseCode.toString + ")")
          else {
            val total = connection.getContentLength.toLong
            val in = connection.getInputStream
            val out = new java.io.FileOutputStream(file)
            var received = 0L
            try {
              val buffer = new Array[Byte](64 * 1024)
              var n = in.read(buffer)
              while (n >= 0) {
                out.write(buffer, 0, n)
                received += n
                if (total > 0) {
                  val share = received.toDouble / total
                  main(progress(share))
                }
                n = in.read(buffer)
              }
            } finally {
              in.close()
              out.close()
            }
            if (total > 0 && received < total) Some("The download was incomplete. Please try again.")
            else commit(file)
          }
        }
        catch { case t: Throwable => Some("The update failed: " + Option(t.getMessage).getOrElse(t.getClass.getSimpleName)) }
      main(done(failure))
    }).start()
  }

  /** The file streamed into an installer session and committed; the system's answer comes to a receiver made for it. */
  private def commit(file: java.io.File): Option[String] = {
    val context = Host.activity
    val installer = context.getPackageManager.getPackageInstaller
    val params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
    params.setAppPackageName(context.getPackageName)
    val id = installer.createSession(params)
    val session = installer.openSession(id)
    try {
      val out = session.openWrite("update.apk", 0, file.length())
      val in = new java.io.FileInputStream(file)
      try {
        // a copy by hand: InputStream.transferTo is Android 13's
        val buffer = new Array[Byte](64 * 1024)
        var n = in.read(buffer)
        while (n >= 0) {
          out.write(buffer, 0, n)
          n = in.read(buffer)
        }
        session.fsync(out)
      } finally {
        in.close()
        out.close()
      }
      listen()
      val flags = PendingIntent.FLAG_UPDATE_CURRENT |
        (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
      val pending = PendingIntent.getBroadcast(context, id, new Intent(Answer).setPackage(context.getPackageName), flags)
      session.commit(pending.getIntentSender)
      None
    }
    finally session.close()
  }

  private var listening = false

  /** The system's answer: its confirmation screen started where it asks for one. */
  private def listen(): Unit = if (!listening) {
    listening = true
    val receiver = new BroadcastReceiver {
      override def onReceive(context: Context, intent: Intent): Unit =
        intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MinValue) match {
          case PackageInstaller.STATUS_PENDING_USER_ACTION =>
            Option(intent.getParcelableExtra[Intent](Intent.EXTRA_INTENT)).foreach { confirm =>
              confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              try Host.activity.startActivity(confirm)
              catch { case t: Throwable => Platform.logError("install: the confirmation would not open", t) }
            }
          case PackageInstaller.STATUS_SUCCESS => Platform.log("install: done")
          case status =>
            Platform.log("install: status " + status.toString + " " +
              Option(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)).getOrElse(""))
        }
    }
    if (android.os.Build.VERSION.SDK_INT >= 33)
      Host.activity.registerReceiver(receiver, new IntentFilter(Answer), Context.RECEIVER_NOT_EXPORTED)
    else Host.activity.registerReceiver(receiver, new IntentFilter(Answer))
  }
}
