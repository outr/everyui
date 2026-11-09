package everyui.app

import everyui.component.{Button, Column, Container, IconButton, ScrollContainer}
import everyui.font.MaterialIcon
import everyui.layout.VerticalLayout
import everyui.list.VirtualList
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** Presses that are scrolls, and what hangs out of a plain column. A side rail drawing a box holds a plain column of
  * icon buttons whose badges hang past the column's edge, within the rail; beside it a scroll of buttons and, under
  * that, a list whose every click activates its row (`clickActivates`), as a picker's does. A finger dragged over
  * either scrolls it and presses nothing as it lifts; a tap presses what it is on. `theme=light`. Logs
  * "press button <n>", "press row <n>" and "press badge <name>" as each is pressed. */
object PressDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val badges = List((MaterialIcon.Inbox, "Inbox", Some("3")), (MaterialIcon.Queue, "Queue", Some("12")),
      (MaterialIcon.Notifications, "Alerts", Some("")))
    val column = Column(4.0)(badges.map { case (glyph, name, badge) =>
      val b = new IconButton(glyph, name, () => Platform.log("press badge " + name))
      b.badge @= badge
      b
    }*)
    column.position.left @= 12.0
    column.position.top @= 16.0
    val rail = new Container {
      size.width @= 64.0
      size.height := ui.size.height()
      backgroundColor := Theme.raised()
      children += column
    }
    val across = () => math.max(200.0, ui.size.width() - 96.0)
    val scroll = new ScrollContainer {
      size.width := across()
      size.height := (ui.size.height() - 48.0) / 2.0
      position.left @= 80.0
      position.top @= 16.0
      content.layout @= Some(VerticalLayout(8.0))
    }
    scroll.content.children ++= (0 until 30).map { i =>
      val b = new Button("Button " + i.toString, () => Platform.log("press button " + i.toString))
      b.size.width := across() - 16.0
      b
    }
    val list = VirtualList.text[String](Var((0 until 100).map("Row " + _).toVector), s => s, s => s)
    list.size.width := across()
    list.size.height := (ui.size.height() - 48.0) / 2.0
    list.position.left @= 80.0
    list.position.top := scroll.position.top() + scroll.size.height() + 16.0
    list.clickActivates @= Some(true)
    list.activated.attach(s => Platform.log("press row " + s.drop(4)))
    ui.children ++= List(rail, scroll, list)
    Platform.log("press ok")
  }
}
