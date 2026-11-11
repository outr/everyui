package everyui

import everyui.render.Host
import javafx.scene.Scene
import javafx.scene.layout.Pane
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{CountDownLatch, TimeUnit}

/** A spec run against the desktop backend's real nodes, on the JavaFX thread, in the one window every spec shares:
  * the focus layer listens on the first one made, whichever spec made it. */
trait FxSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  /** The window the focus layer listens on. */
  protected var window: Scene = null

  /** Whether the spec's focus moves as the keys move it (`Focus.visible`: drawn, put on a screen's `defaultFocus`, moved
    * on to a successor), rather than as a pointer leaves it, the default (Matt's ruling of 2026-10-21). */
  protected def keyed: Boolean = false

  /** `f` on the FX thread, waited for. What it posts for later (`Platform.later`) runs before the next `fx`. The wait
    * gives up after a minute, a bound for a thread that never comes back: the longest work a spec times
    * (ColumnScaleSpec's thousands of lines added one at a time) takes several seconds on a busy machine. */
  protected def fx[T](f: => T): T = {
    // a toolkit that never started (no display) holds runLater forever: fail instead, so the run goes on
    if (!FxSpec.started) fail("JavaFX did not start")
    var result: Option[Either[Throwable, T]] = None
    val done = new CountDownLatch(1)
    javafx.application.Platform.runLater(() => {
      result = Some(try Right(f) catch { case t: Throwable => Left(t) })
      done.countDown()
    })
    done.await(60, TimeUnit.SECONDS) shouldBe true
    result.get.fold(t => throw t, v => v)
  }

  private val threads = java.lang.management.ManagementFactory.getThreadMXBean

  /** `f` on the FX thread, and the time that thread spent on it (ms): what the work costs, without the waits for a core
    * that a clock on the wall counts in on a busy machine, nor the collections that waited for theirs. */
  protected def worked(f: => Unit): Double = fx {
    val start = threads.getCurrentThreadCpuTime
    f
    (threads.getCurrentThreadCpuTime - start) / 1e6
  }

  /** `f`, run on this thread and calling `fx` as often as it likes, and the time the FX thread spent meanwhile (ms):
    * what the turns cost, what each posts for later (a layout) among it, without the waits for the thread to come
    * round that a clock on the wall counts in on a busy machine. */
  protected def fxWorked(f: => Unit): Double = {
    val id = fx(Thread.currentThread().threadId())
    val start = threads.getThreadCpuTime(id)
    f
    fx(())
    (threads.getThreadCpuTime(id) - start) / 1e6
  }

  /** `long(i)` and `short(i)` for each of `steps`, in turn on the FX thread (which goes first changing at each), and
    * the time that thread spent on each in all (ms): work over more beside the same work over less, under whatever
    * load the machine is under at that moment, so that their ratio is the work's and not the load's. A busy machine
    * slows the thread's own time too, three and four times over (the JIT's compilers starved of a core, the code left
    * interpreted, the caches shared), so a bound on that time alone fails on a busy machine; side by side, both slow
    * alike. */
  protected def workedBeside(steps: Int)(long: Int => Unit, short: Int => Unit): (Double, Double) = fx {
    var inLong = 0L
    var inShort = 0L
    var i = 0
    while (i < steps) {
      val start = threads.getCurrentThreadCpuTime
      if (i % 2 == 0) long(i) else short(i)
      val between = threads.getCurrentThreadCpuTime
      if (i % 2 == 0) short(i) else long(i)
      val end = threads.getCurrentThreadCpuTime
      if (i % 2 == 0) {
        inLong += between - start
        inShort += end - between
      } else {
        inShort += between - start
        inLong += end - between
      }
      i += 1
    }
    (inLong / 1e6, inShort / 1e6)
  }

  /** `step(i)` for each of `steps` on the FX thread with `PerfCounters` counting, and for each count in `names`
    * ("fits", "measured", "placed") what all the steps added and the most one step added: the work, counted, which
    * no load changes. */
  protected def counted(steps: Int, names: String*)(step: Int => Unit): Map[String, (Int, Int)] = fx {
    val was = everyui.perf.PerfCounters.on
    everyui.perf.PerfCounters.on = true
    everyui.perf.PerfCounters.reset()
    def now(): Vector[Int] = {
      val all = everyui.perf.PerfCounters.counts.toMap
      names.toVector.map(all)
    }
    val most = Array.fill(names.length)(0)
    try {
      var before = now()
      var i = 0
      while (i < steps) {
        step(i)
        val after = now()
        names.indices.foreach(n => most(n) = math.max(most(n), after(n) - before(n)))
        before = after
        i += 1
      }
      names.indices.map(n => names(n) -> (before(n), most(n))).toMap
    } finally {
      everyui.perf.PerfCounters.reset()
      everyui.perf.PerfCounters.on = was
    }
  }

  /** `check` on the FX thread until it holds, a turn at a time: for what comes a few turns on, or after a timer. It
    * gives up only after ten seconds, a bound for what never comes, as a machine busy with other work can hold the FX
    * thread and the timers up for well over a second; what holds at once costs no more. */
  protected def eventually(check: => Boolean): Boolean = {
    val until = System.currentTimeMillis() + 10000L
    var held = fx(check)
    while (!held && System.currentTimeMillis() < until) {
      Thread.sleep(10L)
      held = fx(check)
    }
    held
  }

  private var stage: javafx.stage.Stage = null

  /** The scene in a window (under Xvfb) until `offStage`. JavaFX keeps every node changed in a scene that has been in
    * a window (MotionSpec puts it in one) on a list of nodes to draw, which only a window's pulse empties: out of one,
    * the list held each node changed since, and what a spec let go of stayed reachable through it. */
  protected def onStage(): Unit = fx {
    javafx.application.Platform.setImplicitExit(false)
    stage = new javafx.stage.Stage()
    stage.setScene(Host.scene)
    stage.show()
  }

  protected def offStage(): Unit = fx {
    stage.hide()
    stage.setScene(null)
    stage = null
  }

  override def beforeAll(): Unit = {
    val started = new CountDownLatch(1)
    try javafx.application.Platform.startup(() => {
      FxSpec.started = true
      started.countDown()
    })
    catch { case _: IllegalStateException => started.countDown() }
    started.await(10, TimeUnit.SECONDS)
    fx {
      if (Host.scene == null) {
        Host.root = new Pane()
        Host.scene = new Scene(Host.root, 1920.0, 1080.0)
      }
      ui.size.width.get
      window = Host.scene
      // no key left held by a spec before this one: a modifier is released as a keyboard releases it, which the
      // backend reads the modifiers held from
      javafx.event.Event.fireEvent(window, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_RELEASED, "", "",
        javafx.scene.input.KeyCode.SHIFT, false, false, false, false))
      // nor the focus left shown by the keys a spec before this one pressed: it starts as the spec says
      everyui.focus.Focus.visible @= keyed
      // nor a tip left showing, nor tips set as a spec before this one wanted them (one that turned the focus's tips
      // off left a later spec's focus with none): as Tooltip starts
      everyui.tooltip.Tooltip.hide()
      everyui.tooltip.Tooltip.delay @= 0.6
      everyui.tooltip.Tooltip.onFocus @= !everyui.render.Platform.remoteNavigation()
    }
  }
}

object FxSpec {
  /** Whether the JavaFX thread has come up in this JVM, once for every spec. */
  @volatile private var started: Boolean = false
}
