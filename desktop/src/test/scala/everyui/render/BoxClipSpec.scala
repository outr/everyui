package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** How the web cuts a box to itself (BoxClip): `overflow: clip` where the engine has it, else `hidden`, pinned. */
class BoxClipSpec extends AnyWordSpec with Matchers {
  "Whether the engine has overflow: clip" should {
    "be CSS.supports' answer where there is one" in {
      BoxClip.known(Some(true), sys.error("not read back")) shouldBe true
      BoxClip.known(Some(false), sys.error("not read back")) shouldBe false
    }
    "be whether clip reads back from a style where there is no CSS.supports" in {
      BoxClip.known(None, "clip") shouldBe true
      // an engine that does not know the value leaves the property as it was
      BoxClip.known(None, "") shouldBe false
    }
  }
  "A box cut to itself" should {
    "be overflow: clip, unpinned, where the engine has it" in {
      BoxClip.overflow(clipKnown = true) shouldBe "clip"
      BoxClip.pinned("clip") shouldBe false
    }
    "be overflow: hidden, pinned, before Chromium 90" in {
      BoxClip.overflow(clipKnown = false) shouldBe "hidden"
      BoxClip.pinned("hidden") shouldBe true
    }
    "be a stacking context of its own before Chromium 90 alone (its rounded corners cut nothing positioned else)" in {
      BoxClip.stacks(clipKnown = false) shouldBe true
      BoxClip.stacks(clipKnown = true) shouldBe false
    }
    "be put back only once it has moved off its start" in {
      BoxClip.shifted(0.0, 0.0) shouldBe false
      BoxClip.shifted(12.0, 0.0) shouldBe true
      BoxClip.shifted(0.0, -30.0) shouldBe true
    }
  }
}
