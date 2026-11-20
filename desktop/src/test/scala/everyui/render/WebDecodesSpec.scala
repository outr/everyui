package everyui.render

import everyui.component.MediaFormat
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What a browser or a TV's engine is asked for each codec `Platform.decodes` is asked after (CodecMime), and what an LG
  * TV says it shows beyond its engine (WebOSDisplay): the answers themselves are the engine's, checked in webcheck's
  * PlayerCheck. */
class WebDecodesSpec extends AnyWordSpec with Matchers {
  private def mime(codec: String, container: String = "", profile: String = "", level: String = "",
                   depth: Int = 0): Option[String] = CodecMime.of(MediaFormat.of(codec, container, profile, level, depth))

  "The MIME type a codec is asked as" should {
    "write a picture's codec out with its profile, level and depth, in its container" in {
      mime("hevc", "mp4", "main 10", "5.1", 10) shouldBe Some("video/mp4; codecs=\"hvc1.2.4.L153.B0\"")
      mime("hevc", "mkv", "main", "153") shouldBe Some("video/x-matroska; codecs=\"hvc1.1.6.L153.B0\"")
      mime("hevc", "ts") shouldBe Some("video/mp4; codecs=\"hvc1.1.6.L93.B0\"")
      mime("h264", "hls", "high", "4.1") shouldBe Some("video/mp4; codecs=\"avc1.640029\"")
      mime("h264", "mp4", "high 10", "5.1") shouldBe Some("video/mp4; codecs=\"avc1.6E0033\"")
      mime("vp9", "webm", "", "", 10) shouldBe Some("video/webm; codecs=\"vp09.02.10.10\"")
      mime("av1", "mp4", "", "5.1", 10) shouldBe Some("video/mp4; codecs=\"av01.0.13M.10\"")
      mime("vp8", "webm") shouldBe Some("video/webm; codecs=\"vp8\"")
      mime("h264", "avi") shouldBe Some("video/avi; codecs=\"avc1.42E01E\"")
    }
    "ask a codec string as it is given" in {
      mime("hev1.2.4.L150.90") shouldBe Some("video/mp4; codecs=\"hev1.2.4.L150.90\"")
      mime("vp09.02.51.10.01.09.16.09.00", "webm") shouldBe Some("video/webm; codecs=\"vp09.02.51.10.01.09.16.09.00\"")
      mime("dvh1.08.06") shouldBe Some("video/mp4; codecs=\"dvh1.08.06\"")
      mime("mp4a.40.2") shouldBe Some("audio/mp4; codecs=\"mp4a.40.2\"")
      mime("ec-3", "ts") shouldBe Some("audio/mp4; codecs=\"ec-3\"")
    }
    "ask Dolby Vision the host vouches for as the codec under it" in {
      CodecMime.of(MediaFormat.of("dvh1.08.06", "mp4", "", "", 0), vouched = true) shouldBe
        Some("video/mp4; codecs=\"hvc1.2.4.L93.B0\"")
    }
    "ask each sound in the type its container calls for" in {
      mime("aac") shouldBe Some("audio/mp4; codecs=\"mp4a.40.2\"")
      mime("ac3", "mkv") shouldBe Some("audio/mp4; codecs=\"ac-3\"")
      mime("eac3") shouldBe Some("audio/mp4; codecs=\"ec-3\"")
      mime("opus") shouldBe Some("audio/webm; codecs=\"opus\"")
      mime("opus", "mp4") shouldBe Some("audio/mp4; codecs=\"opus\"")
      mime("vorbis", "mkv") shouldBe Some("audio/webm; codecs=\"vorbis\"")
      mime("flac") shouldBe Some("audio/flac")
      mime("flac", "mp4") shouldBe Some("audio/mp4; codecs=\"flac\"")
      mime("mp3") shouldBe Some("audio/mpeg")
      mime("mp3", "mp4") shouldBe Some("audio/mp4; codecs=\"mp4a.6B\"")
      mime("dts") shouldBe Some("audio/mp4; codecs=\"dtsc\"")
      mime("dtsh") shouldBe Some("audio/mp4; codecs=\"dtsh\"")
      mime("truehd") shouldBe Some("audio/mp4; codecs=\"mlpa\"")
      mime("pcm_s16le") shouldBe Some("audio/wav; codecs=\"1\"")
    }
    "be asked in its family's other forms, as webOS 3's engine knows only some" in {
      CodecMime.forms("video/webm; codecs=\"vp09.00.10.08\"") shouldBe
        List("video/webm; codecs=\"vp09.00.10.08\"", "video/webm; codecs=\"vp9\"")
      CodecMime.forms("video/webm; codecs=\"vp09.02.10.10.01.09.16.09.01\"") shouldBe
        List("video/webm; codecs=\"vp09.02.10.10.01.09.16.09.01\"")
      CodecMime.forms("video/webm; codecs=\"vp9\"") shouldBe
        List("video/webm; codecs=\"vp9\"", "video/webm; codecs=\"vp09.00.10.08\"")
      CodecMime.forms("video/mp4; codecs=\"dvh1.08.06\"") shouldBe
        List("video/mp4; codecs=\"dvh1.08.06\"", "video/mp4; codecs=\"dvhe.08.06\"")
      CodecMime.forms("video/mp4; codecs=\"dvhe.05.06\"").last shouldBe "video/mp4; codecs=\"dvh1.05.06\""
      CodecMime.forms("video/mp4; codecs=\"dva1.09.05\"").last shouldBe "video/mp4; codecs=\"dvav.09.05\""
      CodecMime.forms("audio/mp4; codecs=\"mp4a.40.2\"") shouldBe List("audio/mp4; codecs=\"mp4a.40.2\"")
      CodecMime.forms("audio/mpeg") shouldBe List("audio/mpeg")
    }
    "write nothing for a codec it does not know, which is then a yes" in {
      mime("wmav2") shouldBe None
      mime("") shouldBe None
    }
  }

