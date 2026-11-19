package everyui.render

import everyui.apps.AppLink

/** The other apps on the device (`everyui.apps.Apps`): an Apple TV lets an app open another only by an address it
  * answers (`Platform.openUrl`), and tells none which are installed, so neither is done here. */
object AppsAccess {
  def launches(): Boolean = false

  def launch(link: AppLink, done: Boolean => Unit): Unit = done(false)

  def installed(id: String, done: Option[Boolean] => Unit): Unit = done(None)
}
