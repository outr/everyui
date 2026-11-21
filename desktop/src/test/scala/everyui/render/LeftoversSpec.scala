package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Android's players let go whose release timed out (Leftovers): kept and silenced until their release is done, and
  * a let-go player's error kept off the source that plays now. */
class LeftoversSpec extends AnyWordSpec with Matchers {
  // a stand-in player: whether its thread has done the release, and how often it was silenced
  private final class Stub {
    var released = false
    var silenced = 0
  }

  private def made: Leftovers[Stub] = new Leftovers[Stub](_.released, _.silenced += 1)

  "Leftovers" should {
    "keep a player until its release is done, silencing it at each sweep meanwhile" in {
      val leftovers = made
      val old = new Stub
      leftovers.keep(old, "lobby2.mp3", at = 10.0)
      leftovers.sweep(now = 11.0) shouldBe Nil
      leftovers.sweep(now = 12.0) shouldBe Nil
      old.silenced shouldBe 2
      leftovers.waiting(now = 12.5) shouldBe List(("lobby2.mp3", 2.5))
      old.released = true
      leftovers.sweep(now = 14.0) shouldBe List(("lobby2.mp3", 4.0))
      leftovers.isEmpty shouldBe true
      // once released it is not silenced again, nor said again
      leftovers.sweep(now = 15.0) shouldBe Nil
      old.silenced shouldBe 2
    }
    "keep each player once, and say each as its own release is done" in {
      val leftovers = made
      val a = new Stub
      val b = new Stub
      leftovers.keep(a, "a.mp3", at = 1.0)
      leftovers.keep(a, "a.mp3", at = 2.0)
      leftovers.keep(b, "b.mp3", at = 3.0)
      leftovers.size shouldBe 2
      b.released = true
      leftovers.sweep(now = 4.0) shouldBe List(("b.mp3", 1.0))
      leftovers.size shouldBe 1
      a.released = true
      leftovers.sweep(now = 5.0) shouldBe List(("a.mp3", 4.0))
    }
    "go on sweeping the rest when one cannot be asked or silenced" in {
      val broken = new Leftovers[Stub](s => if (s.silenced < 0) throw new IllegalStateException else s.released,
        s => if (s.released) () else throw new IllegalStateException("dead thread"))
      val odd = new Stub
      odd.silenced = -1
      val fine = new Stub
      broken.keep(odd, "odd.mp3", at = 0.0)
      broken.keep(fine, "fine.mp3", at = 0.0)
      broken.sweep(now = 1.0) shouldBe Nil
      fine.released = true
      broken.sweep(now = 2.0) shouldBe List(("fine.mp3", 2.0))
      broken.waiting(now = 2.0) shouldBe List(("odd.mp3", 2.0))
    }
  }

  "An error a player says" should {
    "reach the app only from the player playing the source" in {
      Leftovers.blame(current = true, timedOut = false) shouldBe Leftovers.Report
      // a timeout on the player playing now (a surface let go too slowly) is its own failure, as before
      Leftovers.blame(current = true, timedOut = true) shouldBe Leftovers.Report
    }
    "make a let-go player whose release timed out a leftover, and only log anything else it says" in {
      Leftovers.blame(current = false, timedOut = true) shouldBe Leftovers.Keep
      Leftovers.blame(current = false, timedOut = false) shouldBe Leftovers.Log
    }
  }
}
