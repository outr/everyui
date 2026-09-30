package everyui.app.screen

import everyui.async.Task
import everyui.keymap.Keymap
import everyui.seo.SEO
import everyui.task.TaskSupport

/** youi's `Screen`, reduced to its lifecycle (init, load, activate) and its task support. */
trait Screen extends TaskSupport {
  def title: String = ""

  /** What this screen says about itself, while it is shown, to search engines, answer engines and link previews
    * (`everyui.seo.SEO`): the page's head follows it. It is read as a reactive value, so one built from what the screen
    * has loaded is written again when that arrives. None, the default, leaves the head as it is. */
  protected def seo: Option[SEO] = None

  /** This screen's keyboard shortcuts, in force only while it is the one showing, and asked before the global ones
    * (`everyui.keymap.Keymap`). */
  lazy val keymap: Keymap = new Keymap

  protected def init(): Task[Unit] = Task.unit
  protected def load(): Task[Unit] = Task.unit
  protected def activate(): Task[Unit] = Task.unit
  protected def deactivate(): Task[Unit] = Task.unit

  /** Whether this screen is made for one showing (a title's page, a player), and so is taken apart (`dispose`) once
    * nothing can return to it. A screen an app keeps and shows again (a section of its shell) says no, the default. */
  def oneShowing: Boolean = false
  /** Take the screen apart: it will not be shown again. */
  protected def dispose(): Unit = ()

  /** Build the screen afresh (`ScreenManager.rebuild`): by default its `init` again. */
  protected def rebuild(): Task[Unit] = init()
  /** Asked as the screen is about to be built afresh (`ScreenManager.rebuild`), with what it built still there: what
    * to do once it has been built again, to carry across what lives only in the built UI (a field's words, how far a
    * list is scrolled). State kept outside the built UI (a `Var` on the screen that `createUI` reads) needs nothing,
    * the default. */
  protected def onRebuild(): () => Unit = () => ()
}

object Screen {
  def init(screen: Screen): Task[Unit] = screen.init()
  def load(screen: Screen): Task[Unit] = screen.load()
  def activate(screen: Screen): Task[Unit] = screen.activate()
  def deactivate(screen: Screen): Task[Unit] = screen.deactivate()
  def seo(screen: Screen): Option[SEO] = screen.seo
  def dispose(screen: Screen): Unit = screen.dispose()
  def rebuild(screen: Screen): Task[Unit] = screen.rebuild()
  def onRebuild(screen: Screen): () => Unit = screen.onRebuild()
}
