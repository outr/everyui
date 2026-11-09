package everyui.log

import everyui.Color
import everyui.component.Component
import everyui.text.TextRun
import everyui.theme.Palette
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A terminal's text made runs and plain words outside a log view (`AnsiText`): the sixteen colours, the 256 and
  * colours of their own drawn as the theme draws them, readable on a dark and a light ground; bold, dim, italic,
  * underline and strike; a reset; the style carried from line to line; an OSC 8 link kept; the cursor's moves,
  * erasing and the other OSC sequences dropped; a sequence cut off at the end dropped; and words with no escapes as
  * they are. */
class AnsiTextSpec extends AnyWordSpec with Matchers {
  private val E = "\u001b["
  private val dark = LogTheme.of(Palette.dark)
  private val light = LogTheme.of(Palette.light)

  private def words(runs: List[TextRun]): String = runs.map(_.text).mkString

  // the run `w` is in
  private def runOf(runs: List[TextRun], w: String): TextRun = {
    val r = runs.find(_.text.contains(w))
    r.isDefined shouldBe true
    r.get
  }

  private def colourOf(runs: List[TextRun], w: String): Color = Color.fromHex(runOf(runs, w).color)

  private def readable(c: Color, theme: LogTheme): Boolean =
    LogTheme.contrast(LogTheme.luminance(c), LogTheme.luminance(theme.background)) >= 4.5

