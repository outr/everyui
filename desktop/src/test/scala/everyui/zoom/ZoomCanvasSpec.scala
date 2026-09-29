package everyui.zoom

import everyui.component.{Component, Container}
import everyui.draw.{Drawing, Figure, Hit, Path}
import everyui.focus.Focus
import everyui.spatial.{Rect, Size}
import everyui.{Color, FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent, ScrollEvent, ZoomEvent}

/** The canvas's view and its conversions, what it makes (only what is in view, and wide enough), what is under a point
  * down to a drawing's figures, and the pointer, the wheel, a pinch and the keys, sent to the scene. */
class ZoomCanvasSpec extends FxSpec {
  private def canvas(items: Vector[ZoomItem], fit: Boolean = false): ZoomCanvas = fx {
    val c = new ZoomCanvas
    c.autoFit @= fit
    c.overscan @= 0.0
    c.position.left @= 10.0
    c.position.top @= 20.0
    c.size.width @= 400.0
    c.size.height @= 300.0
    c.items @= items
    c
  }

  private def box(): Component = new Container {
    backgroundColor @= Color.Gray
  }

  private def grid(across: Int, down: Int): Vector[ZoomItem] =
    (for (i <- 0 until across; j <- 0 until down)
      yield ZoomItem(i.toString + "," + j.toString, i * 20.0, j * 20.0, 10.0, 10.0, () => box())).toVector

