package everyui.event

import everyui.component.{Button, Container, ScrollContainer}
import everyui.focus.Focus
import everyui.layout.VerticalLayout
import everyui.list.VirtualList
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}
import reactify.*

/** A press that scrolled something is a scroll, not a click: a finger or a mouse dragged over a list's rows or a
  * scroll's buttons, and lifted, clicks nothing there, as a phone's own lists and a browser's pages have it, while a
  * tap, one that wobbled a pixel or two included, still clicks. A finger (a touch screen's, made into the mouse) that
  * went further than a tap may clicks nothing even where nothing scrolled; a mouse dragged where nothing scrolls still
  * clicks, as a desktop's does. Each drag ends as the platform ends one, with its release and then its click, all in
  * one turn of the FX thread. */
class ScrollPressSpec extends FxSpec {
  // the clock a finger's moves are timed by, moved on by each move: a drag stops before it lifts, and glides no further
  private var now = 0.0

  // on the FX thread, from it or from the spec's
  private def onFx(f: => Unit): Unit = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  // `still` is JavaFX's own word on whether the pointer stayed put since the press (`isStillSincePress`)
  private def mouse(on: Container, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double, finger: Boolean,
                    still: Boolean = true): Unit = onFx {
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false,
      false, false, true, false, false, finger, false, still, null))
  }

  // pressed at `x`, `y`, moved `by` down in eight moves, released and clicked where it ends
  private def drag(on: Container, x: Double, y: Double, by: Double, finger: Boolean, still: Boolean = true): Unit =
    onFx {
      mouse(on, MouseEvent.MOUSE_PRESSED, x, y, finger)
      (1 to 8).foreach { i =>
        now += 16.0
        mouse(on, MouseEvent.MOUSE_DRAGGED, x, y + by * i / 8, finger)
      }
      now += Fling.stopped + 10.0
      mouse(on, MouseEvent.MOUSE_RELEASED, x, y + by, finger)
      mouse(on, MouseEvent.MOUSE_CLICKED, x, y + by, finger, still)
    }

  // pressed, wobbled `wobble` down, and released and clicked where it went down
  private def tap(on: Container, x: Double, y: Double, finger: Boolean, wobble: Double = 0.0): Unit = onFx {
    mouse(on, MouseEvent.MOUSE_PRESSED, x, y, finger)
    if (wobble != 0.0) mouse(on, MouseEvent.MOUSE_DRAGGED, x, y + wobble, finger)
    mouse(on, MouseEvent.MOUSE_RELEASED, x, y, finger)
    mouse(on, MouseEvent.MOUSE_CLICKED, x, y, finger)
  }

  private def onScreen[T](part: Container)(test: => T): T = {
    fx {
      Flick.ticks = () => now
      ui.children.clear()
      Focus.focused @= None
      ui.children += part
    }
    try test finally fx {
      Flick.ticks = () => everyui.render.Platform.ticks()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  // a picker's list: 200 rows 28 high, 400 shown, each click activating its row (`clickActivates`)
  private def rows(): (VirtualList[String], () => List[String], () => Int) = fx {
    val l = VirtualList.text[String](Var((0 until 200).map("r" + _).toVector), s => s, s => s)
    l.size.width @= 300.0
    l.size.height @= 400.0
    l.clickActivates @= Some(true)
    var activated: List[String] = Nil
    var clicks = 0
    l.activated.attach(s => activated = activated :+ s)
    l.event.click.attach(_ => clicks += 1)
    (l, () => fx(activated), () => fx(clicks))
  }

  // a scroll 400 high of 40 buttons 48 high, each counting its presses
  private def buttons(count: Int = 40): (ScrollContainer, Vector[Button], () => Vector[Int]) = fx {
    val s = new ScrollContainer {
      size.width @= 300.0
      size.height @= 400.0
      content.layout @= Some(VerticalLayout(8.0))
    }
    val pressed = Array.fill(count)(0)
    val bs = (0 until count).toVector.map { i =>
      val b = new Button("Button " + i.toString, () => pressed(i) += 1)
      b.size.width @= 280.0
      b.size.height @= 40.0
      b
    }
    s.content.children ++= bs
    (s, bs, () => fx(pressed.toVector))
  }

  // the middle of `c` on the screen
  private def middle(c: Container): (Double, Double) =
    fx((c.absoluteLeft + c.size.width() / 2.0, c.absoluteTop + c.size.height() / 2.0))

  "A press on a list's rows" should {
    "be a scroll where a finger dragged them, activating and clicking nothing as it lifts" in {
      val (l, activated, clicks) = rows()
      onScreen(l) {
        drag(l, 100.0, 300.0, -150.0, finger = true)
        fx(l.offset()) should be > 100.0
        activated() shouldBe Nil
        clicks() shouldBe 0
      }
    }
    "still activate the row a finger taps, and one it wobbled on a little" in {
      val (l, activated, clicks) = rows()
      onScreen(l) {
        drag(l, 100.0, 300.0, -150.0, finger = true)
        val at = fx(l.offset())
        tap(l, 100.0, 3 * 28.0 + 10.0, finger = true)
        tap(l, 100.0, 5 * 28.0 + 10.0, finger = true, wobble = 3.0)
        val first = ((at + 3 * 28.0 + 10.0) / 28.0).toInt
        val second = ((fx(l.offset()) + 5 * 28.0 + 10.0) / 28.0).toInt
        activated() shouldBe List("r" + first, "r" + second)
        clicks() shouldBe 2
      }
    }
    "click nothing where a finger went further than a tap may, though nothing scrolled" in {
      val (l, activated, clicks) = rows()
      onScreen(l) {
        // the release made of the touch says the finger strayed (JavaFX's stillSincePress), across the rows
        fx {
          mouse(l, MouseEvent.MOUSE_PRESSED, 100.0, 100.0, finger = true)
          mouse(l, MouseEvent.MOUSE_RELEASED, 100.0, 100.0, finger = true)
          mouse(l, MouseEvent.MOUSE_CLICKED, 100.0, 100.0, finger = true, still = false)
        }
        activated() shouldBe Nil
        clicks() shouldBe 0
        tap(l, 100.0, 100.0, finger = true)
        activated() shouldBe List("r3")
      }
    }
  }

  "A press on a scroll's buttons" should {
    "be a scroll where a finger dragged them, pressing none" in {
      val (s, bs, pressed) = buttons()
      onScreen(s) {
        val (x, y) = middle(bs(6))
        drag(bs(6), x, y, -160.0, finger = true)
        fx(s.offset()) shouldBe 160.0 +- 0.001
        pressed().sum shouldBe 0
      }
    }
    "be a scroll where the mouse dragged them, pressing none" in {
      val (s, bs, pressed) = buttons()
      onScreen(s) {
        val (x, y) = middle(bs(6))
        drag(bs(6), x, y, -160.0, finger = false)
        fx(s.offset()) shouldBe 160.0 +- 0.001
        pressed().sum shouldBe 0
      }
    }
    "still press the one tapped or clicked, and one a finger wobbled on a little" in {
      val (s, bs, pressed) = buttons()
      onScreen(s) {
        val (x, y) = middle(bs(6))
        drag(bs(6), x, y, -160.0, finger = true)
        val (x2, y2) = middle(bs(5))
        tap(bs(5), x2, y2, finger = true)
        tap(bs(5), x2, y2, finger = false)
        tap(bs(5), x2, y2, finger = true, wobble = 4.0)
        pressed()(5) shouldBe 3
        pressed().sum shouldBe 3
        fx(s.offset()) shouldBe 160.0 +- 4.001
      }
    }
    "still press one the mouse dragged over where nothing scrolls, as a desktop's does" in {
      val (s, bs, pressed) = buttons(count = 3)
      onScreen(s) {
        val (x, y) = middle(bs(1))
        drag(bs(1), x, y - 12.0, 24.0, finger = false)
        fx(s.offset()) shouldBe 0.0
        pressed()(1) shouldBe 1
      }
    }
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    super.afterAll()
  }
}
