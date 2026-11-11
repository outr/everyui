package everyui.compiler

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The Swift emitter's string literals (SwiftEmitter.literal): one swiftc refuses is never written. */
class SwiftEmitterSpec extends AnyWordSpec with Matchers {
  "SwiftEmitter.literal" should {
    "escape the characters swiftc refuses or a reader cannot see" in {
      SwiftEmitter.literal("\u007f") shouldBe "\"\\u{7f}\""
      SwiftEmitter.literal("a\u0000b\u001bc") shouldBe "\"a\\u{0}b\\u{1b}c\""
      SwiftEmitter.literal("\u0085\u009f") shouldBe "\"\\u{85}\\u{9f}\""
      SwiftEmitter.literal("\u200b\u2028\u2029\ufeff") shouldBe "\"\\u{200b}\\u{2028}\\u{2029}\\u{feff}\""
    }
    "write quotes, backslashes, newlines and tabs as Swift's escapes" in {
      SwiftEmitter.literal("say \"hi\"\\\n\r\t") shouldBe "\"say \\\"hi\\\"\\\\\\n\\r\\t\""
    }
    "keep printable text, accents, scripts and emoji as they are" in {
      SwiftEmitter.literal("Español العربية 简体 \ud83d\ude00") shouldBe "\"Español العربية 简体 \ud83d\ude00\""
    }
    "write half of a surrogate pair alone as U+FFFD, which is what a Swift String would hold" in {
      SwiftEmitter.literal("\ud83d") shouldBe "\"\\u{fffd}\""
      SwiftEmitter.literal("x\ude00y") shouldBe "\"x\\u{fffd}y\""
    }
    "never leave a raw control or format character in its output" in {
      val all = SwiftEmitter.literal((0 until 0x10000).map(_.toChar).mkString)
      all.exists(c => Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) shouldBe false
    }
  }
}
