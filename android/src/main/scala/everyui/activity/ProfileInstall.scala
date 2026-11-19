package everyui.activity

import android.content.Context

/** The APK's baseline profile (sbt-everyui's AndroidApk writes it) handed to ART on the first launch of each install
  * by androidx.profileinstaller (in `EveryUILibraries.android`), as its own initialiser would where androidx.startup
  * ran it. Before Android 14 nothing but Play hands it over at install (`adb install` and a sideload do not), and an
  * Android 14 or later reads it out of the APK itself (`assets/art-profile`) only where its ART module does (the
  * API 35 emulator's, 35.08, does; a Google TV Streamer's on 14, 34.09, does not), so it is written where ART's next
  * background compilation reads it, and the app runs compiled from then on. Five seconds after the first frame, on a
  * thread of the lowest priority, so it costs the start nothing; once an install it does anything at all. An app
  * built without profileinstaller skips it. */
object ProfileInstall {
  def later(activity: android.app.Activity): Unit = {
    val context = activity.getApplicationContext
    android.view.Choreographer.getInstance().postFrameCallback(_ =>
      new android.os.Handler(android.os.Looper.getMainLooper).postDelayed(() => {
        val thread = new Thread(() => write(context), "everyui-profile")
        thread.setPriority(Thread.MIN_PRIORITY)
        thread.setDaemon(true)
        thread.start()
      }, 5000L))
  }

  private def write(context: Context): Unit =
    try androidx.profileinstaller.ProfileInstaller.writeProfile(context, (r: Runnable) => r.run(),
      new androidx.profileinstaller.ProfileInstaller.DiagnosticsCallback {
        def onDiagnosticReceived(code: Int, data: AnyRef): Unit = ()
        def onResultReceived(code: Int, data: AnyRef): Unit =
          android.util.Log.i("everyui", s"baseline profile: profileinstaller result $code" + Option(data).fold("")(d => s" ($d)"))
      })
    catch {
      case _: LinkageError => ()   // the app's build left profileinstaller out
      case t: Throwable => android.util.Log.w("everyui", "baseline profile not installed: " + t)
    }
}
