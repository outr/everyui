package everyui.app.screen

import everyui.async.Task
import everyui.seo.SEO
import everyui.task.TaskSupport

/** youi's `Screen`, reduced to its lifecycle (init, load, activate) and its task support. */
trait Screen extends TaskSupport {
  def title: String = ""

  /** What this screen says about itself, while it is shown, to search engines, answer engines and link previews
    * (`everyui.seo.SEO`): the page's head follows it. It is read as a reactive value, so one built from what the screen
    * has loaded is written again when that arrives. None, the default, leaves the head as it is. */
  protected def seo: Option[SEO] = None

  protected def init(): Task[Unit] = Task.unit
  protected def load(): Task[Unit] = Task.unit
  protected def activate(): Task[Unit] = Task.unit
  protected def deactivate(): Task[Unit] = Task.unit

  /** Whether this screen is made for one showing (a title's page, a player), and so is taken apart (`dispose`) once
    * nothing can return to it. A screen an app keeps and shows again (a section of its shell) says no, the default. */
  def oneShowing: Boolean = false
  /** Take the screen apart: it will not be shown again. */
  protected def dispose(): Unit = ()
}

object Screen {
  def init(screen: Screen): Task[Unit] = screen.init()
  def load(screen: Screen): Task[Unit] = screen.load()
  def activate(screen: Screen): Task[Unit] = screen.activate()
  def deactivate(screen: Screen): Task[Unit] = screen.deactivate()
  def seo(screen: Screen): Option[SEO] = screen.seo
  def dispose(screen: Screen): Unit = screen.dispose()
}
