package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Whether a video moved on the page lost its source in the move (`MoveReset.dropped`), told by its state after it:
  * webOS 3.5 leaves it with nothing loaded, no source and its playhead at 0; every other engine plays on, or (Chromium
  * 38) is only paused. */
class MoveResetSpec extends AnyWordSpec with Matchers {
  // readyState, networkState, playhead, seeking, looping after a move from 42 s in
  private def after(ready: Int, network: Int, now: Double, seeking: Boolean = false, looping: Boolean = false,
                    loaded: Boolean = true): Boolean =
    MoveReset.dropped(loaded, 42.0, ready, network, now, seeking, looping)

  "A move" should {
    "have let the source go as webOS 3.5 lets it go: nothing loaded, no source, at 0" in {
      after(0, MoveReset.noSource, 0.0) shouldBe true
    }
    "have let it go with nothing loaded, or with no source, either alone" in {
      after(0, 2, 42.0) shouldBe true
      after(1, MoveReset.noSource, 42.0) shouldBe true
    }
    "have let it go where the engine loaded it again from the start" in {
      after(4, 1, 0.0) shouldBe true
      after(2, 2, 39.0) shouldBe true
    }
    "have kept it where it plays on, or was only paused, a little on or back" in {
      after(4, 1, 42.3) shouldBe false
      after(4, 2, 41.0) shouldBe false
      after(3, 1, 44.0 - ResumeSeek.near) shouldBe false
      after(4, 1, 60.0) shouldBe false
    }
    "not be taken for one by a seek of the player's own or a loop coming round" in {
      after(4, 1, 0.5, seeking = true) shouldBe false
      after(4, 1, 0.5, looping = true) shouldBe false
    }
    "not be taken for one before the source has loaded: still opening, it has nothing yet" in {
      after(0, 2, 0.0, loaded = false) shouldBe false
      after(0, MoveReset.noSource, 0.0, loaded = false) shouldBe false
    }
  }

  "The looks after a move" should {
    "be soon, and over within a couple of seconds" in {
      MoveReset.checks.head should be <= 100
      MoveReset.checks.last should be <= 2000
      MoveReset.checks shouldBe MoveReset.checks.sorted
    }
  }
}
