package everyui.tray

import everyui.Color
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A tray icon's picture at several sizes: each platform's size found or scaled from the nearest, a template kept a
  * template at every size, and a badge drawn on every size, no longer a template. */
class TrayImageSpec extends AnyWordSpec with Matchers {
  private def solid(px: Int, argb: Int): TrayImage = TrayImage(px, px, Array.fill(px * px)(argb))

  "A tray image" should {
    "give the size asked for, else scale the smallest bigger one down, or the biggest up" in {
      val set = TrayImage.of(solid(16, 0xff0000ff), solid(32, 0xff00ff00), solid(64, 0xffff0000))
      set.sizes.map(_.width) shouldBe List(16, 32, 64)
      set.sized(32).argb.head shouldBe 0xff00ff00
      // 24 from the 32, 20 from the 32 too
      set.sized(24).width shouldBe 24
      set.sized(24).argb.distinct.toList shouldBe List(0xff00ff00)
      set.sized(20).argb.distinct.toList shouldBe List(0xff00ff00)
      // 128 from the 64, the only one bigger than nothing
      set.sized(128).argb.distinct.toList shouldBe List(0xffff0000)
      solid(16, 0xff000000).sized(16).width shouldBe 16
    }
    "average what a pixel covers going down, weighting colour by alpha" in {
      // a 2×2 of one opaque white pixel and three clear black ones, to 1×1: white at a quarter alpha
      val quarter = TrayImage(2, 2, Array(0xffffffff, 0x00000000, 0x00000000, 0x00000000)).scaled(1, 1)
      quarter.argb.head shouldBe 0x40ffffff
    }
    "stay a template at every size, until it is badged" in {
      val t = TrayImage.of(solid(18, 0xff000000), solid(36, 0xff000000)).asTemplate
      t.sizes.map(_.template) shouldBe List(true, true)
      t.sized(22).template shouldBe true
      val b = t.badged(Color.Green)
      b.sizes.map(_.width) shouldBe List(18, 36)
      b.sizes.map(_.template) shouldBe List(false, false)
      b.sizes.foreach(s => s.argb.last should not be 0xff000000)
    }
  }
}
