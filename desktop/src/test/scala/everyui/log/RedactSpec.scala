package everyui.log

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** An address's secrets masked for a log: a secret parameter's value, whatever its case and wherever it is in the
  * query, and the user and password before a host; the rest of the address, and of the line, as it was. */
class RedactSpec extends AnyWordSpec with Matchers {
  "Redact" should {
    "mask a stream's token, and leave the rest of its address and the line as they were" in {
      Redact("media: https://10.0.0.2:32400/library/parts/7/file.mp3?X-Plex-Token=abc123&download=1 failed") shouldBe
        "media: https://10.0.0.2:32400/library/parts/7/file.mp3?X-Plex-Token=…&download=1 failed"
    }
    "mask every secret parameter, at the end, in the middle and before a fragment, whatever its case" in {
      Redact("https://h/a?x=1&access_token=t0k&API_KEY=k#top") shouldBe "https://h/a?x=1&access_token=…&API_KEY=…#top"
      Redact("see https://h/p?sig=s1, then https://g/q?password=p\"") shouldBe
        "see https://h/p?sig=…, then https://g/q?password=…\""
    }
    "leave parameters that are no secrets, and words that only look like one" in {
      Redact("https://h/a?keyword=cats&monkey=1&page=2") shouldBe "https://h/a?keyword=cats&monkey=1&page=2"
      Redact("the token was refused") shouldBe "the token was refused"
    }
    "mask the user and password of an address, not a path's @" in {
      Redact("ftp://ann:s3cret@files.example.com/a") shouldBe "ftp://…@files.example.com/a"
      Redact("https://example.com/@ann/posts") shouldBe "https://example.com/@ann/posts"
    }
    "pass over a long line once, and give a line with no secrets back as it was" in {
      // a megabyte of parameters, the token near its end: looking to the end of the line for each one's "=" took
      // minutes and gigabytes over a line like it
      val long = "https://h/a?" + ("x=1&" * 250000) + "X-Plex-Token=t0k&y=2"
      val started = System.nanoTime()
      val masked = Redact(long)
      (System.nanoTime() - started) / 1.0e9 should be < 10.0
      masked should endWith("X-Plex-Token=…&y=2")
      masked.length shouldBe long.length - 2
      val plain = "&&&" * 300000
      (Redact(plain) eq plain) shouldBe true
      val clean = "https://h/a?page=2&size=10"
      (Redact(clean) eq clean) shouldBe true
    }
  }
}
