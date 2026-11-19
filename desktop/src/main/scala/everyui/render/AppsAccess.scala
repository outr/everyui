package everyui.render

import everyui.apps.AppLink

/** The other apps on the device (`everyui.apps.Apps`): the desktop opens none and cannot say which are installed. */
object AppsAccess {
  def launches(): Boolean = false

  def launch(link: AppLink, done: Boolean => Unit): Unit = done(false)

  def installed(id: String, done: Option[Boolean] => Unit): Unit = done(None)
}
