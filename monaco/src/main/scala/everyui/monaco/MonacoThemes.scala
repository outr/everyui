package everyui.monaco

import everyui.theme.{Palette, Theme}
import fabric.*

import scala.concurrent.Future

/** Monaco's themes: global to a page, so one theme shows in every editor at once. A theme defined or set here reaches
  * every editor there is, and every editor made later, whichever web view it runs in. */
object MonacoThemes {
  private val defined = scala.collection.mutable.LinkedHashMap.empty[String, MonacoTheme]
  private val styles = scala.collection.mutable.LinkedHashMap.empty[String, String]
  private var shown: Option[String] = None
  private var following: Option[() => Unit] = None

  /** The theme set last, if one has been. */
  def current: Option[String] = shown

  /** `theme` under `name`, replacing one of that name. */
  def defineTheme(name: String, theme: MonacoTheme): Future[Unit] = {
    defined(name) = theme
    everywhere(_.call("editor.defineTheme", obj("name" -> str(name), "theme" -> theme.toJson)))
  }

  /** `name` shown in every editor: one defined here, or Monaco's own `vs`, `vs-dark`, `hc-black`, `hc-light`. */
  def setTheme(name: String): Future[Unit] = {
    shown = Some(name)
    everywhere(_.call("editor.setTheme", obj("theme" -> str(name))))
  }

  /** The editors in the app's colours from now on (`MonacoTheme.fromPalette`, named `MonacoTheme.PaletteName`), and
    * again whenever `Theme.palette` changes. `stopFollowing` lets it be. */
  def followPalette(tokens: Boolean = false, clear: Boolean = false): Future[Unit] = {
    stopFollowing()
    def apply(p: Palette): Future[Unit] = {
      import Monaco.ui
      defineTheme(MonacoTheme.PaletteName, MonacoTheme.fromPalette(p, tokens, clear))
        .flatMap(_ => setTheme(MonacoTheme.PaletteName))
    }
    val reaction = Theme.palette.attach(p => apply(p))
    following = Some(() => Theme.palette.reactions -= reaction)
    apply(Theme.palette())
  }

  def stopFollowing(): Unit = {
    following.foreach(stop => stop())
    following = None
  }

  /** CSS for the classes the app's decorations name, in every editor's page, under `id`: given again, it replaces what
    * that id gave before. everyui-monaco's own classes (MonacoDecoration's helpers) are there already. */
  def addStyles(id: String, css: String): Future[Unit] = {
    styles(id) = css
    everywhere(_.call("bridge.addStyles", obj("id" -> str(id), "css" -> str(css))))
  }

  /** What a copy of Monaco that has just loaded is given first. */
  private[monaco] def replay(bridge: MonacoBridge): List[Future[Json]] =
    styles.toList.map { case (id, css) =>
      bridge.send("bridge.addStyles", obj("id" -> str(id), "css" -> str(css)), Monaco.callTimeout)
    } ++ defined.toList.map { case (name, theme) =>
      bridge.send("editor.defineTheme", obj("name" -> str(name), "theme" -> theme.toJson), Monaco.callTimeout)
    } ++ shown.toList.map(name => bridge.send("editor.setTheme", obj("theme" -> str(name)), Monaco.callTimeout))

  private def everywhere(f: MonacoBridge => Future[Json]): Future[Unit] = {
    import Monaco.ui
    Future.sequence(Monaco.live.map(f)).map(_ => ())
  }
}
