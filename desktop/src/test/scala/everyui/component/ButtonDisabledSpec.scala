package everyui.component

import everyui.FxSpec

/** A button turned off (`enabled`): it says so to whatever reads the page (`disabled`, the web's `aria-disabled`),
  * runs nothing, and the focus still reaches it; turned on again, all of it back. */
class ButtonDisabledSpec extends FxSpec {
  "A disabled button" should {
    "say it is out of use, run nothing, and keep the focus reaching it" in {
      var ran = 0
      val b = fx(new Button("Grant", () => ran += 1))
      fx(b.disabled()) shouldBe false
      fx(b.enabled @= false)
      fx(b.disabled()) shouldBe true
      fx(b.focusable()) shouldBe true
      fx(b.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0.0, 0.0))
      ran shouldBe 0
      fx(b.enabled @= true)
      fx(b.disabled()) shouldBe false
      fx(b.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0.0, 0.0))
      ran shouldBe 1
    }
  }
}
