package everyui.tray

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The screen a tray icon was clicked on, from the point the panel names: three screens at 1.75 side by side, as
  * JavaFX lists them (the middle, the right, the left). Plasma under Wayland names its screen's place and then how far
  * in, in the screen's own pixels; elsewhere a point is as it is, and one on no screen goes to the nearest. */
class TrayScreenSpec extends AnyWordSpec with Matchers {
  private val screens = List(TrayScreen(2194.0, 0.0, 2194.0, 1234.0, 1.75), TrayScreen(4390.0, 0.0, 2194.0, 1234.0,
    1.75), TrayScreen(0.0, 0.0, 2194.0, 1234.0, 1.75))

  private def at(x: Int, y: Int, plasma: Boolean = true): (Int, Long, Long) = {
    val (i, px, py) = TrayScreen.locate(x, y, screens, plasma)
    (i, math.round(px), math.round(py))
  }

  "A tray click" should {
    "be found on the screen Plasma under Wayland means" in {
      // the right screen's tray, 1846 logical pixels in: named 4390 + 1846 × 1.75
      at(7621, 12) shouldBe (1, 6236L, 7L)
      // 1500 into the middle screen, which read against the right one would come out 245 into that
      at(2194 + 2625, 30) shouldBe (0, 3694L, 17L)
      // 2000 into the left one
      at(3500, 0) shouldBe (2, 2000L, 0L)
    }
    "be taken as it is elsewhere, and brought onto the nearest screen from none" in {
      at(5000, 10, plasma = false) shouldBe (1, 5000L, 10L)
      at(9000, 10, plasma = false) shouldBe (1, 6583L, 10L)
      at(-40, 500, plasma = false) shouldBe (2, 0L, 500L)
    }
  }
}
