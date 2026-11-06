package everyui.theme

import everyui.FxSpec
import reactify.*

/** The device's scheme is read as "dark" or "light", and `Theme.follow` sets the palette from it, or from the app's
  * own choice where it makes one, following the choice as it changes. */
class SystemSchemeSpec extends FxSpec {
  "The system's scheme" should {
    "be read as dark or light" in {
      Set("dark", "light") should contain (fx(SystemScheme()))
      fx(SystemScheme.dark) shouldBe (fx(SystemScheme()) == "dark")
    }
    "set the palette, or the app's choice in its place, followed as it changes" in {
      val was = fx(Theme.palette())
      val choice = Var("system")
      try {
        fx(Theme.follow(Palette.light, Palette.dark, choice()))
        fx(Theme.palette()) shouldBe (if (fx(SystemScheme.dark)) Palette.dark else Palette.light)
        fx(choice @= "light")
        fx(Theme.palette()) shouldBe Palette.light
        fx(choice @= "dark")
        fx(Theme.palette()) shouldBe Palette.dark
        fx(Theme.colorScheme()) shouldBe "dark"
      } finally fx(Theme.palette @= was)
    }
  }
}
