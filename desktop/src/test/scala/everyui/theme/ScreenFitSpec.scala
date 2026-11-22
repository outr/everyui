package everyui.theme

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import reactify.*

/** `ui`'s start (`ScreenFit`) on Android's arithmetic, the screen in the app's units the window's pixels over the
  * density times the size last drawn at (`Host.scale`): whether the room is known at the first measure or only once the
  * window is laid out, the size drawn at settles where the device's text size reaches the room (Voidcraft on a narrow
  * screen, text 200%, EveryUI 0.1.41: a cold launch drawn at the whole 2.0, 224 units across, until the next change). */
class ScreenFitSpec extends AnyWordSpec with Matchers {
  // the window `pixels` across at `density`, its width unknown (0) until it is laid out where `late`; `asked` the text
  // size; what the platform was last told to draw at, and how often
  private class Device(pixels: Double, density: Double, asked: Double, chosen: Double = 1.0, late: Boolean = false) {
    val room: Var[Double] = Var(0.0)
    val width: Var[Double] = Var(0.0)
    val height: Var[Double] = Var(0.0)
    var applied: Double = 0.0
    var told: Int = 0
    private var known = !late
    val fit: ScreenFit = new ScreenFit(room, () => UiScale.drawnAt(chosen, asked, room()),
      () => if (known) pixels / density else 0.0,
      () => if (known) pixels / (density * applied) else 0.0,
      () => if (known) 2364.0 / (density * applied) else 0.0,
      d => {
        told += 1
        if (told > 50) throw new IllegalStateException("drawn again for ever")
        applied = d
      },
      width, height)
    // the window laid out, its insets dispatched: `Platform.onResize`
    def laidOut(): Unit = {
      known = true
      fit.measure()
    }
  }

  "The size drawn at from the start" should {
    "be held to the room where the room is known at the first measure" in {
      val d = new Device(1092.0, 2.4375, 2.0)
      d.fit.drawn() shouldBe 1.4
      d.applied shouldBe 1.4
      d.width() shouldBe 320.0 +- 0.000001
      d.laidOut()
      d.applied shouldBe 1.4
      d.told shouldBe 2
    }
    "be held to the room where the room is unknown at the first measure and known once the window is laid out" in {
      val d = new Device(1092.0, 2.4375, 2.0, late = true)
      d.applied shouldBe 2.0
      d.laidOut()
      d.fit.drawn() shouldBe 1.4
      d.applied shouldBe 1.4
      d.room() shouldBe 448.0
      d.width() shouldBe 320.0 +- 0.000001
      d.laidOut()
      d.told shouldBe 2
    }
    "settle on every screen of the sweep, the room known at once or once laid out" in {
      var all = 0
      var wrong = 0
      for (w <- 700 to 2400 by 3; density <- List(1.0, 1.5, 2.4375, 2.625, 3.0, 3.5);
           chosen <- List(0.8, 1.0, 1.5); asked <- List(1.0, 1.3, 2.0); late <- List(false, true)) {
        all += 1
        val d = new Device(w.toDouble, density, asked, chosen, late)
        d.laidOut()
        val settled = UiScale.drawnAt(chosen, asked, w / density)
        if (d.applied != settled || d.fit.drawn() != settled || d.told > 3) wrong += 1
      }
      info(s"$wrong of $all wrong")
      wrong shouldBe 0
    }
  }
}
