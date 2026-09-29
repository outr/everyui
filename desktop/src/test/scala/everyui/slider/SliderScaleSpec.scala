package everyui.slider

import everyui.event.{Key, KeyEvent, Keys}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A slider's numbers: values snapped to steps and kept in range, a range's thumbs kept apart, and where each key
  * moves a value, mirrored for a right-to-left language. */
class SliderScaleSpec extends AnyWordSpec with Matchers {
  private def press(name: String, shift: Boolean = false): KeyEvent = KeyEvent(Keys.of(name), name, false, false, false, shift)

  "A scale" should {
    "snap to its steps and keep in range" in {
      val s = SliderScale(0.0, 100.0, 5.0)
      s.snap(12.4) shouldBe 10.0
      s.snap(12.5) shouldBe 15.0
      s.snap(-3.0) shouldBe 0.0
      s.snap(140.0) shouldBe 100.0
      s.snap(99.0) shouldBe 100.0
    }
    "count small steps without drift" in {
      val s = SliderScale(0.0, 1.0, 0.1)
      s.snap(0.29) shouldBe 0.3
      s.snap(0.71) shouldBe 0.7
      (0 to 10).map(i => s.snap(i * 0.1)) shouldBe (0 to 10).map(i => i / 10.0)
    }
    "snap to divisions where there is no step, and run smoothly with neither" in {
      val d = SliderScale(10.0, 20.0, divisions = Some(4))
      d.grain shouldBe 2.5
      d.snap(13.0) shouldBe 12.5
      d.ticks shouldBe List(0.0, 0.25, 0.5, 0.75, 1.0)
      val smooth = SliderScale(0.0, 1.0)
      smooth.snap(0.3333) shouldBe 0.3333
      smooth.snap(1.2) shouldBe 1.0
      smooth.ticks shouldBe Nil
    }
    "reach its end where its steps do not, and never pass it" in {
      val s = SliderScale(0.0, 10.0, 3.0)
      s.snap(9.4) shouldBe 9.0
      s.snap(9.8) shouldBe 10.0
      s.snap(12.0) shouldBe 10.0
      s.below(8.0) shouldBe 6.0
      s.above(7.0) shouldBe 9.0
    }
    "give fractions and values between its ends" in {
      val s = SliderScale(-50.0, 50.0, 1.0)
      s.fraction(0.0) shouldBe 0.5
      s.fraction(80.0) shouldBe 1.0
      s.value(0.254) shouldBe -25.0
      s.value(-1.0) shouldBe -50.0
    }
    "move a value a step with an arrow, a tenth with Page Up and Down, to its ends with Home and End" in {
      val s = SliderScale(0.0, 100.0, 1.0)
      s.key(press("ArrowRight"), 40.0, false, false) shouldBe Some(41.0)
      s.key(press("ArrowLeft"), 40.0, false, false) shouldBe Some(39.0)
      s.key(press("PageUp"), 40.0, false, false) shouldBe Some(50.0)
      s.key(press("PageDown"), 5.0, false, false) shouldBe Some(0.0)
      s.key(press("Home"), 40.0, false, false) shouldBe Some(0.0)
      s.key(press("End"), 40.0, false, false) shouldBe Some(100.0)
      s.key(press("ArrowRight"), 100.0, false, false) shouldBe Some(100.0)
    }
    "leave the arrows across it to the focus" in {
      val s = SliderScale(0.0, 100.0, 1.0)
      s.key(press("ArrowUp"), 40.0, false, false) shouldBe None
      s.key(press("ArrowDown"), 40.0, false, false) shouldBe None
      s.key(press("ArrowLeft"), 40.0, true, false) shouldBe None
      s.key(press("ArrowUp"), 40.0, true, false) shouldBe Some(41.0)
      s.key(press("Tab"), 40.0, false, false) shouldBe None
      s.key(press("Enter"), 40.0, false, false) shouldBe None
    }
    "mirror Left and Right in a right-to-left language, but not Up and Down" in {
      val s = SliderScale(0.0, 100.0, 1.0)
      s.key(press("ArrowRight"), 40.0, false, true) shouldBe Some(39.0)
      s.key(press("ArrowLeft"), 40.0, false, true) shouldBe Some(41.0)
      s.key(press("ArrowUp"), 40.0, true, true) shouldBe Some(41.0)
    }
    "step a smooth scale by a hundredth and a tenth, and a coarse one by a step at least" in {
      val smooth = SliderScale(0.0, 2.0)
      smooth.key(press("ArrowRight"), 1.0, false, false) shouldBe Some(1.02)
      smooth.key(press("PageUp"), 1.0, false, false) shouldBe Some(1.2)
      val coarse = SliderScale(0.0, 10.0, 4.0)
      coarse.pageStep shouldBe 4.0
    }
    "keep a range's thumbs from crossing, and a gap apart" in {
      val s = SliderScale(0.0, 100.0, 5.0)
      s.start(70.0, 60.0, 0.0) shouldBe 60.0
      s.start(70.0, 60.0, 10.0) shouldBe 50.0
      s.start(12.0, 60.0, 10.0) shouldBe 10.0
      s.end(30.0, 40.0, 10.0) shouldBe 50.0
      s.end(98.0, 40.0, 10.0) shouldBe 100.0
      // a gap off the steps lands on the step inside it
      s.start(80.0, 60.0, 7.0) shouldBe 50.0
      s.end(0.0, 40.0, 7.0) shouldBe 50.0
      val smooth = SliderScale(0.0, 1.0)
      smooth.start(0.9, 0.5, 0.1) shouldBe 0.4 +- 1e-9
    }
  }

  "A value's words" should {
    "be to two places, with no trailing zeros" in {
      SliderScale.text(3.0) shouldBe "3"
      SliderScale.text(0.25) shouldBe "0.25"
      SliderScale.text(1.5) shouldBe "1.5"
      SliderScale.text(0.05) shouldBe "0.05"
      SliderScale.text(-1.5) shouldBe "-1.5"
      SliderScale.text(-0.001) shouldBe "0"
      SliderScale.text(2.999) shouldBe "3"
    }
  }
}
