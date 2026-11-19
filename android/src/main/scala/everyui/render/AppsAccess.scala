package everyui.render

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import everyui.apps.AppLink

/** The other apps on the device (`everyui.apps.Apps`): an activity started for the link (`IntentPlan`), and the
  * PackageManager asked whether a package is installed. From Android 11 an app sees only the packages its manifest
  * names in `<queries>` (or the intents it lists there): one it does not name reads as not installed, and its
  * launcher is not found. A data URI or an intent URI is started without asking, so a link with one opens the app
  * though it is not named. */
object AppsAccess {
  def launches(): Boolean = true

  def launch(link: AppLink, done: Boolean => Unit): Unit = done(intent(IntentPlan.of(link)) match {
    case Some(i) =>
      try {
        Host.activity.startActivity(i)
        true
      }
      catch {
        case t: Throwable =>
          Platform.log("launch: nothing opens " + link.id + " (" + t.toString + ")")
          false
      }
    case None =>
      Platform.log("launch: no way into " + link.id + " (not installed, not in the manifest's <queries>, or no launcher)")
      false
  })

  def installed(id: String, done: Option[Boolean] => Unit): Unit = done(if (id.isEmpty) None else Some(
    try {
      Host.activity.getPackageManager.getApplicationInfo(id, 0)
      true
    }
    catch { case _: PackageManager.NameNotFoundException => false }))

  // the intent the plan describes, None where there is none to start (no launcher in the package, an intent URI
  // that does not parse)
  private def intent(plan: IntentPlan): Option[Intent] = {
    val made =
      if (plan.intentUri.nonEmpty)
        try Some(Intent.parseUri(plan.intentUri, Intent.URI_INTENT_SCHEME))
        catch { case _: java.net.URISyntaxException => None }
      else if (plan.launcher) launcher(plan.pkg)
      else if (plan.action.isEmpty) None
      else {
        val i = new Intent(plan.action)
        if (plan.data.nonEmpty && plan.mime.nonEmpty) i.setDataAndType(Uri.parse(plan.data), plan.mime)
        else if (plan.data.nonEmpty) i.setData(Uri.parse(plan.data))
        else if (plan.mime.nonEmpty) i.setType(plan.mime)
        if (plan.pkg.nonEmpty) i.setPackage(plan.pkg)
        Some(i)
      }
    made.map { i =>
      plan.extras.foreach { case (k, v) => i.putExtra(k, v) }
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  // a television's launcher first (LEANBACK_LAUNCHER) on one, the phone's otherwise, either where the other is missing
  private def launcher(pkg: String): Option[Intent] = {
    val pm = Host.activity.getPackageManager
    val tv = Option(pm.getLeanbackLaunchIntentForPackage(pkg))
    val phone = Option(pm.getLaunchIntentForPackage(pkg))
    if (Platform.remoteNavigation()) tv.orElse(phone) else phone.orElse(tv)
  }
}
