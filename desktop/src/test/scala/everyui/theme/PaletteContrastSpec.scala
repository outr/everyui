package everyui.theme

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** EveryUI's palettes meet WCAG 2.2 AA (`Palette.audit`), and the audit finds what one that does not falls short in. */
class PaletteContrastSpec extends AnyWordSpec with Matchers {
  "The palettes" should {
    "meet AA: words 4.5:1 on every ground, a control's edge 3:1" in {
      Palette.audit(Palette.dark) shouldBe Nil
      Palette.audit(Palette.light) shouldBe Nil
    }
    "find where a palette falls short" in {
      val faint = Palette.light.copy(muted = everyui.Color.fromHex("9e9e9e"))
      Palette.audit(faint) should contain ("muted on background 2.57")
      Palette.audit(faint).forall(_.startsWith("muted on ")) shouldBe true
    }
  }
}
