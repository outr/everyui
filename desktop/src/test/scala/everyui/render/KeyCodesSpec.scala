package everyui.render

import everyui.event.{Key, Keys}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A key named by its code where the engine gives no `key` (KeyCodes; webOS 3's Chromium 38), read as EveryUI reads
  * a key's name: a remote's arrows, OK and Back, a keyboard's editing keys and characters. */
class KeyCodesSpec extends AnyWordSpec with Matchers {
  "name" should {
    "name a remote's arrows and OK as EveryUI's keys" in {
      Map(37 -> Key.ArrowLeft, 38 -> Key.ArrowUp, 39 -> Key.ArrowRight, 40 -> Key.ArrowDown, 13 -> Key.Enter)
        .foreach { case (code, key) => Keys.of(KeyCodes.name(code, shift = false)) shouldBe key }
    }
    "name a keyboard's editing keys as the web does" in {
      List(8 -> "Backspace", 9 -> "Tab", 27 -> "Escape", 46 -> "Delete", 36 -> "Home", 35 -> "End", 33 -> "PageUp",
        34 -> "PageDown", 112 -> "F1", 123 -> "F12", 32 -> " ")
        .foreach { case (code, name) => KeyCodes.name(code, shift = false) shouldBe name }
    }
    "give a character by the US layout, Shift taken" in {
      KeyCodes.name(65, shift = false) shouldBe "a"
      KeyCodes.name(90, shift = true) shouldBe "Z"
      KeyCodes.name(49, shift = false) shouldBe "1"
      KeyCodes.name(50, shift = true) shouldBe "@"
      KeyCodes.name(48, shift = true) shouldBe ")"
      KeyCodes.name(97, shift = false) shouldBe "1"
      KeyCodes.name(190, shift = false) shouldBe "."
      KeyCodes.name(191, shift = true) shouldBe "?"
      KeyCodes.name(222, shift = true) shouldBe "\""
    }
    "say Unidentified for a key it does not know (a TV's own keys are its host's)" in {
      KeyCodes.name(461, shift = false) shouldBe "Unidentified"
      KeyCodes.name(0, shift = false) shouldBe "Unidentified"
    }
  }
}
