package everyui.list

import everyui.async.Task
import everyui.component.{Component, Modal}
import everyui.event.Flick
import everyui.focus.Focus
import everyui.picker.FilterPicker
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}
import reactify.*

/** A finger's tap on a row of a scrolling list is a click though the finger wobbles as it presses, as a real one always
  * does: pressed on the row, moved a pixel or three (short of the slop a drag settles at), and lifted, it activates the
  * row of a list and chooses the row of a picker, the list moved by the wobble or not; one that went further than a tap
  * may is a scroll, and clicks nothing. Each press is fired at the row it lands on and climbs to the list, as JavaFX
  * sends a touch screen's finger, in one turn of the FX thread. */
class TapJitterSpec extends FxSpec {
  private var now = 0.0

  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double,
                    still: Boolean = true): Unit = onFx {
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false,
      false, false, true, false, false, true, false, still, null))
  }

  // a finger pressed on `on` at `x`, `y`, through each of `to` 8 ms apart, lifted where the last left it, and the
  // platform's click
  private def tap(on: Component, x: Double, y: Double, to: List[(Double, Double)]): Unit = onFx {
    mouse(on, MouseEvent.MOUSE_PRESSED, x, y)
    to.foreach { case (dx, dy) =>
      now += 8.0
      mouse(on, MouseEvent.MOUSE_DRAGGED, x + dx, y + dy)
    }
    now += 8.0
    val (dx, dy) = to.lastOption.getOrElse((0.0, 0.0))
    val still = to.forall { case (a, b) => math.abs(a) <= 3.0 && math.abs(b) <= 3.0 }
    mouse(on, MouseEvent.MOUSE_RELEASED, x + dx, y + dy, still)
    mouse(on, MouseEvent.MOUSE_CLICKED, x + dx, y + dy, still)
  }

  // the wobbles a finger makes pressing: none, a pixel, and up to three each way, down and up
  private val wobbles: List[List[(Double, Double)]] = List(Nil, List((1.0, 0.0)), List((1.0, 1.0), (2.0, 2.0), (3.0, 3.0)),
    List((0.0, -1.0), (-2.0, -2.0), (-1.0, -3.0)), List((0.0, 2.0), (1.0, 3.0), (0.0, 1.0)))

  private def onScreen[T](test: => T): T = {
    fx {
      Flick.ticks = () => now
      ui.children.clear()
    }
    try test finally fx {
      Flick.ticks = () => everyui.render.Platform.ticks()
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def rowOf[T](l: VirtualList[T], item: T): ListRow[T] = onFx(l.rows.find(_.item().contains(item)).get)
  private def middle(c: Component): (Double, Double) =
    onFx((c.absoluteLeft + c.size.width() / 3.0, c.absoluteTop + c.size.height() / 2.0))

  "A finger's tap with a wobble" should {
    "activate the row of a list it lands on" in onScreen {
      val activated = scala.collection.mutable.ListBuffer.empty[String]
      val l = fx {
        val l = VirtualList.text[String](Var((0 until 200).map("r" + _).toVector), s => s, s => s)
        l.size.width @= 300.0
        l.size.height @= 400.0
        l.clickActivates @= Some(true)
        l.activated.attach(s => activated += s)
        ui.children += l
        l
      }
      (1 to 3).foreach(_ => fx(l.refresh()))
      wobbles.zipWithIndex.foreach { case (w, i) =>
        val item = "r" + (2 + i * 2)
        val r = rowOf(l, item)
        val (x, y) = middle(r)
        tap(r, x, y, w)
        fx(activated.lastOption) shouldBe Some(item)
      }
      fx(activated.size) shouldBe wobbles.size
    }
    "choose the row of a picker it lands on" in onScreen {
      val names = (0 until 12).map("Workspace " + _).toVector
      wobbles.foreach { w =>
        var chosen: Option[String] = None
        val p = fx(new FilterPicker[String](q => Task.pure(names.filter(_.toLowerCase.contains(q.toLowerCase))), identity,
          identity, debounce = 0.0))
        fx(p.chosen.attach(s => chosen = Some(s)))
        fx(p.open())
        eventually(p.list.count == names.size) shouldBe true
        (1 to 3).foreach(_ => fx(p.list.refresh()))
        val r = rowOf(p.list, "Workspace 3")
        val (x, y) = middle(r)
        tap(r, x, y, w)
        chosen shouldBe Some("Workspace 3")
        fx(while (Modal.showing) Modal.close())
      }
    }
    "click nothing where it went further than a tap may" in onScreen {
      val activated = scala.collection.mutable.ListBuffer.empty[String]
      val l = fx {
        val l = VirtualList.text[String](Var((0 until 200).map("r" + _).toVector), s => s, s => s)
        l.size.width @= 300.0
        l.size.height @= 400.0
        l.clickActivates @= Some(true)
        l.activated.attach(s => activated += s)
        ui.children += l
        l
      }
      (1 to 3).foreach(_ => fx(l.refresh()))
      val r = rowOf(l, "r4")
      val (x, y) = middle(r)
      tap(r, x, y, List((0.0, -6.0), (0.0, -12.0), (0.0, -20.0)))
      fx(activated.toList) shouldBe Nil
    }
  }
}
