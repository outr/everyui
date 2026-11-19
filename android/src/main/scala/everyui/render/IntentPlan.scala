package everyui.render

import everyui.apps.AppLink

/** How Android starts another app for an `AppLink` (`AppsAccess.launch`), as plain Scala, so the desktop's specs read
  * it as the device does: a whole intent URI (`intentUri`, `Intent.parseUri`), else an action on a data URI aimed at
  * a package, else the package's own launcher (`launcher`), with the link's params as string extras on any of them.
  * Empty strings are unset. */
case class IntentPlan(intentUri: String, action: String, data: String, mime: String, pkg: String, launcher: Boolean,
                      extras: Map[String, String])

object IntentPlan {
  val View: String = "android.intent.action.VIEW"

  def of(link: AppLink): IntentPlan =
    if (link.intent.nonEmpty) IntentPlan(link.intent, "", "", "", "", launcher = false, link.params)
    else if (link.uri.nonEmpty || link.mime.nonEmpty)
      IntentPlan("", if (link.action.nonEmpty) link.action else View, link.uri, link.mime, link.id, launcher = false, link.params)
    else if (link.action.nonEmpty) IntentPlan("", link.action, "", "", link.id, launcher = false, link.params)
    else IntentPlan("", "", "", "", link.id, launcher = link.id.nonEmpty, link.params)
}
