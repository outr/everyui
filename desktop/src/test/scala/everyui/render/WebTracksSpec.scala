package everyui.render

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** What the web's player reads beside what the browser lists: an HLS master playlist's renditions, for a TV's player
  * that plays them without listing them (HlsRenditions), SubRip made WebVTT for a `<track>` (SubRipVtt), a place landed on
  * where the player dropped it (ResumeSeek: a start, a sound track chosen, a seek), and a playlist's WebVTT
  * subtitles as one file (HlsVtt). */
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
    "be set again where the player dropped it, until it has played on from there" in {
      val r = new ResumeSeek(600.0)
      r.position(0.0) shouldBe 600.0
      r.heard("loadedmetadata", 600.0, seeking = false) shouldBe None
      // the TV's player dropped the early time
      r.heard("canplay", 0.0, seeking = false) shouldBe Some(600.0)
      r.heard("timeupdate", 600.0, seeking = true) shouldBe None
      r.heard("playing", 598.5, seeking = false) shouldBe None
      r.done shouldBe false
      r.position(598.5) shouldBe 600.0
      r.heard("timeupdate", 600.6, seeking = false) shouldBe None
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
    "be given up when cancelled" in {
      val r = new ResumeSeek(300.0)
      r.cancel()
      r.heard("canplay", 0.0, seeking = false) shouldBe None
      r.position(5.0) shouldBe 5.0
    }
    "be nothing at 0" in {
      new ResumeSeek(0.0).done shouldBe true
    }
  }

  "A sound track chosen on a TV's own player" should {
    "land where it was when the player starts again from 0 (webOS 3's HLS, half a second on)" in {
      val r = new ResumeSeek(20.4)
      r.heard("timeupdate", 20.6, seeking = false) shouldBe None
      r.heard("timeupdate", 20.9, seeking = false) shouldBe None
      r.done shouldBe false
      r.heard("timeupdate", 0.1, seeking = false) shouldBe Some(20.4)
      r.position(0.1) shouldBe 20.4
      r.heard("canplay", 20.4, seeking = false) shouldBe None
      r.heard("timeupdate", 22.5, seeking = false) shouldBe None
      r.done shouldBe true
    }
    "land where it was when the player stalls and comes back a little before (webOS 3's MP4)" in {
      val r = new ResumeSeek(20.0)
      r.heard("playing", 17.4, seeking = false) shouldBe Some(20.0)
    }
  }

  "A seek" should {
    "land at its segment's start, and play on from there" in {
      val r = new ResumeSeek(30.0)
      r.heard("canplay", 28.0, seeking = false) shouldBe None
      r.position(28.4) shouldBe 30.0
      r.heard("timeupdate", 30.1, seeking = false) shouldBe None
      r.done shouldBe true
      r.position(30.1) shouldBe 30.1
    }
    "be set again where the player springs back from it (webOS 3's MP4)" in {
      val r = new ResumeSeek(30.0)
      r.heard("canplay", 30.0, seeking = false) shouldBe None
      r.heard("timeupdate", 30.3, seeking = false) shouldBe None
      r.heard("timeupdate", 25.6, seeking = false) shouldBe Some(30.0)
      r.heard("canplay", 30.0, seeking = false) shouldBe None
      r.heard("timeupdate", 32.1, seeking = false) shouldBe None
      r.done shouldBe true
    }
    "leave a player somewhere else altogether alone" in {
      val r = new ResumeSeek(30.0)
      r.heard("timeupdate", 95.0, seeking = false) shouldBe None
      r.done shouldBe true
    }
  }

  "A playlist's WebVTT subtitles" should {
    val media =
      """#EXTM3U
        |#EXT-X-TARGETDURATION:4
        |#EXT-X-PLAYLIST-TYPE:VOD
        |#EXTINF:4.0,
        |s0.vtt
        |#EXTINF:4.0,
        |../s/s1.vtt?t=1
        |#EXT-X-ENDLIST
        |""".stripMargin
    "name their segments in order, and say whether the playlist is whole" in {
      HlsVtt.segments(media) shouldBe List("s0.vtt", "../s/s1.vtt?t=1")
      HlsVtt.ended(media) shouldBe true
      HlsVtt.ended(media.replace("#EXT-X-ENDLIST", "")) shouldBe false
    }
    "be one WebVTT file of every segment's cues, each once" in {
      val s0 = "WEBVTT\nX-TIMESTAMP-MAP=MPEGTS:126000,LOCAL:00:00:00.000\n\n00:00:01.000 --> 00:00:02.000\nOne\n\n" +
        "00:00:03.500 --> 00:00:04.500\nOver the edge\n"
      val s1 = "WEBVTT\r\nX-TIMESTAMP-MAP=MPEGTS:126000,LOCAL:00:00:00.000\r\n\r\nNOTE a note\r\n\r\n" +
        "00:00:03.500 --> 00:00:04.500\r\nOver the edge\r\n\r\nc2\r\n00:00:05.000 --> 00:00:06.000\r\nTwo\r\n"
      HlsVtt.join(List(s0, "", s1)) shouldBe "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nOne\n\n" +
        "00:00:03.500 --> 00:00:04.500\nOver the edge\n\nc2\n00:00:05.000 --> 00:00:06.000\nTwo\n"
      HlsVtt.join(Nil) shouldBe "WEBVTT\n\n"
    }
  }
}
