package everyui.theme

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The size drawn at settles where the device's text size reaches the room the screen leaves (`UiScale.narrowest`):
  * `ui`'s cycle (the size drawn at, the screen measured over it, the room set, the size worked out again) run for
  * every width, density, size chosen and text size of Voidcraft's sweep (EveryUI 0.1.39 on a Fold: 14,124 of them
  * chased their own last bit for ever, the app laid out again each turn, a black screen and an ANR), on Android's
  * arithmetic (pixels over the density times the size) and the desktop's and the web's (a width over the size). */
class UiScaleRoomSpec extends AnyWordSpec with Matchers {
  private val widths = 700 to 2400
  private val densities = List(1.0, 1.25, 1.5, 2.0, 2.4375, 2.625, 2.75, 3.0, 3.5)
  private val chosen = (8 to 15).map(_ / 10.0)
  private val asked = (10 to 20).map(_ / 10.0)

  // how many times the size drawn at changes before it holds (as `ui`'s `drawn`, a Val, changes only on a new value),
  // past `limit` -1; `roomOf` the room measured for the pixels across, the density and the size drawn at
  private def turns(pixels: Double, density: Double, chosen: Double, asked: Double,
                    roomOf: (Double, Double, Double) => Double, limit: Int = 50): Int = {
    var room = 0.0
    var drawn = UiScale.drawnAt(chosen, asked, room)
    var changes = 0
    var held = false
    while (!held && changes <= limit) {
      // measureScreen: the room set (a Var, so heard only where it differs)
      val measured = roomOf(pixels, density, drawn)
      if (measured == room) held = true
      else {
        room = measured
        val next = UiScale.drawnAt(chosen, asked, room)
        if (next == drawn) held = true
        else {
          drawn = next
          changes += 1
        }
      }
    }
    if (held) changes else -1
  }

  private def sweep(roomOf: (Double, Double, Double) => Double): (Int, Int) = {
    var all = 0
    var unsettled = 0
    for (w <- widths; d <- densities; c <- chosen; a <- asked) {
      all += 1
      val t = turns(w.toDouble, d, c, a, roomOf)
      if (t < 0 || t > 2) unsettled += 1
    }
    (all, unsettled)
  }

  // the room read back over the size it decides, as `ui` had it: the screen's width in the app's units (Android's
  // pixels over the density times the size; the desktop's and the web's width over the size) times the size
  private val readBack: (Double, Double, Double) => Double = (pixels, density, drawn) =>
    pixels / (density * drawn) * drawn
  // the window's own width, as `ui` has it now (`Platform.screenRoom`)
  private val window: (Double, Double, Double) => Double = (pixels, density, _) => pixels / density

  "The size the app is drawn at" should {
    "settle at once on every screen of the sweep, the room the window's own width" in {
      val (all, unsettled) = sweep(window)
      info(s"$unsettled of $all unsettled")
      all shouldBe 1701 * 9 * 8 * 11
      unsettled shouldBe 0
    }
    "settle even where the room is read back over it, a bit or two off, as it was on 0.1.39" in {
      // Voidcraft's case: 1092 pixels at 2.4375, text 200%: 320.00000000000006 across, then 319.99999999999994
      turns(1092.0, 2.4375, 1.0, 2.0, readBack) should (be >= 0 and be <= 2)
      UiScale.drawnAt(1.0, 2.0, 448.00000000000006) shouldBe UiScale.drawnAt(1.0, 2.0, 447.99999999999994)
      val (all, unsettled) = sweep(readBack)
      info(s"$unsettled of $all unsettled")
      unsettled shouldBe 0
    }
    "be held to a billionth, and take a size within rounding of another as the same" in {
      UiScale.steady(1.4000000000000001) shouldBe 1.4
      UiScale.steady(1.3) shouldBe 1.3
      UiScale.drawnAt(1.0, 2.0, 390.0) shouldBe 1.21875
      UiScale.near(320.00000000000006, 319.99999999999994) shouldBe true
      UiScale.near(320.0, 320.001) shouldBe false
      UiScale.near(0.0, 0.0) shouldBe true
      UiScale.near(Double.NaN, 1.0) shouldBe false
    }
  }
}
