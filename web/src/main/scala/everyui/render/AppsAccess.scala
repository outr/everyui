package everyui.render

import everyui.apps.AppLink

/** The other apps on the device (`everyui.apps.Apps`), asked of the host the page runs in as an installed app
  * (`AppHost`: an LG TV's Application Manager). A browser opens no other app and cannot say which are installed. */
object AppsAccess {
  def launches(): Boolean = AppHosts.current.exists(_.launchesApps)

  def launch(link: AppLink, done: Boolean => Unit): Unit = AppHosts.current match {
    case Some(host) => host.launchApp(link, done)
    case None => done(false)
  }

  def installed(id: String, done: Option[Boolean] => Unit): Unit = AppHosts.current match {
    case Some(host) => host.appInstalled(id, done)
    case None => done(None)
  }
}
