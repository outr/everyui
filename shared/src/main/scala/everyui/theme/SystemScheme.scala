package everyui.theme

import everyui.render.Platform
import reactify.*

/** "dark" or "light": what the person has asked their device for, followed as they change it. The web's
  * `prefers-color-scheme`, Android's dark theme (its night mode), the desktop's own setting as JavaFX reads it; a Roku
  * and an Apple TV are "dark". An app that follows it sets its palette from it, with `Theme.follow`. */
object SystemScheme {
  lazy val scheme: Val[String] = {
    val now = Var(Platform.systemColorScheme())
    Platform.watchColorScheme(s => if (now() != s) now @= s)
    now
  }

  def apply(): String = scheme()

  /** Whether the person asked for dark. */
  def dark: Boolean = scheme() == "dark"
}
