package everyui.kit

import everyui.component.*
import everyui.component.types.Semantics
import everyui.focus.Focus
import everyui.i18n.Words
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, Shadow}
import reactify.*

/** A link for the keys past what repeats on every screen (a header, a side bar of sections) to the screen's own
  * content, `to`: the first thing Tab reaches, wherever it is put, and drawn only while it has the focus, at the top
  * left, over what is there. Enter (or a click) puts the focus on the first part of `to` the focus can reach, else on
  * `to` itself. A screen reader hears it as a link. Where a remote moves the focus there is no Tab to skip with, and it
  * takes no part. Put it last among the screen's parts, so it is drawn over them:
  * {{{
  *   children ++= List(header, sidebar, content, new SkipLink(content))
  * }}} */
class SkipLink(to: => Component, label: String = Words.skipToContent) extends Container {
  private val words = new TextView {
    content @= label
    font.size @= 14.0
    color := Theme.onAccent()
    position.left @= 14.0
    position.top @= 8.0
  }
  tabFirst = true
  focusable @= !Platform.remoteNavigation()
  visible @= !Platform.remoteNavigation()
  semantics @= Semantics("link", label = label)
  position.left @= 8.0
  position.top @= 8.0
  // no room at all until it has the focus, so no press meant for what is under it lands on it
  size.width := (if (focused()) words.size.width() + 28.0 else 0.0)
  size.height := (if (focused()) words.size.height() + 16.0 else 0.0)
  radius @= 6.0
  backgroundColor := Theme.accent()
  shadow @= Some(Shadow(0.0, 2.0, 8.0, Color.Black.withAlpha(0.4)))
  // there to be found by the keys, not seen until then
  opacity := (if (focused()) 1.0 else 0.0)
  children += words
  event.click.attach { e =>
    e.consume()
    skip()
  }

  /** The focus on the first part of `to` it can reach, else on `to`. */
  def skip(): Unit = {
    val target = to
    Focus.candidates(target).headOption.getOrElse(target).focus()
  }
}
