package everyui.component

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A codec as RFC 6381 writes it (an HLS playlist's CODECS) read as `Platform.decodes` asks after it (CodecString,
  * MediaFormat.of): the codec, its profile, level, bit depth and HDR, and the string kept to ask a browser with. */
class CodecStringSpec extends AnyWordSpec with Matchers {
  private def of(codec: String, container: String = "mp4"): MediaFormat = MediaFormat.of(codec, container, "", "", 0)

  "A codec string" should {
    "read H.264's profile and level, in hex and in the old decimal form" in {
      of("avc1.640028") shouldBe MediaFormat("h264", "mp4", "high", 4.0, 0, "", "avc1.640028")
      of("avc1.4D401F").profile shouldBe "main"
      of("avc1.4D401F").levelText shouldBe "3.1"
      of("avc1.42E01E").profile shouldBe "constrainedbaseline"
      of("avc3.640033").levelText shouldBe "5.1"
      of("avc1.6E0033").bitDepth shouldBe 10
      of("avc1.66.30") shouldBe MediaFormat("h264", "mp4", "baseline", 3.0, 0, "", "avc1.66.30")
    }
    "read HEVC's profile, tier and level, hvc1 and hev1 alike" in {
      of("hvc1.2.4.L153.B0") shouldBe MediaFormat("hevc", "mp4", "main10", 5.1, 10, "", "hvc1.2.4.L153.B0")
      of("hev1.1.6.L120.90").profile shouldBe "main"
      of("hev1.1.6.L120.90").levelText shouldBe "4.0"
      of("hvc1.A1.60000000.H150.B0").levelText shouldBe "5.0"
      of("hvc1.1.6.L93.B0").deep shouldBe false
    }
    "read VP9 and AV1, and HDR from their transfer characteristics" in {
      of("vp09.00.10.08", "webm") shouldBe MediaFormat("vp9", "webm", "0", 1.0, 8, "", "vp09.00.10.08")
      val pq = of("vp09.02.51.10.01.09.16.09.00", "webm")
      (pq.profile, pq.levelText, pq.bitDepth, pq.hdr) shouldBe (("2", "5.1", 10, "hdr10"))
      of("vp09.02.40.10.01.09.18.09.00").hdr shouldBe "hlg"
      val av1 = of("av01.0.13M.10.0.112.09.16.09.0")
      (av1.codec, av1.profile, av1.levelText, av1.bitDepth, av1.hdr) shouldBe (("av1", "main", "5.1", 10, "hdr10"))
      of("av01.0.05M.08").levelText shouldBe "3.1"
      of("vp8", "webm").codec shouldBe "vp8"
    }
    "read Dolby Vision as the codec under it" in {
      of("dvh1.08.06") shouldBe MediaFormat("hevc", "mp4", "main10", 0.0, 10, "dolbyvision", "dvh1.08.06")
      of("dvhe.05.06").hdr shouldBe "dolbyvision"
      of("dav1.10.06").codec shouldBe "av1"
      of("dvav.09.05").codec shouldBe "h264"
    }
    "read the sounds' strings" in {
      of("mp4a.40.2").codec shouldBe "aac"
      of("mp4a.40.5").profile shouldBe "he"
      of("mp4a.40.34").codec shouldBe "mp3"
      of("mp4a.6B").codec shouldBe "mp3"
      of("mp4a.A5").codec shouldBe "ac3"
      of("mp4a.a6").codec shouldBe "eac3"
      of("ac-3").codec shouldBe "ac3"
      of("ec-3") shouldBe MediaFormat("eac3", "mp4", "", 0.0, 0, "", "ec-3")
      of("ec+3").profile shouldBe "joc"
      of("Opus").codec shouldBe "opus"
      of("fLaC").codec shouldBe "flac"
      of("dtsc").codec shouldBe "dts"
      of("dtsh").profile shouldBe "hd"
      of("dtsx").profile shouldBe "x"
      of("mlpa").codec shouldBe "truehd"
      of("mp4a.40.2").video shouldBe false
    }
    "let a profile, level or bit depth given beside it win" in {
      MediaFormat.of("hvc1.1.6.L93.B0", "mkv", "Main 10", "153", 0) shouldBe
        MediaFormat("hevc", "mkv", "main10", 5.1, 10, "", "hvc1.1.6.L93.B0")
      MediaFormat.of("avc1.640028", "", "", "", 10).bitDepth shouldBe 10
    }
    "leave a plain name to its name, and read an HDR name alone" in {
      of("hevc") shouldBe MediaFormat("hevc", "mp4", "", 0.0, 0)
      of("ac-3").tag shouldBe "ac-3"
      MediaFormat.of("E-AC-3", "", "", "", 0).codec shouldBe "eac3"
      MediaFormat.of("dca", "", "", "", 0).codec shouldBe "dts"
      MediaFormat.of("hdr10", "", "", "", 0) shouldBe MediaFormat("", "", "", 0.0, 0, "hdr10")
      MediaFormat.of("HLG", "", "", "", 0).hdr shouldBe "hlg"
      MediaFormat.of("Dolby Vision", "", "", "", 0).hdr shouldBe "dolbyvision"
      MediaFormat.of("dovi", "", "", "", 0).codec shouldBe ""
    }
  }
}
