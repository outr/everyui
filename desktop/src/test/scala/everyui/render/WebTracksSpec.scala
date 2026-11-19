package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What the web's player reads beside what the browser lists: an HLS master playlist's renditions, for a TV's player
  * that plays them without listing them (HlsRenditions), SubRip made WebVTT for a `<track>` (SubRipVtt), and a start
  * set again where the player dropped it (ResumeSeek). */
class WebTracksSpec extends AnyWordSpec with Matchers {
  private val master =
    """#EXTM3U
      |#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,URI="en/a.m3u8"
      |#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Français, stéréo",LANGUAGE="fr",DEFAULT=NO,URI="fr/a.m3u8"
      |#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="low",NAME="English",LANGUAGE="en",DEFAULT=YES,URI="en/low.m3u8"
      |#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",LANGUAGE="en",DEFAULT=NO,URI="en/s.m3u8"
      |#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Deutsch",LANGUAGE="de",URI="de/s.m3u8"
      |#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,GROUP-ID="cc",NAME="CC1",LANGUAGE="en",INSTREAM-ID="CC1"
      |#EXT-X-STREAM-INF:BANDWIDTH=900000,CODECS="avc1.640028,mp4a.40.2",AUDIO="aud",SUBTITLES="subs",CLOSED-CAPTIONS="cc"
      |v/index.m3u8
      |""".stripMargin

  "An HLS playlist's renditions" should {
    "be read in order, with their quoted attributes whole" in {
      val all = HlsRenditions.all(master)
      all.map(_.kind) shouldBe List("AUDIO", "AUDIO", "AUDIO", "SUBTITLES", "SUBTITLES", "CLOSED-CAPTIONS")
      all(1) shouldBe HlsRendition("AUDIO", "aud", "Français, stéréo", "fr", default = false, "fr/a.m3u8")
      all(5).uri shouldBe ""
    }
    "be those of the group the first variant names" in {
      HlsRenditions.of(master, "AUDIO").map(_.language) shouldBe List("en", "fr")
      HlsRenditions.of(master, "SUBTITLES").map(_.name) shouldBe List("English", "Deutsch")
      HlsRenditions.of(master.replace(",AUDIO=\"aud\"", ""), "AUDIO").map(_.group) shouldBe List("aud", "aud")
    }
    "tell a master playlist from a media playlist" in {
      HlsRenditions.master(master) shouldBe true
      HlsRenditions.master("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg0.ts\n") shouldBe false
    }
  }

  "A subtitle file" should {
    "be WebVTT as it is" in {
      SubRipVtt("WEBVTT\n\n00:00.000 --> 00:01.000\nHi\n") shouldBe Some("WEBVTT\n\n00:00.000 --> 00:01.000\nHi\n")
    }
    "be made WebVTT from SubRip" in {
      SubRipVtt("\uFEFF1\r\n00:00:01,000 --> 00:00:02,500\r\nHello, there\r\n\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\n42\r\n") shouldBe
        Some("WEBVTT\n\n00:00:01.000 --> 00:00:02.500\nHello, there\n\n00:00:03.000 --> 00:00:04.000\n42\n\n")
    }
    "be nothing where a browser cannot show it" in {
      SubRipVtt("[Script Info]\nTitle: x\n[Events]\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hi --> there") shouldBe None
      SubRipVtt("") shouldBe None
    }
  }

  "A start" should {
    "be set again where the player dropped it, until it plays on from there" in {
      val r = new ResumeSeek(600.0)
      r.position(0.0) shouldBe 600.0
      r.heard("loadedmetadata", 600.0, seeking = false) shouldBe None
      // the TV's player dropped the early time
      r.heard("canplay", 0.0, seeking = false) shouldBe Some(600.0)
      r.heard("timeupdate", 600.0, seeking = true) shouldBe None
      r.done shouldBe false
      r.heard("playing", 598.5, seeking = false) shouldBe None
      r.done shouldBe true
      r.position(612.0) shouldBe 612.0
    }
    "be given up after its tries" in {
      val r = new ResumeSeek(300.0)
      (1 to ResumeSeek.tries).map(_ => r.heard("timeupdate", 0.0, seeking = false)).flatten.size shouldBe ResumeSeek.tries
      r.heard("timeupdate", 0.0, seeking = false) shouldBe None
      r.done shouldBe true
      r.position(1.0) shouldBe 1.0
    }
    "end at a seek of the viewer's" in {
      val r = new ResumeSeek(300.0)
      r.cancel()
      r.heard("canplay", 0.0, seeking = false) shouldBe None
      r.position(5.0) shouldBe 5.0
    }
    "be nothing at 0" in {
      new ResumeSeek(0.0).done shouldBe true
    }
  }
}