  "AnsiText.runs" should {
    "colour the sixteen, the 256 and colours of their own as the theme does, readable on either ground" in {
      val text = E + "31mERROR" + E + "0m " + E + "32mok" + E + "0m " + E + "93mwarn" + E + "0m " + E + "38;5;208m" +
        "orange" + E + "0m " + E + "38;2;120;80;220mviolet" + E + "0m " + E + "30mblack" + E + "0m " + E + "97mwhite" +
        E + "0m"
      List(dark, light).foreach { theme =>
        val runs = AnsiText.runs(text, theme)
        words(runs) shouldBe "ERROR ok warn orange violet black white"
        runOf(runs, "ERROR").color shouldBe Component.css(theme.letters(AnsiStyle(fg = 1)))
        runOf(runs, "ok").color shouldBe Component.css(theme.letters(AnsiStyle(fg = 2)))
        runOf(runs, "warn").color shouldBe Component.css(theme.letters(AnsiStyle(fg = 11)))
        runOf(runs, "orange").color shouldBe Component.css(theme.letters(AnsiStyle(fg = 208)))
        runOf(runs, "violet").color shouldBe Component.css(theme.letters(AnsiStyle(fg = AnsiStyle.rgb(120, 80, 220))))
        List("ERROR", "ok", "warn", "orange", "violet", "black", "white").foreach { w =>
          withClue(w + " on " + theme.background.toHex) {
            readable(colourOf(runs, w), theme) shouldBe true
          }
        }
        runOf(runs, " ").color shouldBe ""
      }
      // the same red is lighter on the dark ground than on the light one
      LogTheme.luminance(colourOf(AnsiText.runs(E + "31mx", dark), "x")) should be >
        LogTheme.luminance(colourOf(AnsiText.runs(E + "31mx", light), "x"))
    }
    "draw bold, dim, italic, underline and strike, and a ground of its own behind its words" in {
      val runs = AnsiText.runs(E + "1mbold" + E + "22m " + E + "2mdim" + E + "22m " + E + "3mitalic" + E + "23m " +
        E + "4munder" + E + "24m " + E + "9mstruck" + E + "29m " + E + "41;97mbanner" + E + "0m", dark, grounds = true)
      words(runs) shouldBe "bold dim italic under struck banner"
      runOf(runs, "bold").weight shouldBe 700
      runOf(runs, "dim").color shouldBe Component.css(dark.foreground.blend(dark.background, 0.45))
      runOf(runs, "dim").weight shouldBe 0
      runOf(runs, "italic").italic shouldBe true
      runOf(runs, "under").underline shouldBe true
      runOf(runs, "struck").strike shouldBe true
      runOf(runs, "banner").background shouldBe Component.css(dark.colour(1))
      runOf(runs, "bold").background shouldBe ""
    }
    "draw a reversed run as unreversed, and no grounds, where the platform draws no ground behind a run" in {
      val text = E + "7mreversed" + E + "27m " + E + "41;97mbanner" + E + "0m " + E + "7;32mgreen" + E + "0m"
      val drawn = AnsiText.runs(text, light, grounds = true)
      runOf(drawn, "reversed").color shouldBe Component.css(light.background)
      runOf(drawn, "reversed").background shouldBe Component.css(light.foreground)
      val bare = AnsiText.runs(text, light, grounds = false)
      words(bare) shouldBe "reversed banner green"
      runOf(bare, "reversed") shouldBe TextRun("reversed ")
      runOf(bare, "banner").color shouldBe Component.css(light.letters(AnsiStyle(fg = 15)))
      runOf(bare, "green").color shouldBe Component.css(light.letters(AnsiStyle(fg = 2)))
      bare.forall(_.background.isEmpty) shouldBe true
      List("banner", "green").foreach(w => readable(colourOf(bare, w), light) shouldBe true)
    }
    "go back to the paragraph's own style at a reset, and carry a style from line to line until one" in {
      val runs = AnsiText.runs(E + "31mred\nstill red" + E + "0m plain\n" + E + "1;32mgreen" + E + "m after", dark)
      words(runs) shouldBe "red\nstill red plain\ngreen after"
      runOf(runs, "still red").color shouldBe runOf(runs, "red").color
      runOf(runs, " plain").color shouldBe ""
      runOf(runs, " plain").weight shouldBe 0
      runOf(runs, "green").weight shouldBe 700
      runOf(runs, " after") shouldBe TextRun(" after")
      // each line's end is a plain run's, joined to the plain words beside it
      runs.filter(_.text.contains("\n")).foreach(r => r.color shouldBe "")
    }
    "keep an OSC 8 link's words and where it goes, by BEL or by ST" in {
      val runs = AnsiText.runs("see \u001b]8;;https://example.com/run/42\u0007the run\u001b]8;;\u0007 and " +
        "\u001b]8;id=1;https://example.com/docs\u001b\\docs\u001b]8;;\u001b\\.", light)
      words(runs) shouldBe "see the run and docs."
      runOf(runs, "the run").href shouldBe "https://example.com/run/42"
      runOf(runs, "docs").href shouldBe "https://example.com/docs"
      runOf(runs, " and ").href shouldBe ""
    }
    "drop the cursor's moves, erasing, the other OSC sequences and private modes" in {
      val text = "\u001b]0;agent: working\u0007" + E + "2K" + E + "1G" + E + "?25lStep 1" + E + "3A" + E + "?25h" +
        E + "J done\u001b]133;A\u001b\\\u001b7\u001b8"
      words(AnsiText.runs(text, dark)) shouldBe "Step 1 done"
      AnsiText.strip(text) shouldBe "Step 1 done"
    }
    "drop a sequence cut off at the end, never showing it" in {
      List(E, E + "3", E + "38;5;20", "\u001b", "\u001b]8;;https://example.com/x", "\u001b]0;title").foreach { cut =>
        withClue(cut.drop(1)) {
          words(AnsiText.runs("ok " + E + "32mdone" + E + "0m " + cut, dark)) shouldBe "ok done "
          AnsiText.strip("line\nok " + cut) shouldBe "line\nok "
        }
      }
    }
    "leave words with no escapes as they are, a plain run" in {
      AnsiText.runs("plain words\n  indented\n", dark) shouldBe List(TextRun("plain words\n  indented\n"))
      AnsiText.runs("", dark) shouldBe List(TextRun(""))
    }
  }

  "AnsiText.strip" should {
    "give the words alone, a line to a line, a progress bar at its last state" in {
      val log = E + "2m12:00:01" + E + "0m " + E + "31;1mERROR" + E + "0m boom\n" + E + "32mok" + E + "0m\n"
      AnsiText.strip(log) shouldBe
        "12:00:01 ERROR boom\nok\n"
      AnsiText.strip("10%\r50%\r100%\ndone") shouldBe "100%\ndone"
      AnsiText.strip("no escapes at all") shouldBe "no escapes at all"
    }
  }

  "AnsiText.escaped" should {
    "find a control sequence or an OSC, and nothing in plain words" in {
      AnsiText.escaped("a " + E + "0m") shouldBe true
      AnsiText.escaped("\u001b]8;;x\u0007") shouldBe true
      AnsiText.escaped("plain [0m words") shouldBe false
    }
  }
}
