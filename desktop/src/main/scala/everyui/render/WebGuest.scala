package everyui.render

import everyui.component.EmbedSite
import javafx.scene.layout.Pane

/** A web guest running in an embed's pane on the desktop (Nodes.embed): the host's end of its channel.
  *
  * JavaFX's web view is a module of its own (javafx-web, a large native library), which not every desktop app wants,
  * so the one that runs guests (`FxWebGuest`, in `desktop/web/`) is compiled only into a build that adds it, and is
  * found here by name. Without it `Platform.embeds()` is false and an embed shows its fallback. */
trait WebGuest {
  /** The id of the element the guest draws into, which is also its runtime: each embed is a web view of its own. */
  def id: String
  def send(message: String): Unit
  def hear(f: String => Unit): Unit
  def focus(on: Boolean): Unit
  def onFocus(f: Boolean => Unit): Unit
  def release(): Unit
}

object WebGuest {
  private lazy val maker: Option[java.lang.reflect.Constructor[?]] =
    try Some(Class.forName("everyui.render.FxWebGuest").getConstructor(classOf[Pane], classOf[String], classOf[EmbedSite]))
    catch { case _: Throwable => None }

  /** Whether this build can run a web guest. */
  def available: Boolean = maker.nonEmpty

  /** A guest running `site` in `pane`, if this build can run one. */
  def make(pane: Pane, name: String, site: EmbedSite): Option[WebGuest] =
    maker.map(_.newInstance(pane, name, site).asInstanceOf[WebGuest])
}
