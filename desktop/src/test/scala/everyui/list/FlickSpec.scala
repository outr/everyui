package everyui.list

import everyui.component.{Container, ScrollContainer}
import everyui.event.{Fling, Flick}
import everyui.focus.Focus
import everyui.perf.Quality
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}
import reactify.*

/** A finger on a list or a scroll, decided by each press, not the device: a touch drags the content one for one and a
  * flick glides on once it lifts, on a desktop with a touch screen as on a phone, while the mouse there does what a
  * mouse does. A finger stopped before it lifted does not glide, one put down on a glide stops it and is no tap, and a
  * tap still picks a row. The finger's moves are timed on `Flick.ticks`, and a glide goes all the way at once (as at
  * Minimal), so the distances are exact. */
class FlickSpec extends FxSpec {
  // the clock the finger's moves are timed by, moved on only where a case says
  private var now = 0.0

  // on the FX thread, from it or from the spec's
  private def onFx(f: => Unit): Unit = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def mouse(on: Container, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double,
                    finger: Boolean): Unit = onFx {
    // a touch screen's finger comes to JavaFX as the mouse, made from the touch (synthesized)
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false,
      false, false, true, false, false, finger, false, kind == MouseEvent.MOUSE_CLICKED, null))
  }

  // pressed at `y`, moved `by` in `steps` moves 16 ms apart, then `still` ms before it lifts, if it does: in one turn
  // of the FX thread, as quickly as a flick (a row held for half a second is dragged out instead)
  private def drag(on: Container, x: Double, y: Double, by: Double, steps: Int, finger: Boolean,
                   still: Double = 0.0, lift: Boolean = true): Unit = onFx {
    mouse(on, MouseEvent.MOUSE_PRESSED, x, y, finger)
    (1 to steps).foreach { i =>
      now += 16.0
      mouse(on, MouseEvent.MOUSE_DRAGGED, x, y + by * i / steps, finger)
    }
    now += still
    if (lift) mouse(on, MouseEvent.MOUSE_RELEASED, x, y + by, finger)
  }

  private def tap(on: Container, x: Double, y: Double): Unit = onFx {
    mouse(on, MouseEvent.MOUSE_PRESSED, x, y, finger = true)
    now += 60.0
    mouse(on, MouseEvent.MOUSE_RELEASED, x, y, finger = true)
    mouse(on, MouseEvent.MOUSE_CLICKED, x, y, finger = true)
  }

  private def onScreen[T](part: Container, glides: Boolean = false)(test: => T): T = {
    fx {
      Flick.ticks = () => now
      Quality.glides = glides
      ui.children.clear()
      ui.children += part
    }
    try test finally fx {
      Flick.ticks = () => everyui.render.Platform.ticks()
      Quality.glides = true
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def list(): VirtualList[String] = fx {
    val l = VirtualList.text[String](Var((0 until 500).map("r" + _).toVector), s => s, s => s)
    l.size.height @= 400.0
    l
  }

  // a flick of 240 px in 128 ms: 1.875 px a millisecond as it lifts
  private val flicked = 240.0 + Fling.distance(240.0 / 128.0)

  "A finger on a list" should {
    "drag the rows one for one, and glide on once it lifts" in {
      val l = list()
      onScreen(l) {
        drag(l, 100.0, 350.0, -240.0, 8, finger = true, lift = false)
        fx(l.offset()) shouldBe 240.0 +- 0.001
        mouse(l, MouseEvent.MOUSE_RELEASED, 100.0, 110.0, finger = true)
        fx(l.offset()) shouldBe flicked +- 1.0
      }
    }
    "glide back up the other way" in {
      val l = list()
      onScreen(l) {
        fx(l.scrollTop(3000.0))
        drag(l, 100.0, 50.0, 240.0, 8, finger = true)
        fx(l.offset()) shouldBe (3000.0 - flicked) +- 1.0
      }
    }
    "not glide where it stopped before it lifted" in {
      val l = list()
      onScreen(l) {
        drag(l, 100.0, 350.0, -240.0, 8, finger = true, still = Fling.stopped + 10.0)
        fx(l.offset()) shouldBe 240.0 +- 0.001
      }
    }
    "be left to the mouse where a mouse drags, on the same desktop" in {
      val l = list()
      onScreen(l) {
        drag(l, 100.0, 350.0, -240.0, 8, finger = false)
        fx(l.offset()) shouldBe 0.0
        fx(l.gliding) shouldBe false
      }
    }
    "be stopped by a finger put down on its glide, which picks no row" in {
      val l = list()
      var activated: List[String] = Nil
      fx {
        l.clickActivates @= true
        l.activated.attach(s => activated = activated :+ s)
      }
      onScreen(l, glides = true) {
        // the flick and the finger put down on it in the same turn, before a frame of the glide
        fx {
          drag(l, 100.0, 350.0, -240.0, 8, finger = true)
          l.gliding shouldBe true
          tap(l, 100.0, 200.0)
          l.gliding shouldBe false
        }
        val at = fx(l.offset())
        at shouldBe 240.0 +- 0.001
        activated shouldBe Nil
        // and a tap after it is one
        tap(l, 100.0, 3 * 28.0 + 10.0)
        fx(l.offset()) shouldBe at
        activated shouldBe List("r" + ((at + 3 * 28.0 + 10.0) / 28.0).toInt)
      }
    }
    "be stopped by a press of the mouse, which still picks its row" in {
      val l = list()
      var activated: List[String] = Nil
      fx {
        l.clickActivates @= true
        l.activated.attach(s => activated = activated :+ s)
      }
      onScreen(l, glides = true) {
        fx {
          drag(l, 100.0, 350.0, -240.0, 8, finger = true)
          mouse(l, MouseEvent.MOUSE_PRESSED, 100.0, 10.0, finger = false)
          mouse(l, MouseEvent.MOUSE_RELEASED, 100.0, 10.0, finger = false)
          mouse(l, MouseEvent.MOUSE_CLICKED, 100.0, 10.0, finger = false)
          l.gliding shouldBe false
        }
        activated.size shouldBe 1
      }
    }
    "tap a row without scrolling" in {
      val l = list()
      var activated: List[String] = Nil
      fx {
        l.clickActivates @= true
        l.activated.attach(s => activated = activated :+ s)
      }
      onScreen(l) {
        tap(l, 100.0, 3 * 28.0 + 10.0)
        fx(l.offset()) shouldBe 0.0
        activated shouldBe List("r3")
      }
    }
    "still drag a row held on it a moment, and scroll one moved at once" in {
      val l = list()
      fx {
        l.draggable @= true
        l.accepts @= (_ => true)
      }
      onScreen(l) {
        drag(l, 100.0, 350.0, -240.0, 8, finger = true)
        fx(DragDrop.dragging()) shouldBe None
        fx(l.offset()) shouldBe flicked +- 1.0
      }
    }
  }

  "A finger on a scroll" should {
    def scroll(): (ScrollContainer, Container) = fx {
      val s = new ScrollContainer
      s.size.width @= 300.0
      s.size.height @= 400.0
      val tall = new Container
      tall.size.width @= 300.0
      tall.size.height @= 5000.0
      s.content.children += tall
      (s, tall)
    }
    "drag it one for one and glide on once it lifts" in {
      val (s, tall) = scroll()
      onScreen(s) {
        drag(tall, 100.0, 350.0, -240.0, 8, finger = true, lift = false)
        fx(s.offset()) shouldBe 240.0 +- 0.001
        mouse(tall, MouseEvent.MOUSE_RELEASED, 100.0, 110.0, finger = true)
        fx(s.offset()) shouldBe flicked +- 1.0
      }
    }
    "be dragged by the mouse without a glide" in {
      val (s, tall) = scroll()
      onScreen(s) {
        drag(tall, 100.0, 350.0, -240.0, 8, finger = false)
        fx(s.offset()) shouldBe 240.0 +- 0.001
      }
    }
  }
}
