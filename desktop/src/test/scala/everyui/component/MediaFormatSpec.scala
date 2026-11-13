package everyui.component

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A codec as `Platform.decodes(codec, container, profile, level, bitDepth)` reads it, in one spelling whatever the
  * media server's: what each platform asks its device after. */
class MediaFormatSpec extends AnyWordSpec with Matchers {
  "MediaFormat.of" should {
    "spell the codec and the container one way" in {
      MediaFormat.of("H265", "Matroska", "", "", 0) shouldBe MediaFormat("hevc", "mkv", "", 0.0, 0)
      MediaFormat.of("avc1", "mpegts", "", "", 0).codec shouldBe "h264"
      MediaFormat.of("mpeg4 avc", "m2ts", "", "", 0).container shouldBe "ts"
      MediaFormat.of("hevc", "m3u8", "", "", 0).container shouldBe "hls"
      MediaFormat.of("hevc", ".m4v", "", "", 0).container shouldBe "mp4"
      MediaFormat.of("ac3", "", "", "", 0).video shouldBe false
      MediaFormat.of("vp09", "webm", "", "", 0).video shouldBe true
    }
    "read the profile without its spaces, and ten bits from it" in {
      val main10 = MediaFormat.of("hevc", "mkv", "Main 10", "", 0)
      main10.profile shouldBe "main10"
      main10.bitDepth shouldBe 10
      main10.deep shouldBe true
      MediaFormat.of("h264", "", "High 10", "", 0).bitDepth shouldBe 10
      MediaFormat.of("vp9", "", "Profile 2", "", 0).profile shouldBe "2"
      MediaFormat.of("vp9", "", "Profile 2", "", 0).bitDepth shouldBe 10
      MediaFormat.of("hevc", "", "main", "", 0).bitDepth shouldBe 0
      MediaFormat.of("hevc", "", "main", "", 8).deep shouldBe false
      MediaFormat.of("hevc", "", "", "", 10).deep shouldBe true
    }
    "read a level as a number however the server writes it" in {
      MediaFormat.of("hevc", "", "", "5.1", 0).levelText shouldBe "5.1"
      MediaFormat.of("hevc", "", "", "153", 0).levelText shouldBe "5.1"
      MediaFormat.of("hevc", "", "", "120", 0).levelText shouldBe "4.0"
      MediaFormat.of("h264", "", "", "41", 0).levelText shouldBe "4.1"
      MediaFormat.of("h264", "", "", "L4.1", 0).levelText shouldBe "4.1"
      MediaFormat.of("hevc", "", "", "", 0).levelText shouldBe ""
      MediaFormat.of("hevc", "", "", "unknown", 0).level shouldBe 0.0
    }
  }
}
