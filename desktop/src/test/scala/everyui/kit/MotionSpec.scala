package everyui.kit

import everyui.render.{Host, Motion}
import everyui.{FxSpec, ui}
import javafx.stage.Stage

/** A component's motion on the desktop runs only while its node is in a shown window and visible: a spinner taken off
  * the screen without being disposed of, or hidden, stops its timeline, and JavaFX stops pulsing for it. The window is
  * shown (under Xvfb when the specs run), since a scene in no shown window runs no motion. */
class MotionSpec extends FxSpec {
  private var stage: Stage = null
  @volatile private var pulses = 0
  private val counter: Runnable = () => pulses += 1

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      ui.children.clear()
      // the toolkit kept when the window is hidden, as the specs after this one need it
      javafx.application.Platform.setImplicitExit(false)
      stage = new Stage()
      stage.setScene(Host.scene)
      stage.show()
      Host.scene.addPostLayoutPulseListener(counter)
    }
  }

  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      Host.scene.removePostLayoutPulseListener(counter)
      stage.hide()
      stage.setScene(null)
    }
    super.afterAll()
  }

  /** How many pulses JavaFX ran in `ms`. */
  private def pulsesIn(ms: Long): Int = {
    val before = pulses
    Thread.sleep(ms)
    pulses - before
  }

  /** Wait until no pulse has run for half a second: whether that came before a bound that only pulses that never stop
    * reach, however busy the machine. */
  private def settled(): Boolean = {
    val until = System.currentTimeMillis() + 20000L
    var still = pulsesIn(500L) == 0
    while (!still && System.currentTimeMillis() < until) still = pulsesIn(500L) == 0
    still
  }

  /** Wait until more than `count` pulses have run since `from` (a count of `pulses`): whether they did, before a bound
    * that only pulses that never come reach. Counted rather than timed, as a busy machine runs fewer a second. */
  private def pulsesPast(from: Int, count: Int): Boolean = {
    val until = System.currentTimeMillis() + 20000L
    while (pulses - from <= count && System.currentTimeMillis() < until) Thread.sleep(10L)
    pulses - from > count
  }

  // the spinner's turn now
  private def turn(spinner: Spinner): Double = fx(spinner.node.fx.getRotate)

  "A spinner's motion" should {
    "turn while it is shown" in {
      settled() shouldBe true
      val before = fx(Motion.running)
      val spinner = fx(new Spinner)
      fx(ui.children += spinner)
      try {
        fx(Motion.running) shouldBe before + 1
        val first = turn(spinner)
        val from = pulses
        eventually(spinner.node.fx.getRotate != first) shouldBe true
        pulsesPast(from, 5) shouldBe true
      }
      finally fx(ui.children -= spinner)
    }

    "stop when it is taken off without being disposed of, and JavaFX stop pulsing for it" in {
      val before = fx(Motion.running)
      val spinner = fx(new Spinner)
      fx(ui.children += spinner)
      fx(Motion.running) shouldBe before + 1
      fx(ui.children -= spinner)
      fx(Motion.running) shouldBe before
      settled() shouldBe true
      val still = turn(spinner)
      pulsesIn(600L) shouldBe 0
      turn(spinner) shouldBe still
      // and turn again from where it is when put back
      fx(ui.children += spinner)
      try {
        fx(Motion.running) shouldBe before + 1
        eventually(spinner.node.fx.getRotate != still) shouldBe true
      }
      finally fx(ui.children -= spinner)
    }

    "stop while hidden, and while its window is" in {
      val before = fx(Motion.running)
      val spinner = fx(new Spinner)
      fx(ui.children += spinner)
      try {
        fx(spinner.visible @= false)
        fx(Motion.running) shouldBe before
        settled() shouldBe true
        fx(spinner.visible @= true)
        fx(Motion.running) shouldBe before + 1
        fx(stage.hide())
        fx(Motion.running) shouldBe before
        fx(stage.show())
        fx(Motion.running) shouldBe before + 1
      }
      finally fx(ui.children -= spinner)
      fx(Motion.running) shouldBe before
    }

    "stop while a box it is in is hidden, and turn again once it is shown" in {
      val before = fx(Motion.running)
      val spinner = fx(new Spinner)
      val inner = fx(new everyui.component.Container)
      val plate = fx(new everyui.component.Container)
      fx {
        inner.children += spinner
        plate.children += inner
        ui.children += plate
      }
      try {
        fx(Motion.running) shouldBe before + 1
        // the box around the box hidden: the spinner's own visibility is untouched
        fx(plate.visible @= false)
        fx(spinner.visible()) shouldBe true
        fx(Motion.running) shouldBe before
        settled() shouldBe true
        val still = turn(spinner)
        pulsesIn(600L) shouldBe 0
        turn(spinner) shouldBe still
        fx(plate.visible @= true)
        fx(Motion.running) shouldBe before + 1
        eventually(spinner.node.fx.getRotate != still) shouldBe true
        // hidden by a box and by itself, it runs only once both are shown
        fx {
          inner.visible @= false
          spinner.visible @= false
        }
        fx(Motion.running) shouldBe before
        fx(inner.visible @= true)
        fx(Motion.running) shouldBe before
        fx(spinner.visible @= true)
        fx(Motion.running) shouldBe before + 1
      }
      finally fx(ui.children -= plate)
      fx(Motion.running) shouldBe before
      settled() shouldBe true
    }

    "stop once disposed of" in {
      val before = fx(Motion.running)
      val spinner = fx(new Spinner)
      fx(ui.children += spinner)
      fx(Motion.running) shouldBe before + 1
      fx {
        ui.children -= spinner
        spinner.dispose()
      }
      fx(Motion.running) shouldBe before
      settled() shouldBe true
    }
  }
}
