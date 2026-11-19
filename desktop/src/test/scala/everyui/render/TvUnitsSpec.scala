package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A TV's screen in the app's units (TvUnits), as the web backend lays an installed app out: 540 tall, so 960 by 540 at
  * 1080p and at 720p as on Android TV and a Roku, a unit two CSS pixels at 1080. */
class TvUnitsSpec extends AnyWordSpec with Matchers {
  "A TV's units" should {
    "be two CSS pixels at 1080, so the screen is 960 by 540" in {
      TvUnits.scale(1080.0) shouldBe 2.0
      1920.0 / TvUnits.scale(1080.0) shouldBe 960.0
      1080.0 / TvUnits.scale(1080.0) shouldBe 540.0
    }
    "be a third larger than a CSS pixel at 720, so 1280 by 720 is 960 by 540 too" in {
      1280.0 / TvUnits.scale(720.0) shouldBe 960.0 +- 1e-9
      720.0 / TvUnits.scale(720.0) shouldBe 540.0 +- 1e-9
    }
    "be one CSS pixel where the page's height is not known" in {
      TvUnits.scale(0.0) shouldBe 1.0
    }
  }
}
