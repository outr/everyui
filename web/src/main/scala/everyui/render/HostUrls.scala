package everyui.render

import everyui.component.NavigationUrl

/** Where the web's `Platform.openUrl` sends an address: a page opens it in a new tab (`tab`); an installed app on a TV
  * (`AppHost`) hands it to the system's browser, never to its own window, which on a TV has no way back from another
  * site. `done` is told whether it was opened: false for an address no platform opens, or one the host could not (or
  * would not) open, which is logged. Plain Scala, so the desktop's specs read it. */
object HostUrls {
  def open(url: String, host: Option[AppHost], tab: String => Unit, done: Boolean => Unit): Unit =
    if (!NavigationUrl.safe(url)) done(false)
    else host match {
      case Some(h) => h.openUrl(url, opened => {
        if (!opened) Platform.log("openUrl: nothing opens " + url)
        done(opened)
      })
      case None =>
        tab(url)
        done(true)
    }
}
