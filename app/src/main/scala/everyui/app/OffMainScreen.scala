package everyui.app

import everyui.Color
import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Component, Container, ScrollContainer}
import everyui.focus.Focus
import everyui.layout.HorizontalLayout
import everyui.net.*
import everyui.render.Platform
import reactify.*

/** The off-main demo's one screen: twenty cards in a rail 600 wide, the focus followed with a glide. Three seconds
  * after the app goes away, the first card's colour is changed off the thread that draws (`Platform.offThread`). */
class OffMainScreen extends ScrollScreen {
  override def manager: ScreenManager = OffMainDemo
  override def title: String = "Off main"
  override def path: URLPath = path"/offmain"

  private val rail = new ScrollContainer(horizontal = true) {
    size.width @= 600.0
    size.height @= 120.0
    revealGlide @= 0.4
    content.layout @= Some(HorizontalLayout(20.0))
  }
  private val cards = Vector.tabulate(20) { i =>
    new Container {
      size.width @= 160.0
      size.height @= 100.0
      backgroundColor @= (if (i % 2 == 0) Color.Gray else Color.Blue)
      focusable @= true
    }
  }

  override def defaultFocus(): Component = cards.head

  override def createUI(): Task[Unit] = Task {
    rail.content.children := cards
    body.children += rail
    Focus.focused.attach(_ => Platform.after(1.0, () =>
      Platform.log("offmain rail at " + math.round(rail.offset()).toString)))
    Platform.onAway(() => Platform.after(3.0, () => Platform.offThread { () =>
      cards.head.backgroundColor @= Color.Red
      Platform.log("offmain changed off the main thread")
    }))
  }
}
