package everyui.compiler

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** An interpolated string's literal parts as both emitters write them (RokuEmitter.interpolated): TASTy holds a part
  * as it was typed, so `s"a\tb"` went out as a backslash and a t on an Apple TV and a Roku until its escapes were
  * taken at the emit. */
class InterpolationSpec extends AnyWordSpec with Matchers {
  // each part as the TASTy holds it: the characters between the quotes
  private val typed = "a\\tb\\n\\\"q\\\"\\\\\\u0041\\r\\b\\f\\'"

  "RokuEmitter.interpolated" should {
    "take an s part's escapes as StringContext does on the JVM" in {
      RokuEmitter.interpolated("s", typed) shouldBe "a\tb\n\"q\"\\A\r\b\f'"
      RokuEmitter.interpolated("s", "no escapes, Español \ud83d\ude00") shouldBe "no escapes, Español \ud83d\ude00"
      RokuEmitter.interpolated("s", "") shouldBe ""
    }
    "keep a raw part's backslashes" in {
      RokuEmitter.interpolated("raw", typed) shouldBe typed
      RokuEmitter.interpolated("raw", "C:\\dir\\new") shouldBe "C:\\dir\\new"
    }
    "keep a part as typed when its escape is not one" in {
      RokuEmitter.interpolated("s", "a\\qb") shouldBe "a\\qb"
    }
    "give text that SwiftEmitter.literal writes as Swift's escapes" in {
      SwiftEmitter.literal(RokuEmitter.interpolated("s", "id\\tname\\n")) shouldBe "\"id\\tname\\n\""
      SwiftEmitter.literal(RokuEmitter.interpolated("raw", "id\\tname")) shouldBe "\"id\\\\tname\""
    }
  }
}
