package everyui.activity

import android.app.Activity
import android.window.{OnBackInvokedCallback, OnBackInvokedDispatcher}
import everyui.render.Host

/** Back where the app has opted in to predictive back (Android 13 and up, the default from Android 16 for an app that
  * targets it): the system no longer sends the Back key to the activity, only to a callback registered for it, and with
  * none it closes the app. This hands it to the shared layer's hook as the key would have been, and leaves the app
  * (to the background, as Android does itself) only when nothing took it, asking first while the app is guarded
  * (LeaveBack). Back is always the app's, never an embed's, and held with nothing: what the last key left in
  * `Host.embedded` and `Host.modifiers` (a key typed into a web view) is not this one's, and with an embed named the
  * shared layer handed Back to the keymap alone, so an open menu or dialog never heard it and the app went to the
  * background instead. A class of its own, so an older Android never loads the interface it implements. */
class BackCallback(activity: Activity) extends OnBackInvokedCallback {
  override def onBackInvoked(): Unit = {
    Host.embedded = ""
    Host.modifiers = everyui.event.Modifiers(false, false, false, false)
    val hook = Host.key
    val taken = hook != null && hook("Back", true)
    if (hook != null) hook("Back", false)
    if (!taken) {
      if (LeaveBack.holds()) LeaveBack.ask(activity) else activity.moveTaskToBack(true)
    }
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