  private def onScreen[T](c: Component)(test: => T): T = {
    fx(ui.children += c)
    try test finally fx {
      ui.children -= c
      Focus.focused @= None
    }
  }

  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double): Unit = fx {
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false, false,
      false, true, false, false, false, false, true, null))
  }

  private def wheel(on: Component, dx: Double, dy: Double, ctrl: Boolean): Unit = fx {
    javafx.event.Event.fireEvent(on.node.fx, new ScrollEvent(ScrollEvent.SCROLL, 0, 0, 0, 0, false, ctrl, false, false,
      false, false, dx, dy, dx, dy, ScrollEvent.HorizontalTextScrollUnits.NONE, 0, ScrollEvent.VerticalTextScrollUnits.NONE, 0,
      0, null))
  }

  private def press(code: KeyCode, text: String = ""): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, false, false, false, false))
  }

  "A zoom canvas" should {
    "convert between the screen and the world" in {
      val c = canvas(Vector.empty)
      fx(c.view @= ZoomView(100.0, 50.0, 2.0))
      fx(c.toWorld(0.0, 0.0)) shouldBe ((100.0, 50.0))
      fx(c.toWorld(20.0, 40.0)) shouldBe ((110.0, 70.0))
      fx(c.toScreen(110.0, 60.0)) shouldBe ((20.0, 20.0))
    }
    "zoom about a point, which stays where it is, within its scales" in {
      val c = canvas(Vector.empty)
      fx(c.zoomBy(3.0, 100.0, 80.0))
      val (x, y) = fx(c.toScreen(100.0, 80.0))
      x shouldBe 100.0 +- 1e-9
      y shouldBe 80.0 +- 1e-9
      fx(c.view().scale) shouldBe 3.0
      fx(c.zoomBy(1000.0, 0.0, 0.0))
      fx(c.view().scale) shouldBe fx(c.maxScale())
    }
    "make only what is in view, and put away what leaves it" in {
      val c = canvas(grid(100, 50))
      onScreen(c) {
        // 400 by 300 at a scale of 1: the 20-unit grid's 21 columns and 16 rows that reach into it
        fx(c.showing) shouldBe 21 * 16
        fx(c.made) shouldBe 21 * 16
        fx(c.keep @= 0)
        fx(c.panBy(1000.0, 500.0))
        fx(c.showing) shouldBe 21 * 16
        fx(c.made) shouldBe 21 * 16
        fx(c.componentAt("0,0")) shouldBe None
        fx(c.componentAt("50,25")).nonEmpty shouldBe true
      }
    }
    "place what scales in the world, and what does not at its size on the screen" in {
      val scaled = ZoomItem("a", 10.0, 10.0, 40.0, 20.0, () => box())
      val flat = ZoomItem("b", 100.0, 10.0, 40.0, 20.0, () => box(), scales = false)
      val designed = ZoomItem("c", 200.0, 10.0, 40.0, 20.0, () => box(), design = Some(Size(200.0, 100.0)))
      val c = canvas(Vector(scaled, flat, designed))
      onScreen(c) {
        fx(c.view @= ZoomView(0.0, 0.0, 2.0))
        val a = fx(c.componentAt("a").get)
        (fx(a.size.width()), fx(a.position.left())) shouldBe ((40.0, 10.0))
        val b = fx(c.componentAt("b").get)
        (fx(b.size.width()), fx(b.size.height()), fx(b.position.left())) shouldBe ((80.0, 40.0, 200.0))
        // laid out at 200 by 100 and drawn a fifth of that, from the box's corner
        val d = fx(c.componentAt("c").get)
        (fx(d.size.width()), fx(d.scale())) shouldBe ((200.0, 0.2))
        (fx(d.position.left()) + 100.0 - 0.2 * 100.0) shouldBe 200.0 +- 1e-9
        fx(c.hit(420.0, 30.0).map(h => (h.x, h.y, h.pixel))) shouldBe Some((50.0, 25.0, 2.5))
      }
    }
    "show children once their parent is wide enough, in its own 0 to 1" in {
      val kids = () => Vector(ZoomItem("x", 0.5, 0.5, 0.25, 0.25, () => box()))
      val parent = ZoomItem("p", 0.0, 0.0, 100.0, 100.0, () => box(), children = kids, childrenFrom = 300.0,
        until = Some(500.0))
      val c = canvas(Vector(parent))
      onScreen(c) {
        fx(c.showing) shouldBe 1
        fx(c.boxOf("p", "x")) shouldBe Some(Rect(50.0, 50.0, 25.0, 25.0))
        fx(c.view @= ZoomView(0.0, 0.0, 4.0))
        fx(c.showing) shouldBe 2
        fx(c.hit(55.0 * 4.0, 55.0 * 4.0).map(_.path)) shouldBe Some(List("p", "x"))
        fx(c.view @= ZoomView(0.0, 0.0, 6.0))
        fx(c.showing) shouldBe 1
        fx(c.hit(10.0, 10.0)) shouldBe None
      }
    }
    "find a drawing's figure under a point, however far it is zoomed" in {
      val figures = List(Figure(Path.rect(0, 0, 50, 50), fill = Some(Color.Red)),
        Figure(Path.line(0, 80, 100, 80), stroke = Some(Color.Blue), width = 2.0))
      val drawing = ZoomItem("d", 0.0, 0.0, 100.0, 100.0, () => {
        val d = new Drawing
        d.figures @= figures
        d
      })
      val c = canvas(Vector(drawing))
      onScreen(c) {
        fx(c.hit(20.0, 20.0).flatMap(_.figure(figures))) shouldBe Some(0)
        fx(c.hit(60.0, 83.0).flatMap(_.figure(figures))) shouldBe Some(1)
        fx(c.hit(60.0, 90.0).flatMap(_.figure(figures))) shouldBe None
        // four pixels of the screen are two of the drawing's at twice the size
        fx(c.view @= ZoomView(0.0, 0.0, 2.0))
        fx(c.hit(120.0, 166.0).flatMap(_.figure(figures))) shouldBe Some(1)
        fx(c.hit(120.0, 170.0).flatMap(_.figure(figures))) shouldBe None
      }
      Hit.inside(Path.circle(0, 0, 10), 7, 7) shouldBe true
      Hit.inside(Path.circle(0, 0, 10), 8, 8) shouldBe false
      Hit.distance(Path.empty.moveTo(0, 0).cubicTo(0, 10, 10, 10, 10, 0), 5, 7.5) shouldBe 0.0 +- 0.1
    }
    "pan by a drag, and click only where it did not move" in {
      val c = canvas(Vector(ZoomItem("a", 0.0, 0.0, 100.0, 100.0, () => box())))
      var clicks: List[ZoomPoint] = Nil
      fx(c.clicked.attach(p => clicks = p :: clicks))
      onScreen(c) {
        mouse(c, MouseEvent.MOUSE_PRESSED, 60.0, 70.0)
        mouse(c, MouseEvent.MOUSE_DRAGGED, 110.0, 90.0)
        mouse(c, MouseEvent.MOUSE_RELEASED, 110.0, 90.0)
        mouse(c, MouseEvent.MOUSE_CLICKED, 110.0, 90.0)
        fx(c.view()) shouldBe ZoomView(-50.0, -20.0, 1.0)
        clicks shouldBe Nil
        mouse(c, MouseEvent.MOUSE_PRESSED, 70.0, 70.0)
        mouse(c, MouseEvent.MOUSE_RELEASED, 70.0, 70.0)
        mouse(c, MouseEvent.MOUSE_CLICKED, 70.0, 70.0)
        clicks.map(p => (p.x, p.y, p.hit.map(_.key))) shouldBe List((10.0, 30.0, Some("a")))
      }
    }
    "pan by the wheel, and zoom about the pointer with Ctrl" in {
      val c = canvas(Vector.empty)
      onScreen(c) {
        wheel(c, 0.0, -40.0, ctrl = false)
        fx(c.view()) shouldBe ZoomView(0.0, 40.0, 1.0)
        mouse(c, MouseEvent.MOUSE_MOVED, 210.0, 170.0)
        val under = fx(c.toWorld(200.0, 150.0))
        wheel(c, 0.0, 40.0, ctrl = true)
        fx(c.view().scale) shouldBe math.pow(2.0, 40.0 / 250.0) +- 1e-9
        val (x, y) = fx(c.toScreen(under._1, under._2))
        x shouldBe 200.0 +- 1e-9
        y shouldBe 150.0 +- 1e-9
      }
    }
    "zoom by a pinch about where it is" in {
      val c = canvas(Vector.empty)
      onScreen(c) {
        fx(javafx.event.Event.fireEvent(c.node.fx, new ZoomEvent(ZoomEvent.ZOOM, 110.0, 120.0, 110.0, 120.0, false, false,
          false, false, false, false, 2.0, 2.0, null)))
        fx(c.view().scale) shouldBe 2.0
        fx(c.toScreen(100.0, 100.0)) shouldBe ((100.0, 100.0))
        fx(javafx.event.Event.fireEvent(c.node.fx, new ZoomEvent(ZoomEvent.ZOOM_FINISHED, 110.0, 120.0, 110.0, 120.0, false,
          false, false, false, false, false, 1.0, 2.0, null)))
      }
    }
    "pan by the arrows while there is more that way, zoom by + and -, and fit on 0" in {
      val c = canvas(Vector(ZoomItem("a", 0.0, 0.0, 800.0, 300.0, () => box())))
      onScreen(c) {
        fx(c.focus())
        press(KeyCode.RIGHT)
        fx(c.view().x) shouldBe 50.0
        // nothing more above: the arrow is the focus layer's
        fx(c.keepsEvent(everyui.event.KeyEvent(everyui.event.Key.ArrowUp, "ArrowUp"))) shouldBe false
        press(KeyCode.PLUS, "+")
        fx(c.view().scale) shouldBe 1.25
        press(KeyCode.MINUS, "-")
        fx(c.view().scale) shouldBe 1.0 +- 1e-9
        fx(c.glide @= 0.0)
        press(KeyCode.DIGIT0, "0")
        fx(c.view().scale) shouldBe 0.44 +- 1e-9
      }
    }
    "stay quick with thousands of items, making only what shows" in {
      // 5,000 at the top, each with four children shown past 200 pixels: 25,000 in all
      val kids = () => (0 until 4).toVector.map(k => ZoomItem(k.toString, (k % 2) * 0.5, (k / 2) * 0.5, 0.45, 0.45,
        () => box(), scales = false))
      val items = (for (i <- 0 until 100; j <- 0 until 50)
        yield ZoomItem(i.toString + "," + j.toString, i * 20.0, j * 20.0, 16.0, 16.0, () => box(), children = kids,
          childrenFrom = 200.0)).toVector
      val c = canvas(items)
      onScreen(c) {
        def timed(label: String, steps: Int)(step: Int => Unit): Unit = {
          val each = fx {
            // once to warm up, and again to time
            (0 until steps).foreach(i => step(i))
            (0 until steps).foreach(i => step(steps - 1 - i))
            val start = System.nanoTime()
            (0 until steps).foreach(i => step(i))
            (System.nanoTime() - start) / 1e6 / steps
          }
          info(f"$label: $each%.2f ms a step, ${fx(c.showing)} shown, ${fx(c.made)} made")
        }
        // a pan back and forth, a step a frame
        timed("panned at 1x (about 336 in view)", 100)(i => c.view @= ZoomView((i % 40) * 15.0, (i % 40) * 7.0, 1.0))
        timed("panned with all 5,000 in view", 60)(i => c.view @= ZoomView(-100.0 + (i % 20) * 2.0, -100.0, 0.09))
        fx(c.showing) shouldBe 5000
        timed("panned at 14x, children shown", 100)(i => c.view @= ZoomView(100.0 + (i % 40), 100.0, 14.0))
        fx(c.showing) should be < 200
      }
    }
    "fit what it holds when it first can, and zoom to an item by its keys" in {
      val kids = () => Vector(ZoomItem("x", 0.5, 0.5, 0.5, 0.5, () => box()))
      val c = canvas(Vector(ZoomItem("p", 0.0, 0.0, 1000.0, 1000.0, () => box(), children = kids)), fit = true)
      onScreen(c) {
        fx(c.view().scale) shouldBe (300.0 - 48.0) / 1000.0 +- 1e-9
        fx(c.glide @= 0.0)
        fx(c.zoomToItem("p", "x"))
        fx(c.view().scale) shouldBe (300.0 - 48.0) / 500.0 +- 1e-9
        fx(c.toScreen(750.0, 750.0)) shouldBe ((200.0, 150.0))
      }
    }
  }

  "Paths" should {
    "be cut into dashes, and rounded at their corners" in {
      val dashes = Path.line(0, 0, 20, 0).dashed(4, 2)
      dashes.ops.map(_.kind).count(_ == "M") shouldBe 4
      dashes.ops.filter(_.kind == "L").map(_.values.head) shouldBe Vector(4.0, 10.0, 16.0, 20.0)
      Hit.bounds(Path.roundRect(0, 0, 40, 20, 6)) shouldBe Some(Rect(0, 0, 40, 20))
    }
  }
}
