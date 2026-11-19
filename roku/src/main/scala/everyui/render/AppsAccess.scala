package everyui.render

import everyui.apps.AppLink
import everyui.roku.uiAppsInstalled

/** The other apps on the device (`everyui.apps.Apps`): a Roku says whether a channel is installed (roAppManager's
  * IsAppInstalled) but opens none. roAppManager's LaunchApp would, and Roku's static analysis refuses a channel that
  * names it (certification 2.4, 4.6 and 5.3), so it is not in the runtime: another channel is opened from outside,
  * by ECP's `/launch/<id>`. */
object AppsAccess {
  def launches(): Boolean = false

  def launch(link: AppLink, done: Boolean => Unit): Unit = done(false)

  def installed(id: String, done: Option[Boolean] => Unit): Unit =
    done(if (id.isEmpty) None else Some(uiAppsInstalled(id) == id))
}