  "What an LG TV says it shows" should {
    "be read from webOS.deviceInfo, and the runtime's deviceInfo" in {
      WebOSDisplay.fromDeviceInfo("""{"modelName":"OLED55C1","uhd":true,"hdr10":true,"dolbyVision":false,"dolbyAtmos":true}""") shouldBe
        Map("hdr10" -> true, "hlg" -> true, "hdr10plus" -> false, "dolbyvision" -> false, "atmos" -> true, "uhd" -> true)
      WebOSDisplay.fromDeviceInfo("""{"modelName":"OLED55C4","platformVersionMajor":"9"}""") shouldBe Map.empty
      WebOSDisplay.fromDeviceInfo("not json") shouldBe Map.empty
    }
    "be read from the configuration service, as webOSTV.js reads it" in {
      WebOSDisplay.fromConfigs("""{"returnValue":true,"configs":{"tv.model.supportHDR":true,""" +
        """"tv.config.supportDolbyHDRContents":true,"tv.hw.panelResolution":"UD","tv.config.supportDolbyTVATMOS":false,""" +
        """"tv.model.soundModeType":"Dolby Atmos"}}""") shouldBe
        Map("hdr10" -> true, "hlg" -> true, "hdr10plus" -> false, "dolbyvision" -> true, "atmos" -> true,
          "uhd" -> true)
      WebOSDisplay.fromConfigs("""{"returnValue":true,"configs":{"tv.model.supportHDR":false,""" +
        """"tv.hw.panelResolution":"FHD"}}""") shouldBe
        Map("hdr10" -> false, "hlg" -> false, "hdr10plus" -> false, "uhd" -> false)
      WebOSDisplay.fromConfigs("""{"returnValue":false,"errorText":"Denied method call"}""") shouldBe Map.empty
      WebOSDisplay.fromConfigs("") shouldBe Map.empty
    }
    "be read as a 2017 LG answers, its partial reply taken (webOS 3.5, OLED55E7P)" in {
      WebOSDisplay.fromConfigs("""{"missingConfigs":["tv.model.soundModeType"],"subscribed":false,"returnValue":true,""" +
        """"configs":{"tv.config.supportDolbyHDRContents":true,"tv.model.supportHDR":true,"tv.hw.panelResolution":"UD",""" +
        """"tv.config.supportDolbyTVATMOS":true},"errorCode":-2,"errorText":"Partial configs found"}""") shouldBe
        Map("hdr10" -> true, "hlg" -> true, "hdr10plus" -> false, "dolbyvision" -> true, "atmos" -> true, "uhd" -> true)
    }
  }
}
