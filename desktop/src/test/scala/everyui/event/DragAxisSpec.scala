package everyui.event

import everyui.component.{Button, Component, Container, ScrollContainer}
import everyui.focus.Focus
import everyui.layout.VerticalLayout
import everyui.perf.Quality
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}

/** A drag's way, settled once (`DragAxis`): by the first move further than a tap may, along where it went mostly
  * along, and never again in that press. On a page of a rail and a button (a scroll down holding a scroll across), a
  * finger's drag down that starts on the rail scrolls the page alone, one across it the rail alone, one that turns
  * after it settled stays settled, the page follows a finger from its first move with no jump where the drag settles
  * (a page or a list), a flick down glides the page and not the rail, and a drag down that starts on the
  * button scrolls the page and clicks nothing, while a tap on it still clicks. Each press is fired at what it lands on
  * and climbs to the page, as JavaFX sends a touch screen's finger, in one turn of the FX thread. */
class DragAxisSpec extends FxSpec {
  private var now = 0.0

  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double,
                    still: Boolean = true): Unit = onFx {
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false,
      false, false, true, false, false, true, false, still, null))
  }

  // a finger pressed at `x`, `y` on `on`, through each of `to` 16 ms apart, then `still` ms before it lifts there
  private def drag(on: Component, x: Double, y: Double, to: List[(Double, Double)], still: Double): Unit = onFx {
    mouse(on, MouseEvent.MOUSE_PRESSED, x, y)
    to.foreach { case (dx, dy) =>
      now += 16.0
      mouse(on, MouseEvent.MOUSE_DRAGGED, x + dx, y + dy, still = false)
    }
    now += still
    val (dx, dy) = to.last
    mouse(on, MouseEvent.MOUSE_RELEASED, x + dx, y + dy, still = false)
    mouse(on, MouseEvent.MOUSE_CLICKED, x + dx, y + dy, still = false)
  }

  // eight even steps to `dx`, `dy`
  private def straight(dx: Double, dy: Double): List[(Double, Double)] = (1 to 8).toList.map(i => (dx * i / 8, dy * i / 8))

  // a page 400 high: a rail 100 high of a strip 2000 wide, a button, and room below to scroll down to
  private class Page {
    var clicks = 0
    val rail: ScrollContainer = new ScrollContainer(horizontal = true) {
      size.width @= 300.0
      size.height @= 100.0
    }
    val strip: Container = new Container {
      size.width @= 2000.0
      size.height @= 100.0
    }
    rail.content.children += strip
    val button: Button = new Button("Press", () => clicks += 1)
    button.size.width @= 280.0
    button.size.height @= 48.0
    val filler: Container = new Container {
      size.width @= 280.0
      size.height @= 2000.0
    }
    val page: ScrollContainer = new ScrollContainer {
      size.width @= 300.0
      size.height @= 400.0
      content.layout @= Some(VerticalLayout(8.0))
    }
    page.content.children ++= List(rail, button, filler)
    def middle(c: Component): (Double, Double) = (c.absoluteLeft + 150.0, c.absoluteTop + c.size.height() / 2.0)
  }

  private def onScreen[T](test: Page => T): T = {
    val p = fx {
      Flick.ticks = () => now
      Quality.glides = false
      ui.children.clear()
      val p = new Page
      ui.children += p.page
      p
    }
    try test(p) finally fx {
      Flick.ticks = () => everyui.render.Platform.ticks()
      Quality.glides = true
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A drag's way" should {
    "be open until it goes further than a tap may, then along where it went mostly along" in {
      DragAxis.settle(5.0, 8.0, vertical = true, 8.0) shouldBe 0
      DragAxis.settle(3.0, 9.0, vertical = true, 8.0) shouldBe 1
      DragAxis.settle(9.0, 3.0, vertical = true, 8.0) shouldBe -1
      DragAxis.settle(9.0, 3.0, vertical = false, 8.0) shouldBe 1
      DragAxis.settle(-3.0, -9.0, vertical = false, 8.0) shouldBe -1
      // just as far each way is neither scroll's, so a rail and the page it is in never both take it
      DragAxis.settle(9.0, 9.0, vertical = true, 8.0) shouldBe -1
      DragAxis.settle(9.0, 9.0, vertical = false, 8.0) shouldBe -1
    }
    "stay as it settled for the rest of the press" in {
      val a = new DragAxis(true)
      a.down(100.0, 100.0)
      a.move(103.0, 104.0)
      (a.along, a.across) shouldBe ((false, false))
      a.move(112.0, 103.0)
      a.across shouldBe true
      a.move(112.0, 400.0)
      (a.along, a.across) shouldBe ((false, true))
      a.down(0.0, 0.0)
      a.move(0.0, 20.0)
      a.along shouldBe true
    }
  }

  "A finger's drag on a scroll in a scroll" should {
    "scroll the page alone where it goes down from the rail" in onScreen { p =>
      val (x, y) = fx(p.middle(p.rail))
      drag(p.strip, x, y, straight(-15.0, -200.0), still = Fling.stopped + 10.0)
      fx(p.page.offset()) shouldBe 200.0 +- 0.001
      fx(p.rail.offset()) shouldBe 0.0
    }
    "scroll the rail alone where it goes across" in onScreen { p =>
      val (x, y) = fx(p.middle(p.rail))
      drag(p.strip, x, y, straight(-200.0, 15.0), still = Fling.stopped + 10.0)
      fx(p.rail.offset()) shouldBe 200.0 +- 0.001
      fx(p.page.offset()) shouldBe 0.0
    }
    "keep the way it settled on as it turns" in onScreen { p =>
      val (x, y) = fx(p.middle(p.rail))
      drag(p.strip, x, y, List((-20.0, 2.0), (-40.0, -60.0), (-60.0, -200.0)), still = Fling.stopped + 10.0)
      fx(p.rail.offset()) shouldBe 60.0 +- 0.001
      fx(p.page.offset()) shouldBe 0.0
    }
    "glide the page on, and never the rail, after a flick down from the rail" in onScreen { p =>
      val (x, y) = fx(p.middle(p.rail))
      drag(p.strip, x, y, straight(-10.0, -160.0), still = 0.0)
      fx(p.page.offset()) should be > 161.0
      fx(p.rail.offset()) shouldBe 0.0
    }
    "scroll the page, and click nothing, where it goes down from a button" in onScreen { p =>
      val (x, y) = fx(p.middle(p.button))
      drag(p.button, x, y, straight(-5.0, -200.0), still = Fling.stopped + 10.0)
      fx(p.page.offset()) shouldBe 200.0 +- 0.001
      fx(p.clicks) shouldBe 0
    }
    "still click a button on a tap" in onScreen { p =>
      val (x, y) = fx(p.middle(p.button))
      onFx {
        mouse(p.button, MouseEvent.MOUSE_PRESSED, x, y)
        mouse(p.button, MouseEvent.MOUSE_RELEASED, x, y)
        mouse(p.button, MouseEvent.MOUSE_CLICKED, x, y)
      }
      fx(p.clicks) shouldBe 1
      fx(p.page.offset()) shouldBe 0.0
    }
    "follow a finger from its first move, with no jump where the drag settles" in onScreen { p =>
      val (x, y) = fx(p.middle(p.button))
      // two pixels at a time, past the slop: the page under the finger at every move, never still and then caught up
      val seen = onFx {
        mouse(p.button, MouseEvent.MOUSE_PRESSED, x, y)
        val at = (1 to 10).toList.map { i =>
          now += 16.0
          mouse(p.button, MouseEvent.MOUSE_DRAGGED, x, y - 2.0 * i, still = false)
          p.page.offset()
        }
        mouse(p.button, MouseEvent.MOUSE_RELEASED, x, y - 20.0, still = false)
        at
      }
      seen shouldBe (1 to 10).toList.map(_ * 2.0)
    }
    "follow a finger on a list from its first move, with no jump where the drag settles" in {
      val l = fx {
        val l = everyui.list.VirtualList.text[String](reactify.Var((0 until 200).map("r" + _).toVector), s => s, s => s)
        l.size.width @= 300.0
        l.size.height @= 400.0
        l
      }
      fx {
        Flick.ticks = () => now
        ui.children.clear()
        ui.children += l
      }
      try {
        val seen = onFx {
          mouse(l, MouseEvent.MOUSE_PRESSED, 100.0, 300.0)
          val at = (1 to 10).toList.map { i =>
            now += 16.0
            mouse(l, MouseEvent.MOUSE_DRAGGED, 100.0, 300.0 - 2.0 * i, still = false)
            l.offset()
          }
          now += Fling.stopped + 10.0
          mouse(l, MouseEvent.MOUSE_RELEASED, 100.0, 280.0, still = false)
          at
        }
        seen shouldBe (1 to 10).toList.map(_ * 2.0)
      }
      finally fx {
        Flick.ticks = () => everyui.render.Platform.ticks()
        ui.children.clear()
      }
    }
  }
}
