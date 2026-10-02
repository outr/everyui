package everyui.event

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A finger's flick on a touch screen: how fast it went as it lifted, from its last tenth of a second, and how the
  * content glides on from there, slowing as a phone's own lists do, until it rests or reaches an end. */
class FlingSpec extends AnyWordSpec with Matchers {
  // a finger moving `perMs` pixels a millisecond, a move every 16 ms for `ms`, from `start` (ms) at 0
  private def moving(fling: Fling, perMs: Double, ms: Double, start: Double = 0.0, from: Double = 0.0): Double = {
    var t = 0.0
    while (t <= ms) {
      fling.at(from + perMs * t, start + t)
      t += 16.0
    }
    start + t - 16.0
  }

  "A finger's velocity" should {
    "be how fast it moved as it lifted" in {
      val fling = new Fling
      val last = moving(fling, 2.5, 100.0)
      fling.velocity(last + 5.0) shouldBe 2.5 +- 0.001
    }
    "be negative moving the other way" in {
      val fling = new Fling
      val last = moving(fling, -1.2, 200.0)
      fling.velocity(last + 5.0) shouldBe -1.2 +- 0.001
    }
    "count only its last tenth of a second" in {
      val fling = new Fling
      // slowly for half a second, then fast
      val slow = moving(fling, 0.2, 500.0)
      val last = moving(fling, 3.0, 96.0, slow + 16.0, 0.2 * slow + 3.0 * 16.0)
      fling.velocity(last + 4.0) shouldBe 3.0 +- 0.05
    }
    "be none where it stopped before it lifted" in {
      val fling = new Fling
      val last = moving(fling, 2.5, 100.0)
      fling.velocity(last + Fling.stopped + 1.0) shouldBe 0.0
    }
    "be none for a touch that never moved, or barely did" in {
      val still = new Fling
      still.at(300.0, 0.0)
      still.velocity(10.0) shouldBe 0.0
      val creeping = new Fling
      val last = moving(creeping, 0.01, 100.0)
      creeping.velocity(last + 5.0) shouldBe 0.0
    }
    "not be thrown by a jittery finger" in {
      val fling = new Fling
      // 2 px a millisecond, each move a pixel or two off
      Vector(0.0, 16.0, 32.0, 48.0, 64.0, 80.0, 96.0).zip(Vector(1.0, -2.0, 2.0, -1.0, 1.0, -2.0, 1.0)).foreach {
        case (t, off) => fling.at(2.0 * t + off, t)
      }
      fling.velocity(100.0) shouldBe 2.0 +- 0.05
    }
    "be no faster than the fastest" in {
      val fling = new Fling
      val last = moving(fling, 30.0, 64.0)
      fling.velocity(last + 1.0) shouldBe Fling.fastest
    }
    "forget a drag before it started again" in {
      val fling = new Fling
      moving(fling, 2.5, 100.0)
      fling.clear()
      fling.at(10.0, 200.0)
      fling.velocity(205.0) shouldBe 0.0
    }
  }

  "A fling" should {
    "slow as a phone's does, over a plausible distance and time" in {
      // a flick of 2.5 px a millisecond (250 px in a tenth of a second) goes about 1250 px more, in about 2.4 s
      Fling.distance(2.5) shouldBe 1249.0 +- 2.0
      var went = 0.0
      var frames = 0
      val run = new FlingRun(2.5, (d: Double) => { went += d; d })
      while (run.advance(1000.0 / 60.0)) frames += 1
      went shouldBe 1249.0 +- 15.0
      (frames * 1000.0 / 60.0) shouldBe 2400.0 +- 150.0
      run.gliding shouldBe false
    }
    "go each frame less far than the last" in {
      val steps = scala.collection.mutable.ArrayBuffer.empty[Double]
      val run = new FlingRun(-2.0, (d: Double) => { steps += d; d })
      while (run.advance(16.0)) ()
      steps.forall(_ < 0.0) shouldBe true
      steps.zip(steps.tail).forall { case (a, b) => math.abs(b) < math.abs(a) } shouldBe true
    }
    "go as far whatever the frame rate" in {
      def far(ms: Double): Double = {
        var went = 0.0
        val run = new FlingRun(1.5, (d: Double) => { went += d; d })
        while (run.advance(ms)) ()
        went
      }
      far(1000.0 / 120.0) shouldBe far(1000.0 / 30.0) +- 5.0
    }
    "stop at an end of the content" in {
      // 300 px left before the end
      var at = 0.0
      var frames = 0
      val run = new FlingRun(2.5, (d: Double) => {
        val was = at
        at = math.min(300.0, at + d)
        at - was
      })
      while (run.advance(16.0)) frames += 1
      at shouldBe 300.0
      frames should be < 15
    }
    "stop where a finger catches it" in {
      var went = 0.0
      val run = new FlingRun(2.5, (d: Double) => { went += d; d })
      run.advance(16.0) shouldBe true
      val caught = went
      run.stop()
      run.advance(16.0) shouldBe false
      went shouldBe caught
      run.gliding shouldBe false
    }
  }
}
