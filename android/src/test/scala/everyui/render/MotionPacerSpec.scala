package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The motions' steps go on after the app goes away and comes back, whenever in the gap it went, and after a frame
  * asked for never comes: Voidcraft's spinners stood still after each return to the app (0.1.28, a Pixel Fold), the
  * step asked for as it went never stepped, and nothing asked again. A clock and a display of the spec's own stand in
  * for Android's Handler and Choreographer (PacerRig). */
class MotionPacerSpec extends AnyWordSpec with Matchers {
  "A motion's steps" should {
    "go on, gap after gap, while frames come" in {
      val r = new PacerRig
      r.pacer.ask()
      r.run(10)
      r.steps shouldBe 10
    }

    "go on after the app went away with a frame asked for and never come" in {
      val r = new PacerRig
      r.pacer.ask()
      r.run(3)
      r.advance(30L)
      r.frameAsked shouldBe true
      // away: no vsync for a window behind another, so the frame never comes
      r.pacer.away()
      r.frameAsked shouldBe false
      r.advance(5000L)
      r.steps shouldBe 3
      r.pacer.back()
      r.run(5)
      r.steps shouldBe 8
    }

    "go on after the app went away mid-gap, before its frame was asked" in {
      val r = new PacerRig
      r.pacer.ask()
      r.run(2)
      r.advance(10L)
      r.pacer.away()
      r.advance(5000L)
      r.frameAsked shouldBe false
      r.steps shouldBe 2
      r.pacer.back()
      r.run(4)
      r.steps shouldBe 6
    }

    "go on after the app came back with no word it went (a frame lost to a surface made again)" in {
      val r = new PacerRig
      r.pacer.ask()
      r.run(2)
      r.advance(30L)
      // the frame asked is lost: the surface was made again
      r.frameAsked = false
      r.advance(300L)
      r.frameAsked shouldBe true
      r.frame()
      r.run(3)
      r.steps shouldBe 6
    }

    "go on after the app came back with nothing lost, asked once and not twice" in {
      val r = new PacerRig
      r.pacer.ask()
      r.run(2)
      r.pacer.back()
      r.pacer.back()
      r.posted.size shouldBe 2
      r.run(3)
      r.steps shouldBe 5
    }

    "ask for nothing while away, or while nothing moves" in {
      val r = new PacerRig
      r.pacer.away()
      r.pacer.ask()
      r.posted shouldBe empty
      r.pacer.back()
      r.live = false
      r.run(3)
      r.steps shouldBe 0
      r.posted shouldBe empty
      r.frameAsked shouldBe false
    }
  }
}
