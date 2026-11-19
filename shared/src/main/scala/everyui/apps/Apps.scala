package everyui.apps

import everyui.async.Task
import everyui.render.AppsAccess

/** The other apps on the device: opening one, to its start or to a title in it (`launch`), and whether one is there
  * (`installed`). An LG TV asks its Application Manager (the app declares `application.launcher` and
  * `application.query` in `requiredACG`), Android starts an activity and asks its PackageManager (the manifest names
  * the packages in its `<queries>`), a Roku says whether a channel is installed but opens none. The desktop, the web
  * and an Apple TV open no other app. Everything is heard on the thread that draws.
  *
  * {{{
  *   Apps.installed("netflix").flatMap {
  *     case Some(false) => Task.pure(false)
  *     case _ => Apps.launch(AppLink("netflix", json = """{"contentTarget": "..."}"""))
  *   }.map(opened => if (!opened) Toast.show("Netflix could not be opened")).start()
  * }}} */
object Apps {
  /** Whether this platform can open another app. */
  def launches: Boolean = AppsAccess.launches()

  /** Open `link`'s app, as it asks: true where the platform took the launch, false where the app is not there, would
    * not open, or this platform opens none. The app opened comes in front of this one, which goes on in the
    * background (`Platform.onAway`). */
  def launch(link: AppLink): Task[Boolean] = Task.async[Boolean](done => AppsAccess.launch(link, done))

  /** Whether the app `id` is installed, None where the platform cannot tell (the web, the desktop, an Apple TV, webOS
    * before 4). */
  def installed(id: String): Task[Option[Boolean]] = Task.async[Option[Boolean]](done => AppsAccess.installed(id, done))
}
