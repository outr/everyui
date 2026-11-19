package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A TV's keyboard on the screen going down by itself with a field still focused (webOS's Back, or an arrow off its
  * edge), read as the web backend reads it (SystemKeyboard): the field is let go before the remote's next key goes on
  * from it, never on the keyboard going down (LG's voice button takes it down and up again), and a keyboard of its own
  * typing on keeps it. */
class SystemKeyboardSpec extends AnyWordSpec with Matchers {
  "A field whose keyboard went down by itself" should {
    "be let go before the next arrow, OK or Back, once" in {
      List("ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Enter", "GoBack").foreach { name =>
        val k = new SystemKeyboard
        k.shown(visible = true, inField = true)
        k.letsGo(name, inField = true) shouldBe false
        k.shown(visible = false, inField = true)
        k.letsGo(name, inField = true) shouldBe true
        k.letsGo(name, inField = true) shouldBe false
      }
    }
    "not be let go where the keyboard came back up (a voice button)" in {
      val k = new SystemKeyboard
      k.shown(visible = false, inField = true)
      k.shown(visible = true, inField = true)
      k.letsGo("Enter", inField = true) shouldBe false
    }
    "be kept while a keyboard of its own types on, Enter then submitting" in {
      val k = new SystemKeyboard
      k.shown(visible = false, inField = true)
      k.letsGo("a", inField = true) shouldBe false
      k.letsGo("Backspace", inField = true) shouldBe false
      k.letsGo("Enter", inField = true) shouldBe false
    }
    "be no field once the focus left it" in {
      val k = new SystemKeyboard
      k.shown(visible = false, inField = true)
      k.letsGo("Enter", inField = false) shouldBe false
      k.letsGo("Enter", inField = true) shouldBe false
    }
    "let go of nothing where the keyboard went down with no field focused, or for other keys" in {
      val k = new SystemKeyboard
      k.shown(visible = false, inField = false)
      k.letsGo("Enter", inField = true) shouldBe false
      k.shown(visible = false, inField = true)
      k.letsGo("MediaPlay", inField = true) shouldBe false
      k.letsGo("ColorF0Red", inField = true) shouldBe false
      k.letsGo("ArrowDown", inField = true) shouldBe true
    }
  }
}
