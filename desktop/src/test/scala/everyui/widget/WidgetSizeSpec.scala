package everyui.widget

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A widget's size class from the size its launcher gives it, and as a kind that draws only some sizes has it. */
class WidgetSizeSpec extends AnyWordSpec with Matchers {
  "A widget's size" should {
    "be compact while narrow, medium wide and short, and large wide and tall" in {
      WidgetSize.of(110.0, 110.0) shouldBe WidgetSize.Compact
      WidgetSize.of(140.0, 400.0) shouldBe WidgetSize.Compact
      WidgetSize.of(0.0, 0.0) shouldBe WidgetSize.Compact
      WidgetSize.of(250.0, 110.0) shouldBe WidgetSize.Medium
      WidgetSize.of(200.0, 199.0) shouldBe WidgetSize.Medium
      WidgetSize.of(250.0, 250.0) shouldBe WidgetSize.Large
      WidgetSize.of(200.0, 200.0) shouldBe WidgetSize.Large
    }
    "be the largest a kind draws that is not larger, or its smallest" in {
      val all = Set(WidgetSize.Compact, WidgetSize.Medium, WidgetSize.Large)
      WidgetSize.within(WidgetSize.Medium, all) shouldBe WidgetSize.Medium
      WidgetSize.within(WidgetSize.Medium, Set.empty) shouldBe WidgetSize.Medium
      WidgetSize.within(WidgetSize.Large, Set(WidgetSize.Compact, WidgetSize.Medium)) shouldBe WidgetSize.Medium
      WidgetSize.within(WidgetSize.Large, Set(WidgetSize.Compact)) shouldBe WidgetSize.Compact
      WidgetSize.within(WidgetSize.Compact, Set(WidgetSize.Medium, WidgetSize.Large)) shouldBe WidgetSize.Medium
      WidgetSize.within(WidgetSize.Medium, Set(WidgetSize.Large)) shouldBe WidgetSize.Large
    }
  }
}
