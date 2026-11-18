package everyui.markdown

import everyui.text.TextRun
import everyui.theme.Tone
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Where a passage lies in a document's words (`Passages`): its first occurrence, whatever the spaces and line breaks
  * and with or without Markdown's marks, across lines, list items and cells, never in code, and nowhere where it is
  * not; and the runs marked, cut where it starts and ends. */
class PassagesSpec extends AnyWordSpec with Matchers {
  private def units(markdown: String): Vector[String] = Passages.units(Markdown.parse(markdown))
  // the words of `markdown` marked by `passage`, a unit's at a time
  private def marked(markdown: String, passage: String): Vector[String] = {
    val all = units(markdown)
    Passages.find(all, Vector(Passage(passage))).zip(all).flatMap { case (spans, words) =>
      spans.map(s => words.substring(s.from, s.to))
    }
  }

  "A passage" should {
    "be found where its words first are" in {
      marked("Morning. Shall I start? Then shall I start?", "shall I start?") shouldBe Vector("shall I start?")
      Passages.at("abc Shall I start? Shall I start?", "Shall I start?") shouldBe Some((4, 18))
    }
    "be found whatever the spaces and line breaks between its words" in {
      marked("Shall  I\nstart with group 1?", "Shall I start   with\ngroup 1?") shouldBe Vector("Shall  I", "start with group 1?")
      Passages.at("Shall I start", "  Shall\tI start \n") shouldBe Some((0, 13))
    }
    "be found with or without the marks around its words" in {
      marked("Shall **I start** with `group 1`?", "Shall I start with group 1?") shouldBe
        Vector("Shall I start with group 1?")
      marked("Shall I start with group 1?", "**Shall** I _start_ with ~~group~~ `1`?") shouldBe
        Vector("Shall I start with group 1?")
      marked("See the plan first.", "See [the plan](demo-file:plan.md) first.") shouldBe Vector("See the plan first.")
      marked("Keep snake_case names?", "Keep snake_case names?") shouldBe Vector("Keep snake_case names?")
      marked("Keep 2 * 3 as is?", "Keep 2 * 3 as is?") shouldBe Vector("Keep 2 * 3 as is?")
      marked("- First\n- Do you want it?", "- Do you want it?") shouldBe Vector("Do you want it?")
    }
    "be found nowhere where its words are not" in {
      marked("Shall I start?", "Shall we start?") shouldBe Vector.empty
      marked("Shall I start?", "") shouldBe Vector.empty
      marked("Shall I start?", "** **") shouldBe Vector.empty
      Passages.found("Shall I start?", Vector(Passage("Shall we start?"), Passage("I start"))) shouldBe
        Vector(false, true)
    }
    "never be found in code" in {
      marked("```\nShall I start?\n```\n\nThen what?", "Shall I start?") shouldBe Vector.empty
      // the code passed over, the words after it found
      marked("```\nShall I start?\n```\n\nShall I start?", "Shall I start?") shouldBe Vector("Shall I start?")
      marked("    Shall I start?\n\nNo.", "Shall I start?") shouldBe Vector.empty
    }
    "run across lines, list items and table cells, each part its own" in {
      marked("- one\n- two", "one two") shouldBe Vector("one", "two")
      marked("| a | b |\n| --- | --- |\n| Owner | **Should it go?** |", "**Should it go?**") shouldBe
        Vector("Should it go?")
      marked("First para ends.\n\nSecond starts here.", "ends. Second") shouldBe Vector("ends.", "Second")
      marked("> Quoted question?", "Quoted question?") shouldBe Vector("Quoted question?")
    }
  }

  "Marked runs" should {
    "be cut where a passage starts and ends, across runs, the code's own wash given up" in {
      val runs = List(TextRun("Shall "), TextRun("I start", weight = 700), TextRun(" with "),
        TextRun("group 1", family = "Roboto Mono", background = "#333333"), TextRun("? Then."))
      val out = Passages.marked(runs, Vector(MarkSpan(6, 27, 0, Tone.Warning)))
      out.map(_.text) shouldBe List("Shall ", "I start", " with ", "group 1", "?", " Then.")
      out.map(_.mark.nonEmpty) shouldBe List(false, true, true, true, true, false)
      out(1).weight shouldBe 700
      out(3).background shouldBe ""
      out(3).family shouldBe "Roboto Mono"
      out(1).markLine should not be ""
      // none, and they are as they were
      Passages.marked(runs, Vector.empty) should be theSameInstanceAs runs
    }
  }
}
