package everyui.app.screen

import everyui.async.Task
import everyui.component.TextView
import everyui.focus.Focus
import everyui.keymap.Keymap
import everyui.net.*
import everyui.task.*
import everyui.util.{Clock, Format}
import everyui.{Color, FxSpec, ui}
import reactify.*

import scala.concurrent.duration.*

/** The screen manager's frame loop on JavaFX's own pulses: a still screen runs no frames at all, and each thing that
  * needs frames has them, the app's `frame` hook with them, until it no longer does. */
class FrameLoopSpec extends FxSpec {
  private object Manager extends ScreenManager {
    @volatile var frames = 0
    override protected def frame(ms: Double, updateMs: Double): Unit = frames += 1
  }

  private class Still extends UIScreen {
    override def manager: ScreenManager = Manager
    override def path: URLPath = path"/still"
    /** Work of its own in `update`, every frame, while this is set. */
    val counting: Var[Boolean] = Var(false)
    var updates = 0
    lazy val words: TextView = {
      val t = TextView("Still")
      t.focusable @= true
      t
    }

    override def needsFrames: Boolean = super.needsFrames || counting()
    override def update(delta: Double): Unit = {
      super.update(delta)
      if (counting()) updates += 1
    }
    override def defaultFocus(): TextView = words
    override def createUI(): Task[Unit] = Task(container.children += words)
  }

  private lazy val still = new Still

  /** How many frames run in `ms`. */
  private def framesIn(ms: Long): Int = {
    val before = Manager.frames
    Thread.sleep(ms)
    Manager.frames - before
  }

  /** Wait until no frame has run for half a second. */
  private def settled(): Unit = {
    val until = System.currentTimeMillis() + 5000L
    while (framesIn(500L) > 0 && System.currentTimeMillis() < until) ()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      ui.children.clear()
      Focus.focused @= None
      Manager.run(still)
    }
    eventually(Manager.active().nonEmpty) shouldBe true
  }

  override def afterAll(): Unit = {
    fx {
      // the loop parked, and the app taken down, for the specs after
      Frames.drive(_ => (), () => false, () => (), _ => ())
      ui.children.clear()
      Focus.focused @= None
      Focus.home @= (() => None)
      Focus.page @= (() => None)
      Keymap.shown @= (() => None)
      ScreenManager.running = None
    }
    super.afterAll()
  }

  "A screen manager's frames" should {
    "stop while nothing changes or moves" in {
      settled()
      framesIn(600L) shouldBe 0
      fx(Frames.running) shouldBe false
    }

    "run for a change to what is shown, and stop after" in {
      settled()
      fx(still.words.color @= Color.Red)
      framesIn(300L) should (be >= 1 and be <= 4)
      framesIn(400L) shouldBe 0
    }

    "run through an animation, and stop when it ends" in {
      settled()
      fx(still.words.opacity.to(0.5).in(400.millis).start())
      Thread.sleep(700L)
      fx(still.words.opacity()) shouldBe 0.5
      settled()
      fx(Frames.running) shouldBe false
    }

    "run for a screen that says it needs them, until it no longer does" in {
      settled()
      fx {
        still.counting @= true
        Frames.request()
      }
      framesIn(500L) should be > 10
      fx(still.updates) should be > 10
      fx(still.counting @= false)
      settled()
      framesIn(400L) shouldBe 0
    }

    "run once or so a second for words that follow the clock, and stop once none does" in {
      settled()
      val started = Clock.real()
      val ticking = fx {
        val t = TextView.bound(Format.elapsed(started, None))
        ui.children += t
        t
      }
      Thread.sleep(300L)
      val before = fx(ticking.content())
      val ran = framesIn(3000L)
      // the clock's frame and the two its words changing take, each second
      ran should (be >= 3 and be <= 15)
      fx(ticking.content()) should not be before
      fx {
        ui.children -= ticking
        ticking.dispose()
      }
      fx(Clock.watched) shouldBe false
      settled()
      framesIn(1500L) shouldBe 0
    }

    "run every frame while held" in {
      settled()
      val hold = fx(Frames.hold())
      framesIn(500L) should be > 20
      fx(hold.release())
      settled()
      framesIn(400L) shouldBe 0
    }
  }
}
