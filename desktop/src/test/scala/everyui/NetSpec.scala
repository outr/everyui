package everyui

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A value encoded for a URL as the standard library does it for a path segment, and decoded back as it went in. */
class NetSpec extends AnyWordSpec with Matchers {
  "net.encode" should {
    "keep the unreserved characters and escape the rest" in {
      net.encode("AZaz09-._~") shouldBe "AZaz09-._~"
      net.encode("team/a:1") shouldBe "team%2Fa%3A1"
      net.encode("a b+c&d=e?f#g%h") shouldBe "a%20b%2Bc%26d%3De%3Ff%23g%25h"
      net.encode("") shouldBe ""
    }
    "escape a character as the bytes of its UTF-8" in {
      net.encode("é") shouldBe "%C3%A9"
      net.encode("€") shouldBe "%E2%82%AC"
      net.encode("😀") shouldBe "%F0%9F%98%80"
    }
    "agree with the JVM's encoder, space aside" in {
      val samples = List("conv/2026-09-29:ab", "naïve café", "日本語 テキスト", "a😀b", "!*'();@&=+$,/?#[]")
      samples.foreach { s =>
        net.encode(s) shouldBe java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("*", "%2A")
          .replace("%7E", "~")
      }
    }
    "be undone by decode" in {
      val samples = List("team/a:1", "a b+c", "é€😀", "100%", "")
      samples.foreach(s => net.decode(net.encode(s)) shouldBe s)
    }
  }
}
