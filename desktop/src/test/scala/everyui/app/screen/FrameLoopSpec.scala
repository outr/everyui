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

  /** Wait until no frame has run for half a second: whether that came before a bound that only a loop that never
    * stops reaches, however busy the machine. */
  private def settled(): Boolean = {
    val until = System.currentTimeMillis() + 20000L
    var still = framesIn(500L) == 0
    while (!still && System.currentTimeMillis() < until) still = framesIn(500L) == 0
    still
  }

  /** Wait until more than `count` frames have run since `from` (a count of `Manager.frames`): whether they did, before
    * a bound that only frames that never come reach. Counted rather than timed, as a busy machine runs fewer frames a
    * second. */
  private def framesPast(from: Int, count: Int): Boolean = {
    val until = System.currentTimeMillis() + 20000L
    while (Manager.frames - from <= count && System.currentTimeMillis() < until) Thread.sleep(10L)
    Manager.frames - from > count
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
      settled() shouldBe true
      framesIn(600L) shouldBe 0
      fx(Frames.running) shouldBe false
    }

    "run for a change to what is shown, and stop after" in {
      settled() shouldBe true
      val before = Manager.frames
      fx(still.words.color @= Color.Red)
      // every frame the change runs, counted until they stop: the one that draws it, the one after, and no more than
      // a couple the change itself has after it
      framesPast(before, 0) shouldBe true
      settled() shouldBe true
      Manager.frames - before should (be >= 1 and be <= 4)
    }

    "run through an animation, and stop when it ends" in {
      settled() shouldBe true
      fx(still.words.opacity.to(0.5).in(400.millis).start())
      eventually(still.words.opacity() == 0.5) shouldBe true
      settled() shouldBe true
      fx(Frames.running) shouldBe false
    }

    "run for a screen that says it needs them, until it no longer does" in {
      settled() shouldBe true
      val before = Manager.frames
      fx {
        still.counting @= true
        Frames.request()
      }
      // far more than the one asked for
      framesPast(before, 10) shouldBe true
      fx(still.updates) should be > 10
      fx(still.counting @= false)
      settled() shouldBe true
      framesIn(400L) shouldBe 0
    }

    "run once or so a second for words that follow the clock, and stop once none does" in {
      settled() shouldBe true
      val started = Clock.real()
      val ticking = fx {
        val t = TextView.bound(Format.elapsed(started, None))
        ui.children += t
        t
      }
      // the frames from the words' first change to their fourth, counted rather than timed: the clock's frame and the
      // two its words changing take, each time it moves on, whenever the machine gets to them
      var shown = fx(ticking.content())
      eventually(ticking.content() != shown) shouldBe true
      val (from, at) = (Manager.frames, System.nanoTime())
      (1 to 3).foreach { _ =>
        shown = fx(ticking.content())
        eventually(ticking.content() != shown) shouldBe true
      }
      val ran = Manager.frames - from
      ran should (be >= 3 and be <= 15)
      // and a second apart, give or take how late each change is seen
      (System.nanoTime() - at) / 1e9 should be >= 2.0
      fx {
        ui.children -= ticking
        ticking.dispose()
      }
      fx(Clock.watched) shouldBe false
      settled() shouldBe true
      framesIn(1500L) shouldBe 0
    }

    "run every frame while held" in {
      settled() shouldBe true
      val before = Manager.frames
      val hold = fx(Frames.hold())
      framesPast(before, 20) shouldBe true
      fx(hold.release())
      settled() shouldBe true
      framesIn(400L) shouldBe 0
    }
  }
}
