package everyui.activity

import android.app.{Activity, KeyguardManager}
import android.content.{Context, Intent, ActivityNotFoundException}
import android.net.Uri

/** Dispatch is observable; it is never a claim that navigation actually began in a car host. */
enum ActionResult { case Dispatched, Unavailable, NeedsVisibleActivity, NeedsUnlock, Invalid }
object ExternalActions {
  def navigationIntent(destination: String, mode: String = "d"): Option[Intent] =
    Option.when(destination.trim.nonEmpty && destination.length <= 512 && Set("d", "w", "b").contains(mode)) {
      new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination.trim) + "&mode=" + mode))
        .setPackage("com.google.android.apps.maps")
    }
  def navigate(activity: Option[Activity], destination: String, mode: String = "d"): ActionResult =
    navigationIntent(destination, mode) match {
      case None => ActionResult.Invalid
      case Some(intent) => activity match {
        case None => ActionResult.NeedsVisibleActivity
        case Some(a) if a.isFinishing || a.isDestroyed => ActionResult.NeedsVisibleActivity
        case Some(a) if a.getSystemService(Context.KEYGUARD_SERVICE).asInstanceOf[KeyguardManager].isKeyguardLocked => ActionResult.NeedsUnlock
        case Some(a) =>
          if (intent.resolveActivity(a.getPackageManager) == null) ActionResult.Unavailable
          else try { a.startActivity(intent); ActionResult.Dispatched }
          catch {
            case _: ActivityNotFoundException => ActionResult.Unavailable
            case _: SecurityException => ActionResult.NeedsVisibleActivity
          }
      }
    }
}
