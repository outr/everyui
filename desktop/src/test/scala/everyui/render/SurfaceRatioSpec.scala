package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The screen's pixels to a CSS pixel as the page is drawn (SurfaceRatio), from what `devicePixelRatio` says and what
  * the resolution query matches. */
class SurfaceRatioSpec extends AnyWordSpec with Matchers {
  // a resolution query that matches up to `surface`
  private def query(surface: Double): Double => Option[Boolean] = r => Some(r <= surface)

  "The surface's ratio" should {
    "be devicePixelRatio where the query agrees" in {
      SurfaceRatio.of(2.0, query(2.0)) shouldBe 2.0
      SurfaceRatio.of(1.0, query(1.0)) shouldBe 1.0
      SurfaceRatio.of(1.1667, query(1.1667)) shouldBe 1.1667
      SurfaceRatio.of(2.625, query(2.625)) shouldBe 2.625
    }
    "be what the query matches where devicePixelRatio says more (webOS 3: 2 said, 1:1 drawn)" in {
      SurfaceRatio.of(2.0, query(1.0)) shouldBe 1.0
      SurfaceRatio.of(3.0, query(1.5)) shouldBe 1.5
    }
    "be devicePixelRatio where the query is not known, and 1 where nothing is said" in {
      SurfaceRatio.of(2.0, _ => None) shouldBe 2.0
      SurfaceRatio.of(0.0, query(1.0)) shouldBe 1.0
    }
  }
}
