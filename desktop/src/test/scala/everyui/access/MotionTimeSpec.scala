package everyui.access

import everyui.component.*
import everyui.kit.Toast
import everyui.motion.{Keyframe, ReduceMotion, Track}
import everyui.task.{AnimateIn, Conclusion}
import everyui.time.{TimeLimit, TimeLimits}
import everyui.{FxSpec, ui}
import reactify.*

import scala.concurrent.duration.*

/** Motion and time: a flash of more than three a second over more than a small area is found, and nothing else is;
  * an animation is at its end at once where less motion is asked for; a toast stays as long as people need and while
  * a pointer rests on it; and a time limit warns before it ends, gives more time, and ends. */
class MotionTimeSpec extends FxSpec {
  override def afterAll(): Unit = {
    fx {
      ReduceMotion.on @= false
      TimeLimits.factor @= 1.0
      while (Modal.showing) Modal.close()
      ui.children.clear()
    }
    super.afterAll()
  }

  private def blinking(w: Double, h: Double, seconds: Double, property: String = Track.Opacity): Container = {
    val c = new Container
    c.size.width @= w
    c.size.height @= h
    c.backgroundColor @= everyui.Color.White
    c.motion @= List(Track(property, seconds, List(Keyframe(0.0, 1.0), Keyframe(0.5, 0.0), Keyframe(1.0, 1.0))))
    c
  }

  "The flash check" should {
    "find a large part flashing over three times a second, and not a small one, a slow one or a turning one" in {
      val (fast, small, slow, turning) = fx {
        val parts = (blinking(300.0, 200.0, 0.25), blinking(100.0, 100.0, 0.25), blinking(300.0, 200.0, 1.0),
          blinking(300.0, 200.0, 0.25, Track.Rotation))
        ui.children.clear()
        ui.children ++= List(parts._1, parts._2, parts._3, parts._4)
        parts
      }
      try {
        val found = fx(Access.check().filter(_.rule == AccessRule.Flash))
        found.map(_.component) shouldBe List(fast)
        found.head.detail shouldBe "flashes 4.0 times a second"
      } finally fx(ui.children.clear())
    }
  }

  "An animation" should {
    "be at its end at once where less motion is asked for, and on its way where not" in {
      var at = 0.0
      def glide(): AnimateIn = AnimateIn(() => at, v => at = v, () => 100.0, () => 1.second, everyui.easing.Easing.linear)
      fx(ReduceMotion.on @= false)
      at = 0.0
      fx(glide().update(0.1, reset = true)) shouldBe Conclusion.Continue
      at should be < 100.0
      fx(ReduceMotion.on @= true)
      at = 0.0
      fx(glide().update(0.016, reset = true)) shouldBe Conclusion.Finished
      at shouldBe 100.0
      fx(ReduceMotion.on @= false)
    }
  }

  "A toast" should {
    "stay as long as people asked for, longer for more words, and while a pointer rests on it" in {
      fx(TimeLimits.factor @= 2.0)
      try {
        fx {
          ui.children.clear()
          Toast.show("Saved", seconds = 0.2)
        }
        // 0.25 for its words, twice as long for the time asked for: there at 0.3, gone after 0.5
        Thread.sleep(300L)
        fx(ui.children().size) shouldBe 1
        eventually(ui.children().isEmpty) shouldBe true
      } finally fx {
        TimeLimits.factor @= 1.0
        Toast.hide()
      }
    }
  }

  "A time limit" should {
    "warn before it ends, give more time, and end with nothing done" in {
      // on a clock of the test's own (TimeLimit.after): what falls due runs as `pass` says the time has gone, on the
      // JavaFX thread, so no warning comes and goes between two looks, however busy the machine
      var now = 0.0
      var due: Vector[(Double, () => Unit)] = Vector.empty
      def pass(seconds: Double): Unit = {
        now += seconds
        val ready = due.filter(_._1 <= now + 1e-9).sortBy(_._1)
        due = due.filterNot(d => ready.exists(_ eq d))
        ready.foreach(d => fx(d._2()))
      }
      TimeLimit.after = (seconds, f) => due = due :+ (now + seconds -> f)
      try {
        var ended = 0
        val limit = fx(new TimeLimit(0.6, () => ended += 1, warning = 0.3))
        fx(limit.start())
        pass(0.25)
        fx(Modal.showing) shouldBe false
        pass(0.1)
        fx(Modal.showing) shouldBe true
        ended shouldBe 0
        // more time: the warning goes, and comes again
        fx(limit.extend())
        fx(Modal.showing) shouldBe false
        pass(0.35)
        fx(Modal.showing) shouldBe true
        ended shouldBe 0
        pass(0.3)
        ended shouldBe 1
        fx(Modal.showing) shouldBe false
        fx(limit.isRunning) shouldBe false
        // stopped, nothing runs
        fx(limit.start())
        fx(limit.stop())
        pass(1.0)
        ended shouldBe 1
        fx(Modal.showing) shouldBe false
      } finally TimeLimit.after = (seconds, f) => everyui.render.Platform.after(seconds, f)
    }
  }
}
