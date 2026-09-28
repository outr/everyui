package everyui.app

import everyui.Color
import everyui.component.{Container, ScrollContainer}
import everyui.i18n.Direction
import everyui.layout.HorizontalLayout
import everyui.render.Platform
import everyui.ui
import reactify.*

/** A rail in a right-to-left language, as the web's own scroll holds it: ten cards 100 wide in a rail 300 wide, the
  * first red, the third green and the sixth blue. A page check (tools/rtl-scroll-check.js) clicks them: the green one
  * narrows the rail, the red one adds five more cards, and the blue one reveals itself at the rail's start, which logs
  * how far along it is (500):
  *
  *   [ui] rtlscroll revealed at 500
  */
object RtlScrollDemo {
  def run(): Unit = {
    Direction.set(true)
    val rail = new ScrollContainer(horizontal = true) {
      position.left @= 40.0
      position.top @= 40.0
      size.width @= 300.0
      size.height @= 50.0
      revealMargin @= 0.0
      revealAlign @= Some(0.0)
      content.layout @= Some(HorizontalLayout(0.0))
    }
    def card(fill: Color): Container = new Container {
      size.width @= 100.0
      size.height @= 50.0
      backgroundColor @= fill
    }
    val cards = Vector.tabulate(10) { i =>
      card(if (i == 0) Color.Red else if (i == 2) Color.Green else if (i == 5) Color.Blue else Color.Gray)
    }
    cards(2).event.click.attach(_ => rail.size.width @= 200.0)
    cards.head.event.click.attach(_ => rail.content.children ++= Vector.fill(5)(card(Color.Gray)))
    cards(5).event.click.attach { _ =>
      rail.reveal(cards(5))
      Platform.log("rtlscroll revealed at " + rail.node.scrolled(true).round.toString)
    }
    rail.content.children := cards
    ui.children += rail
  }
}
