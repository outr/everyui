package everyui.theme

import everyui.component.{Button, Component, Container}
import everyui.event.{KeyEvent, Keys, PointerEvent}
import everyui.focus.Focus
import everyui.keymap.Keymap
import everyui.menu.{Menu, MenuItem}
import everyui.render.Host
import everyui.store.{Store, Stored}
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}
import reactify.*

/** The size the whole app is drawn at: kept in range, taken in steps and back to 1, the screen laid out over it and the
  * root drawn at it, a pointer's place read back over it, so a click is where it lands and a menu opens there; the
  * keys a browser zooms with; and kept on the device across a new binding. */
class UiScaleSpec extends FxSpec {
  // a space of this run's own: the store is the home folder's, shared by every JVM running these specs at once
  private val space = "everyui-uiscale-spec-" + java.util.UUID.randomUUID()

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children ++= parts
    }
    try test finally fx {
      UiScale.reset()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  // (on the FX thread) where `c` is drawn in the window: its box, from the root's corner
  private def box(c: Component): javafx.geometry.Bounds =
    c.node.fx.localToScene(new javafx.geometry.BoundingBox(0.0, 0.0, c.size.width(), c.size.height()))

  override def afterAll(): Unit = {
    fx(UiScale.reset())
    fx(())
    fx(Store.clear(space).start())
    new java.io.File(System.getProperty("user.home"), "." + Host.app.toLowerCase + "/store/" + space).delete()
    super.afterAll()
  }

  "The size the app is drawn at" should {
    "be kept between its least and most, NaN as 1" in {
      fx(UiScale.factor @= 5.0)
      fx((UiScale.factor(), UiScale())) shouldBe ((3.0, 3.0))
      fx(UiScale.factor @= 0.1)
      fx(UiScale()) shouldBe 0.5
      fx(UiScale.factor @= Double.NaN)
      fx(UiScale()) shouldBe 1.0
      fx(UiScale.factor @= 1.3)
      fx(UiScale()) shouldBe 1.3
      fx(UiScale.reset())
      fx(UiScale.factor()) shouldBe 1.0
    }
    "go in tenths from 0.8 to 2.0, and no further, and say it as a percentage" in {
      fx {
        UiScale.larger()
        UiScale.larger()
        UiScale.larger()
      }
      fx(UiScale()) shouldBe 1.3
      fx(UiScale.percent()) shouldBe "130%"
      fx((1 to 20).foreach(_ => UiScale.larger()))
      fx(UiScale()) shouldBe 2.0
      fx((1 to 30).foreach(_ => UiScale.smaller()))
      fx(UiScale()) shouldBe 0.8
      fx(UiScale.percent()) shouldBe "80%"
      // set past the steps, a step back comes into them, and a step on goes nowhere
      fx(UiScale.factor @= 2.5)
      fx(UiScale.larger())
      fx(UiScale()) shouldBe 2.5
      fx(UiScale.smaller())
      fx(UiScale()) shouldBe 2.0
      fx(UiScale.reset())
      fx(UiScale()) shouldBe 1.0
    }
    "lay the screen out over it and draw the root at it, live, a component following the screen reflowing" in {
      val wide = fx(new Container {
        size.width := ui.size.width() - 40.0
        size.height @= 30.0
      })
      onScreen(wide) {
        fx((ui.size.width(), ui.size.height())) shouldBe ((1920.0, 1080.0))
        fx(wide.size.width()) shouldBe 1880.0
        fx(UiScale.factor @= 1.5)
        fx((ui.size.width(), ui.size.height())) shouldBe ((1280.0, 720.0))
        fx(wide.size.width()) shouldBe 1240.0
        // drawn half as large again: its box in the window
        val drawn = fx(box(wide))
        drawn.getWidth shouldBe 1860.0 +- 0.01
        drawn.getHeight shouldBe 45.0 +- 0.01
        // the root laid out over it, so its edges are the window's
        fx((Host.root.getWidth, Host.root.getHeight)) shouldBe ((1280.0, 720.0))
        fx(UiScale.factor @= 0.8)
        fx((ui.size.width(), ui.size.height())) shouldBe ((2400.0, 1350.0))
        fx((Host.root.getWidth, Host.root.getHeight)) shouldBe ((2400.0, 1350.0))
        fx(everyui.render.Platform.pixelRatio()) shouldBe 0.8 +- 0.001
        fx(UiScale.reset())
        fx((ui.size.width(), Host.root.getWidth)) shouldBe ((1920.0, 1920.0))
        fx(box(wide).getWidth) shouldBe 1880.0 +- 0.01
      }
    }
    "read a pointer's place back over it, so a click is the component's it lands on and a menu opens where it was" in {
      var heard: List[PointerEvent] = Nil
      val button = fx(new Button("Open"))
      fx {
        button.position.left @= 200.0
        button.position.top @= 100.0
        button.event.click.attach(e => heard = heard :+ e)
      }
      onScreen(button) {
        fx(UiScale.factor @= 1.5)
        // where it is drawn in the window: half as far in again, and half as large
        val drawn = fx(box(button))
        drawn.getMinX shouldBe 300.0 +- 0.01
        drawn.getMinY shouldBe 150.0 +- 0.01
        // a click in the middle of where it is drawn, in the window's coordinates
        val (x, y) = (drawn.getCenterX, drawn.getCenterY)
        fx(javafx.event.Event.fireEvent(button.node.fx, new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
          MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, true, null)))
        heard.size shouldBe 1
        val e = heard.head
        e.x shouldBe (200.0 + fx(button.size.width()) / 2.0) +- 0.01
        e.y shouldBe (100.0 + fx(button.size.height()) / 2.0) +- 0.01
        // a menu at the click has its corner where the click is drawn
        val menu = fx(Menu.at(e.x, e.y, List(MenuItem("One"), MenuItem("Two"))))
        val corner = fx(menu.node.fx.localToScene(0.0, 0.0))
        corner.getX shouldBe x +- 0.01
        corner.getY shouldBe y +- 0.01
        fx(while (everyui.component.Modal.showing) everyui.component.Modal.close())
      }
    }
    "be turned up, down and back by Primary with =, + (Shift held or not), - and 0" in {
      val keys = new Keymap
      fx(UiScale.commands(keys))
      def press(name: String, shift: Boolean = false): Unit = fx(keys.find(KeyEvent(Keys.of(name), name, ctrl = true,
        shift = shift)).foreach(_.run()))
      press("=")
      fx(UiScale()) shouldBe 1.1
      press("=", shift = true)
      fx(UiScale()) shouldBe 1.2
      press("+", shift = true)
      fx(UiScale()) shouldBe 1.3
      press("+")
      fx(UiScale()) shouldBe 1.4
      press("-")
      press("-")
      fx(UiScale()) shouldBe 1.2
      press("0")
      fx(UiScale()) shouldBe 1.0
      // without the modifier they are only keys
      fx(keys.find(KeyEvent(Keys.of("="), "="))) shouldBe None
      fx(keys("ui.larger").map(_.label)) shouldBe Some("Larger")
    }
    "be kept on the device when the app asks, and taken from there by the next binding" in {
      fx(UiScale.factor @= 1.0)
      fx(Stored.put[Double](space, "scale", 1.7).start())
      val took = Var[Option[Option[Double]]](None)
      fx(UiScale.remembered(space).map(found => took @= Some(found)).start())
      eventually(took().nonEmpty) shouldBe true
      fx(took()) shouldBe Some(Some(1.7))
      fx(UiScale()) shouldBe 1.7
      fx(ui.size.width()) shouldBe (1920.0 / 1.7) +- 0.001
      // a change is kept, read back as it was written
      fx(UiScale.larger())
      fx(())
      val read = Var[Option[Double]](None)
      fx(Stored.get[Double](space, "scale").map(v => read @= v).start())
      eventually(read().nonEmpty) shouldBe true
      fx(read()) shouldBe Some(1.8)
      fx(UiScale.reset())
    }
  }
}
