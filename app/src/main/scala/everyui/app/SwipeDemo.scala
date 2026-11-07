package everyui.app

import everyui.Color
import everyui.component.{Container, ScrollContainer, TextView}
import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.render.Platform
import everyui.ui
import reactify.*

/** A finger's swipe on a phone: a page of thirty cards a tap is heard on, with a rail of ten cards across it and a
  * grip that drags itself (`touchDrags`), for a swipe to scroll however it starts, on a card or between them, a tap
  * still to land, and a drag on the grip to be the grip's alone. Each tap, each drag of the grip, and where each
  * scroll comes to rest, are logged (adb logcat on Android):
  *
  *   [ui] swipe page at 812
  *   [ui] swipe rail at 340
  *   [ui] swipe tapped card 4
  *   [ui] swipe grip heard 24 moves
  */
object SwipeDemo {
  def run(): Unit = {
    val page = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
      content.layout @= Some(VerticalLayout(12.0))
    }
    def card(name: String, width: Double, fill: Color): Container = new Container {
      size.width @= width
      size.height @= 80.0
      backgroundColor @= fill
      children += TextView(name, 16.0, Color.White)
      event.click.attach(_ => Platform.log("swipe tapped " + name))
    }
    val rail = new ScrollContainer(horizontal = true) {
      size.width := ui.size.width()
      size.height @= 80.0
      content.layout @= Some(HorizontalLayout(12.0))
    }
    rail.content.children ++= (0 until 10).map(i => card("rail card " + i, 160.0, Color.fromHex("2e7d32")))
    // one that drags itself (a grip, a handle): a finger dragged on it is its own, and the page stays put
    val grip = card("grip", 300.0, Color.fromHex("ef6c00"))
    grip.touchDrags @= true
    var moves = 0
    grip.event.pointer.move.attach(_ => moves += 1)
    grip.event.pointer.up.attach { _ =>
      Platform.log("swipe grip heard " + moves.toString + " moves")
      moves = 0
    }
    page.content.children ++= (0 until 30).map { i =>
      if (i == 3) rail
      else if (i == 5) grip
      else card("card " + i, 300.0, if (i % 2 == 0) Color.fromHex("1565c0") else Color.fromHex("6a1b9a"))
    }
    // where each comes to rest, a beat after it last moved
    def logged(name: String, s: ScrollContainer): Unit = {
      var moves = 0
      s.offset.attach { at =>
        moves += 1
        val mine = moves
        Platform.after(0.3, () => if (moves == mine) Platform.log("swipe " + name + " at " + math.round(at).toString))
      }
    }
    logged("page", page)
    logged("rail", rail)
    ui.children += page
  }
}
