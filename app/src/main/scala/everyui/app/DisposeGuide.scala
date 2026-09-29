package everyui.app

import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Button, Component, Container, ImageView, Row, ScrollContainer, TextView}
import everyui.layout.HorizontalLayout
import everyui.net.*
import everyui.theme.Theme
import everyui.util.{Clock, Format}

/** A theatre guide, shown once and taken apart as it is left (`oneShowing`): theatres down the page, each with its name,
  * a tile (a poster and the time since it opened, following the clock) and a rail of showtimes, every one a button the
  * focus can land on. What `DisposeDemo` builds, leaves, and looks for afterwards. */
class DisposeGuide(val manager: ScreenManager, theatres: Int = DisposeGuide.Theatres) extends ScrollScreen {
  override def title: String = "Guide"
  override def path: URLPath = path"/guide"
  override def oneShowing: Boolean = true

  /** Every showtime, in order: the first takes the focus. */
  var showtimes: List[Button] = Nil

  override def defaultFocus(): Component = showtimes.headOption.getOrElse(container)

  private def theatre(i: Int): Component = {
    val name = TextView(DisposeGuide.Theatre + " " + i.toString, 18.0)
    val opened = Clock.real()
    val tile = new Container {
      size.width @= 160.0
      size.height @= 120.0
      backgroundColor := Theme.surface()
    }
    val poster = ImageView("/images/logo.png", 90.0)
    val since = TextView.bound(Format.elapsed(opened, None), 11.0, Theme.muted())
    since.position.top @= 96.0
    tile.children ++= List(poster, since)
    val rail = new ScrollContainer(horizontal = true)
    rail.size.width := body.size.width() - 176.0
    rail.size.height @= 44.0
    rail.content.layout @= Some(HorizontalLayout(8.0))
    val times = (0 until DisposeGuide.Showtimes).toList.map(k => new Button((k + 5).toString + ":30 PM"))
    showtimes = showtimes ++ times
    rail.content.children ++= times
    Row(16.0, middle = true)(tile, everyui.component.Column(8.0)(name, rail))
  }

  override def createUI(): Task[Unit] = Task {
    container.backgroundColor := Theme.background()
    body.children ++= (1 to theatres).toList.map(theatre)
  }
}

object DisposeGuide {
  /** What each theatre is called, before its number: what is looked for once the guide is gone. */
  val Theatre: String = "Bricktown"
  val Theatres: Int = 12
  val Showtimes: Int = 9
}
