package everyui.render

import everyui.motion.{Keyframe, Track}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Whether a motion is in sight is read from everywhere its cycle takes its view (MotionReach), not from where it holds
  * the view now: NaboTV's leaves, which their motions take out past the edge of the screen and back in at the other,
  * were stopped as they went out, and none came in again. A spinner scrolled away stays as far away as it was. */
class MotionReachSpec extends AnyWordSpec with Matchers {
  private def reach(box: (Double, Double, Double, Double), tracks: List[Track], x: Double = 0.0, y: Double = 0.0,
                    turned: Boolean = false, scale: Double = 1.0): List[Double] = {
    val at = Array(box._1, box._2, box._3, box._4)
    MotionReach(at, tracks, x, y, (box._1 + box._3) / 2.0, (box._2 + box._4) / 2.0, turned, scale, scale)
    at.toList.map(v => math.round(v * 100.0) / 100.0)
  }

  "A motion's reach" should {
    "be where the node is placed, with no motion that moves it" in {
      reach((0.0, 0.0, 10.0, 10.0), Nil, 5.0, 7.0) shouldBe List(5.0, 7.0, 15.0, 17.0)
      reach((0.0, 0.0, 10.0, 10.0), List(Track(Track.Opacity, 1.0, List(Keyframe(0.0, 0.0), Keyframe(1.0, 1.0)))), 5.0,
        7.0) shouldBe List(5.0, 7.0, 15.0, 17.0)
    }

    "take in every place a position track goes, and where it rests" in {
      val track = Track(Track.Position, 20.0, List(Keyframe(0.0, 300.0, 200.0), Keyframe(0.5, 900.0, 1100.0),
        Keyframe(0.5005, -1920.0, 1100.0), Keyframe(0.501, -1920.0, -50.0), Keyframe(1.0, 300.0, 200.0)))
      reach((0.0, 0.0, 10.0, 10.0), List(track), -5.0, -5.0) shouldBe List(-1925.0, -55.0, 905.0, 1105.0)
    }

    "keep a leaf at its wrap in sight of the screen it falls over" in {
      // a leaf held out past the left side: its reach still crosses a 1920 by 1080 screen
      val track = Track(Track.Position, 20.0, List(Keyframe(0.0, 400.0, -54.0), Keyframe(0.4999, 600.0, 1134.0),
        Keyframe(0.5, -1920.0, 1134.0), Keyframe(0.5001, -1920.0, -54.0), Keyframe(1.0, 400.0, -54.0)))
      val List(l, t, r, b) = reach((0.0, 0.0, 22.0, 22.0), List(track), -11.0, -11.0)
      (l < 1920.0 && r > 0.0 && t < 1080.0 && b > 0.0) shouldBe true
    }

    "turn about its pivot as far as its farthest corner, at its largest scale" in {
      val turn = Track(Track.Rotation, 1.0, List(Keyframe(0.0, 0.0), Keyframe(1.0, 6.28)))
      reach((0.0, 0.0, 6.0, 8.0), List(turn)) shouldBe List(-2.0, -1.0, 8.0, 9.0)
      val grow = Track(Track.Scale, 1.0,
        List(Keyframe(0.0, 1.0, 1.0), Keyframe(0.5, 2.0, 2.0), Keyframe(1.0, 1.0, 1.0)))
      reach((0.0, 0.0, 6.0, 8.0), List(grow)) shouldBe List(-7.0, -6.0, 13.0, 14.0)
      // turned or scaled of its own, by no track
      reach((0.0, 0.0, 6.0, 8.0), Nil, turned = true) shouldBe List(-2.0, -1.0, 8.0, 9.0)
      reach((0.0, 0.0, 6.0, 8.0), Nil, scale = 2.0) shouldBe List(-7.0, -6.0, 13.0, 14.0)
    }

    "leave a spinner scrolled far below where it is" in {
      val turn = Track(Track.Rotation, 1.0, List(Keyframe(0.0, 0.0), Keyframe(1.0, 6.28)))
      val List(_, t, _, b) = reach((0.0, 0.0, 20.0, 20.0), List(turn), 10.0, 2000.0)
      t should be > 1985.0
      b should be < 2035.0
    }
  }
}
