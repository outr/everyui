package everyui.render

import everyui.text.TextRun
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The subtitles EveryUI draws on the web where the browser's own would not be seen (a TV's player draws the picture
  * on a plane under the page): where it draws them and the modes the tracks are given (CueDrawing), a cue's words
  * (CueText) and its place (CuePlace). */
class WebCuesSpec extends AnyWordSpec with Matchers {
  "Where EveryUI draws the cues" should {
    "be a TV's host and an engine that squashes what lies over a video, unless forced" in {
      CueDrawing.drawn(None, tvHost = true, squashes = false) shouldBe true
      CueDrawing.drawn(None, tvHost = false, squashes = true) shouldBe true
      CueDrawing.drawn(None, tvHost = false, squashes = false) shouldBe false
      CueDrawing.drawn(Some(true), tvHost = false, squashes = false) shouldBe true
      CueDrawing.drawn(Some(false), tvHost = true, squashes = true) shouldBe false
    }
    "take webOS 3.5's Chromium 38 and webOS 4's 53 for engines that squash, and today's not" in {
      val lg38 = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.122 " +
        "Safari/537.36 WebAppManager"
      CueDrawing.drawn(None, tvHost = false, TvEngine.squashesOverVideo(lg38)) shouldBe true
      CueDrawing.drawn(None, tvHost = false, TvEngine.squashesOverVideo("Chrome/53.0.2785.34")) shouldBe true
      CueDrawing.drawn(None, tvHost = false, TvEngine.squashesOverVideo("Chrome/131.0.0.0")) shouldBe false
    }
    "give the chosen track `hidden` where it draws, `showing` where the browser does, and any other `disabled`" in {
      CueDrawing.mode(chosen = true, drawn = true) shouldBe "hidden"
      CueDrawing.mode(chosen = true, drawn = false) shouldBe "showing"
      CueDrawing.mode(chosen = false, drawn = true) shouldBe "disabled"
      CueDrawing.mode(chosen = false, drawn = false) shouldBe "disabled"
    }
  }

  "A cue's words" should {
    "keep plain words and their line breaks" in {
      CueText.runs("Hello there\nGeneral Kenobi") shouldBe List(TextRun("Hello there\nGeneral Kenobi"))
      CueText.runs("one\r\ntwo") shouldBe List(TextRun("one\ntwo"))
    }
    "style italics, bold and underlines, nested" in {
      CueText.runs("a <i>b <b>c</b></i> <u>d</u>") shouldBe List(TextRun("a "), TextRun("b ", italic = true),
        TextRun("c", italic = true, weight = 700), TextRun(" "), TextRun("d", underline = true))
    }
    "keep a voice's, a class's and a language's words, and their colours" in {
      CueText.runs("<v Roger Bingham>We are in New York City</v>") shouldBe List(TextRun("We are in New York City"))
      CueText.plain("<v.loud Esme>It's <lang en-GB>colour</lang></v>") shouldBe "It's colour"
      CueText.runs("<c.yellow>Warning</c> now") shouldBe List(TextRun("Warning", color = "#ffff00"), TextRun(" now"))
      CueText.runs("<font color=\"#FF0000\">red</font>") shouldBe List(TextRun("red", color = "#ff0000"))
    }
    "drop timestamps, unknown tags and a ruby's reading" in {
      CueText.plain("Never <00:00:01.500>drink <00:00:02.000>liquid") shouldBe "Never drink liquid"
      CueText.plain("<ruby>漢<rt>kan</rt></ruby>字") shouldBe "漢字"
      CueText.plain("<blink>odd</blink> tag") shouldBe "odd tag"
    }
    "read entities, and leave a stray ampersand or bracket as it is" in {
      CueText.plain("Tom &amp; Jerry &lt;3 &gt; &#65;&#x42; &nbsp;") shouldBe "Tom & Jerry <3 > AB  "
      CueText.plain("R&D at 5 < 6") shouldBe "R&D at 5 < 6"
    }
    "take out SubRip's SubStation overrides, and say where one puts the cue" in {
      CueText.plain("{\\an8}Up top") shouldBe "Up top"
      CueText.anchor("{\\an8}Up top") shouldBe 8
      CueText.anchor("Down below") shouldBe 2
    }
  }

  "A cue's place" should {
    "be at the foot, centred, within the margin, where it says nothing (today's engines and Chromium 38's alike)" in {
      val today = CuePlace.of(None, snapToLines = true, "", None, "auto", 100.0, "center")
      today shouldBe CuePlace()
      today.ordinary shouldBe true
      // an older engine's cue: line -1, position 50, align `middle`
      CuePlace.of(Some(-1.0), snapToLines = true, "", Some(50.0), "", 100.0, "middle") shouldBe CuePlace()
    }
    "count lines from the top, or up from the foot" in {
      CuePlace.of(Some(0.0), snapToLines = true, "", None, "", 100.0, "center") shouldBe
        CuePlace(edge = CuePlace.Top, lines = 0)
      CuePlace.of(Some(-3.0), snapToLines = true, "", None, "", 100.0, "center") shouldBe CuePlace(lines = 2)
    }
    "put a line in percent where it is anchored" in {
      CuePlace.of(Some(10.0), snapToLines = false, "start", None, "", 100.0, "center") shouldBe CuePlace(percent = 10.0)
      CuePlace.of(Some(50.0), snapToLines = false, "center", None, "", 100.0, "center") shouldBe
        CuePlace(percent = 50.0, shift = 50.0)
      CuePlace.of(Some(90.0), snapToLines = false, "end", None, "", 100.0, "center") shouldBe
        CuePlace(percent = 90.0, shift = 100.0)
    }
    "place a narrower box by its position and alignment, kept within the margin" in {
      CuePlace.of(None, snapToLines = true, "", Some(20.0), "line-left", 30.0, "start") shouldBe
        CuePlace(left = 20.0, width = 30.0, align = "left")
      CuePlace.of(None, snapToLines = true, "", Some(80.0), "auto", 40.0, "end") shouldBe
        CuePlace(left = 40.0, width = 40.0, align = "right")
      CuePlace.of(None, snapToLines = true, "", Some(50.0), "center", 40.0, "center") shouldBe
        CuePlace(left = 30.0, width = 40.0)
      CuePlace.of(None, snapToLines = true, "", Some(0.0), "line-left", 50.0, "left") shouldBe
        CuePlace(left = 5.0, width = 50.0, align = "left")
    }
    "go where SubRip's override says" in {
      CuePlace.of(None, snapToLines = true, "", None, "", 100.0, "center", keypad = 8) shouldBe
        CuePlace(edge = CuePlace.Top)
      CuePlace.of(None, snapToLines = true, "", None, "", 100.0, "center", keypad = 5) shouldBe
        CuePlace(percent = 50.0, shift = 50.0)
    }
  }
}
