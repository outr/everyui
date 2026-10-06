package everyui.event

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A finger's tap near a small control: the control answers anywhere in the 48-pixel square around its centre, a
  * larger one only to its edges, and the nearer centre takes a tap two reaches share. */
class TapSlopSpec extends AnyWordSpec with Matchers {
  // a 24-pixel glyph at 100, 100: its centre at 112, 112
  private val glyph = (100.0, 100.0, 24.0, 24.0)

  "A tap near a control" should {
    "reach a 24-pixel glyph from up to 12 pixels past its edges, and no further" in {
      TapSlop.reach shouldBe 48.0
      TapSlop.pick(112.0, 112.0, Vector(glyph)) shouldBe Some(0)
      // just inside 44 and 48 across, on each side and at a corner
      TapSlop.pick(112.0 + 21.0, 112.0, Vector(glyph)) shouldBe Some(0)
      TapSlop.pick(112.0 - 23.5, 112.0, Vector(glyph)) shouldBe Some(0)
      TapSlop.pick(112.0, 112.0 + 23.5, Vector(glyph)) shouldBe Some(0)
      TapSlop.pick(112.0 + 23.0, 112.0 - 23.0, Vector(glyph)) shouldBe Some(0)
      TapSlop.pick(112.0 + 24.5, 112.0, Vector(glyph)) shouldBe None
      TapSlop.pick(112.0, 112.0 - 30.0, Vector(glyph)) shouldBe None
    }
    "reach a control larger than a fingertip only to its edges" in {
      val wide = (0.0, 0.0, 200.0, 60.0)
      TapSlop.pick(199.0, 59.0, Vector(wide)) shouldBe Some(0)
      TapSlop.pick(201.0, 30.0, Vector(wide)) shouldBe None
      // a wide chip 24 tall reaches 12 above and below, its own width across
      val chip = (0.0, 0.0, 100.0, 24.0)
      TapSlop.pick(50.0, 35.0, Vector(chip)) shouldBe Some(0)
      TapSlop.pick(101.0, 12.0, Vector(chip)) shouldBe None
    }
    "give a tap two reaches share to the control whose centre is nearer" in {
      // two 32-pixel icons 8 apart: centres at 16 and 56, the gap from 32 to 40
      val icons = Vector((0.0, 0.0, 32.0, 32.0), (40.0, 0.0, 32.0, 32.0))
      TapSlop.pick(35.0, 16.0, icons) shouldBe Some(0)
      TapSlop.pick(37.0, 16.0, icons) shouldBe Some(1)
      TapSlop.pick(37.0, 38.0, icons) shouldBe Some(1)
      TapSlop.pick(-6.0, 16.0, icons) shouldBe Some(0)
      TapSlop.pick(79.0, 16.0, icons) shouldBe Some(1)
    }
    "be judged where the finger went down, not where the browser moved the click" in {
      // Voidcraft's header: a 32-pixel Menu button at 4, 10, the conversation's list from 52 down. A finger 22 pixels
      // under the button's centre went down at 20, 48, on the header; Chrome sent its click to the list at 20, 55, 29
      // pixels from the centre and out of reach. From where the finger went down, the button takes it.
      val menu = Vector((4.0, 10.0, 32.0, 32.0))
      TapSlop.pick(20.0, 55.0, menu) shouldBe None
      TapSlop.retarget(20.0, 48.0, menu, Vector(true), None) shouldBe Some(0)
      // the click already on its way to it is left alone
      TapSlop.retarget(20.0, 48.0, menu, Vector(true), Some(0)) shouldBe None
      // where the finger landed on another control (a tab under the header) that control keeps it
      TapSlop.retarget(20.0, 48.0, menu, Vector(false), None) shouldBe None
      // and of two in reach, the open one nearer the finger, wherever the click was sent
      val two = Vector((4.0, 10.0, 32.0, 32.0), (44.0, 10.0, 32.0, 32.0))
      TapSlop.retarget(42.0, 40.0, two, Vector(true, true), Some(0)) shouldBe Some(1)
      TapSlop.retarget(42.0, 40.0, two, Vector(true, false), Some(1)) shouldBe Some(0)
    }
    "never reach a control that is not drawn" in {
      TapSlop.pick(0.0, 0.0, Vector((0.0, 0.0, 0.0, 0.0))) shouldBe None
      TapSlop.pick(3.0, 3.0, Vector((0.0, 0.0, 0.0, 0.0), glyph)) shouldBe None
    }
  }
}
