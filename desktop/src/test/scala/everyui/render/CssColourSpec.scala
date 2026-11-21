package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A colour written for an engine without `#rrggbbaa` (CssColour): that one as `rgba(...)`, anything else as it is. */
class CssColourSpec extends AnyWordSpec with Matchers {
  "A colour seen through" should {
    "be written rgba, its alpha to three places" in {
      CssColour.written("#0a0e16f2") shouldBe "rgba(10, 14, 22, 0.949)"
      CssColour.written("#FF8000") shouldBe "#FF8000"
      CssColour.written("#ffffff80") shouldBe "rgba(255, 255, 255, 0.502)"
      CssColour.written("#00000000") shouldBe "rgba(0, 0, 0, 0.0)"
    }
    "leave anything else as it is" in {
      List("", "#fff", "#ffffff", "transparent", "rgba(1, 2, 3, 0.5)", "#gggggggg", "currentColor")
        .foreach(c => CssColour.written(c) shouldBe c)
    }
  }
}
