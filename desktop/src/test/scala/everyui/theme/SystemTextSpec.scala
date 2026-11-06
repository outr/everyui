package everyui.theme

import everyui.{FxSpec, ui}

/** The device's text size draws the whole app larger with the size chosen, never leaving it under 320 across, and not
  * where it is not followed; the steps and the control go by the size chosen alone. */
class SystemTextSpec extends FxSpec {
  private def reset(): Unit = fx {
    UiScale.system @= 1.0
    UiScale.followsSystem @= true
    UiScale.factor @= 1.0
  }

  "The device's text size" should {
    "draw the app larger with the size chosen, the screen laid out in growing smaller" in {
      try {
        reset()
        val wide = fx(ui.size.width())
        fx(UiScale.system @= 1.5)
        fx(UiScale()) shouldBe 1.5
        fx(ui.size.width()) shouldBe (wide / 1.5 +- 0.5)
        fx(UiScale.factor @= 1.2)
        fx(UiScale()) shouldBe (1.8 +- 1e-9)
        // the steps and the percentage are the size chosen's
        fx(UiScale.chosen()) shouldBe 1.2
        fx(UiScale.percent()) shouldBe "120%"
        fx(UiScale.larger())
        fx(UiScale.factor()) shouldBe 1.3
      } finally reset()
    }
    "never leave the app under 320 across, nor be followed where it is turned off" in {
      try {
        reset()
        // a phone's screen, 390 across, asked for text twice as large: drawn 390 / 320 as large
        UiScale.drawnAt(1.0, 2.0, 390.0) shouldBe (390.0 / 320.0 +- 1e-9)
        // chosen larger already, the device's size takes only what is left: 320 across still
        390.0 / UiScale.drawnAt(1.2, 2.0, 390.0) shouldBe (320.0 +- 1e-9)
        // room to spare: as asked; smaller text asked for, smaller
        UiScale.drawnAt(1.0, 1.3, 1920.0) shouldBe (1.3 +- 1e-9)
        UiScale.drawnAt(1.0, 0.85, 390.0) shouldBe (0.85 +- 1e-9)
        fx(UiScale.system @= 1.5)
        fx(UiScale.followsSystem @= false)
        fx(UiScale()) shouldBe 1.0
      } finally reset()
    }
  }
}
