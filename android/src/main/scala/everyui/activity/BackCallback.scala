package everyui.activity

import android.app.Activity
import android.window.{OnBackInvokedCallback, OnBackInvokedDispatcher}
import everyui.render.Host

/** Back where the app has opted in to predictive back (Android 13 and up, the default from Android 16 for an app that
  * targets it): the system no longer sends the Back key to the activity, only to a callback registered for it, and with
  * none it closes the app. This hands it to the shared layer's hook as the key would have been, and leaves the app
  * (to the background, as Android does itself) only when nothing took it. A class of its own, so an older Android
  * never loads the interface it implements. */
class BackCallback(activity: Activity) extends OnBackInvokedCallback {
  override def onBackInvoked(): Unit = {
    val hook = Host.key
    val taken = hook != null && hook("Back", true)
    if (hook != null) hook("Back", false)
    if (!taken) activity.moveTaskToBack(true)
  }
}

object BackCallback {
  /** Registered on `activity` from Android 13. The system calls it only where the app has opted in; otherwise Back
    * still arrives as a key, and this is never asked. */
  def register(activity: Activity): Unit =
    if (android.os.Build.VERSION.SDK_INT >= 33)
      activity.getOnBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,
        new BackCallback(activity))
}
