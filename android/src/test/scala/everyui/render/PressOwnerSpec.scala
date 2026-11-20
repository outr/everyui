package everyui.render

import everyui.event.PointerKind
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Which touches a node takes as its own on Android, where the one view that takes a touch's start has the whole touch:
  * a node that only watches a press (a picker's row whose words were cut short, its tip hiding as it is pressed) left
  * Voidcraft's workspace switcher's rows dead to taps, the list round them never hearing the tap. */
class PressOwnerSpec extends AnyWordSpec with Matchers {
  import PointerKind.*

  "A node hearing the pointer" should {
    "take a touch where it answers a click or a long press, follows the pointer's moves or its lift, or drags" in {
      PressOwner.owns(Set(Wheel, Down, Click, Menu), drags = false) shouldBe true
      PressOwner.owns(Set(Click), drags = false) shouldBe true
      PressOwner.owns(Set(Menu), drags = false) shouldBe true
      PressOwner.owns(Set(Down, Move, Up), drags = false) shouldBe true
      // a chart followed by a finger, and a hover lit (PointerEvents.hover)
      PressOwner.owns(Set(Down, Move), drags = false) shouldBe true
      PressOwner.owns(Set(Enter, Leave, Down, Move), drags = false) shouldBe true
      PressOwner.owns(Set(Down), drags = true) shouldBe true
      PressOwner.owns(Set.empty, drags = true) shouldBe true
    }
    "leave a touch to what it is in where it only watches the press go down" in {
      // a row's tip (RowTips) and a component's (Tooltip): the pointer's coming and going, and a press to hide it
      PressOwner.owns(Set(Down), drags = false) shouldBe false
      PressOwner.owns(Set(Enter, Leave, Down), drags = false) shouldBe false
      // and the wheel, which no touch brings
      PressOwner.owns(Set(Wheel, Down), drags = false) shouldBe false
    }
  }
}
