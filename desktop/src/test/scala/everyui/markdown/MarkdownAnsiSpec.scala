package everyui.markdown

import everyui.component.{Component, Container, RichText, ScrollContainer, TextView}
import everyui.kit.Highlight
import everyui.log.{AnsiStyle, LogTheme}
import everyui.text.TextRun
import everyui.theme.{Palette, Theme}
import everyui.{FxSpec, ui}

/** A coding agent's output in a Markdown code block: drawn in the theme's terminal colours where it holds escapes or
  * is given as "ansi" or "terminal", following the theme as it changes, its label the language it was given and never
  * "ansi" where that was inferred, escapes winning over highlighting, an OSC 8 link kept where it is safe; copied, made
  * plain text and made HTML without its escapes; and a block with no escapes drawn, copied and written as before. */
class MarkdownAnsiSpec extends FxSpec {
  private val E = "\u001b["
  private val output: String = E + "2m12:00:01" + E + "0m " + E + "31mERROR" + E + "0m build failed\n" +
    E + "2m12:00:02" + E + "0m " + E + "32mok" + E + "0m " + E + "1mtests" + E + "22m passed " +
    E + "38;5;208m(3 slow)" + E + "0m " + E + "38;2;120;80;220mcache" + E + "0m " +
    "\u001b]8;;https://example.com/run/42\u001b\\run 42\u001b]8;;\u001b\\" + E + "2K" + E + "1G\n"
  private val words: String = "12:00:01 ERROR build failed\n12:00:02 ok tests passed (3 slow) cache run 42"

  private def onScreen[T](view: MarkdownView)(test: => T): T = {
    val before = fx(Theme.palette())
    fx {
      ui.children.clear()
      ui.children += view
    }
    try test finally fx {
      ui.children.clear()
      Theme.palette @= before
    }
  }

  private def all(c: Component): List[Component] = c :: (c match {
    case s: ScrollContainer => s.children().toList.flatMap(all) ++ all(s.content)
    case k: Container => k.children().toList.flatMap(all)
    case _ => Nil
  })

  private def block(view: MarkdownView): CodeBlockView = fx(view.children().collectFirst {
    case c: CodeBlockView => c
  }.get)

  private def runsOf(c: CodeBlockView): List[TextRun] = fx(all(c).collectFirst { case r: RichText => r.runs() }.get)

  // the label over the code, where it shows
  private def label(c: CodeBlockView): Option[String] =
    fx(c.children().collectFirst { case t: TextView => t }.filter(_.visible()).map(_.content()))

  private def runOf(runs: List[TextRun], w: String): TextRun = runs.find(_.text.contains(w)).get

  "A Markdown code block of a terminal's output" should {
    "be drawn in the terminal colours where it is given as ansi, its label ansi" in {
      val view = fx(MarkdownView("```ansi\n" + output + "```", 600.0))
      onScreen(view) {
        fx(Theme.palette @= Palette.dark)
        val code = block(view)
        val runs = runsOf(code)
        runs.map(_.text).mkString shouldBe words
        val theme = fx(LogTheme.of(Palette.dark).copy(background = MarkdownView.codeBackground()))
        runOf(runs, "ERROR").color shouldBe Component.css(theme.letters(AnsiStyle(fg = 1)))
        runOf(runs, "ok").color shouldBe Component.css(theme.letters(AnsiStyle(fg = 2)))
        runOf(runs, "12:00:01").color shouldBe Component.css(theme.letters(AnsiStyle(dim = true)))
        runOf(runs, "tests").weight shouldBe 700
        runOf(runs, "(3 slow)").color shouldBe Component.css(theme.letters(AnsiStyle(fg = 208)))
        runOf(runs, "cache").color shouldBe Component.css(theme.letters(AnsiStyle(fg = AnsiStyle.rgb(120, 80, 220))))
        runOf(runs, "run 42").href shouldBe "https://example.com/run/42"
        runOf(runs, "run 42").underline shouldBe true
        runOf(runs, "run 42").color shouldBe fx(MarkdownView.link().toHex)
        label(code) shouldBe Some("ansi")
        fx(code.copied) shouldBe words
      }
    }
    "follow the theme as it changes" in {
      val view = fx(MarkdownView("```terminal\n" + output + "```", 600.0))
      onScreen(view) {
        fx(Theme.palette @= Palette.dark)
        val code = block(view)
        val onDark = runOf(runsOf(code), "ERROR").color
        fx(Theme.palette @= Palette.light)
        val onLight = runOf(runsOf(code), "ERROR").color
        val theme = fx(LogTheme.of(Palette.light).copy(background = MarkdownView.codeBackground()))
        onLight shouldBe Component.css(theme.letters(AnsiStyle(fg = 1)))
        onLight should not be onDark
      }
    }
    "be inferred from its escapes, its label the language it was given or none, and never ansi" in {
      val bare = fx(MarkdownView("```\n" + output + "```", 600.0))
      onScreen(bare) {
        val code = block(bare)
        code.language shouldBe ""
        label(code) shouldBe None
        runsOf(code).map(_.text).mkString shouldBe words
        runOf(runsOf(code), "ERROR").color should not be ""
      }
      // escapes win over highlighting: "failed" is no keyword, and nothing is a shell's colour
      val bash = fx(MarkdownView("```bash\n" + output + "```", 600.0))
      onScreen(bash) {
        val code = block(bash)
        label(code) shouldBe Some("bash")
        val runs = runsOf(code)
        runs.map(_.text).mkString shouldBe words
        runs.exists(_.text.contains("\u001b")) shouldBe false
        fx(code.copied) shouldBe words
      }
    }
    "drop a link that is not safe to follow, keeping its words" in {
      val runs = fx(CodeBlockView.runs("\u001b]8;;javascript:alert(1)\u0007click\u001b]8;;\u0007 " + E + "31mx", ""))
      runs.map(_.text).mkString shouldBe "click x"
      runs.forall(_.href.isEmpty) shouldBe true
    }
    "draw a sequence cut off as an answer streams in as nothing, then whole once it has come" in {
      val view = fx(MarkdownView("", 600.0))
      onScreen(view) {
        fx(view.content @= "```\n" + E + "31mERROR" + E)
        val code = block(view)
        runsOf(code).map(_.text).mkString shouldBe "ERROR"
        fx(view.content @= "```\n" + E + "31mERROR" + E + "0m done")
        block(view) should be theSameInstanceAs code
        runsOf(code).map(_.text).mkString shouldBe "ERROR done"
        runOf(runsOf(code), " done").color shouldBe ""
      }
    }
    "be copied as plain text and as HTML without its escapes" in {
      MarkdownPlain("Output:\n\n```\n" + output + "```\n\nDone.") shouldBe "Output:\n\n" + words + "\n\nDone."
      MarkdownHtml("```ansi\n" + output + "```") shouldBe "<pre><code>" + MarkdownHtml.escape(words) + "</code></pre>"
      MarkdownPlain("```console\n" + output + "```") shouldBe words
      MarkdownHtml("```\n" + output + "```").contains("\u001b") shouldBe false
    }
  }

  "A Markdown code block with no escapes" should {
    "be drawn, labelled, copied and written as before" in {
      val source = "val x = List(1, 2)\nif (x.isEmpty) println(\"none\")\t// tab\n"
      val view = fx(MarkdownView("```scala\n" + source + "```", 600.0))
      onScreen(view) {
        val code = block(view)
        fx(code.code()) shouldBe source.stripSuffix("\n")
        runsOf(code) shouldBe fx(Highlight.runs(code.code(), "scala"))
        label(code) shouldBe Some("scala")
        fx(code.copied) shouldBe fx(code.code())
      }
      MarkdownPlain("```scala\n" + source + "```") shouldBe source.stripSuffix("\n")
      MarkdownHtml("```scala\n" + source + "```") shouldBe
        "<pre><code>" + MarkdownHtml.escape(source.stripSuffix("\n")) + "</code></pre>"
      // a console's words still coloured as a shell's
      val console = "$ echo hi # say hi"
      fx(CodeBlockView.runs(console, "console")) shouldBe fx(Highlight.runs(console, "console"))
      CodeBlockView.plain("console", console) shouldBe console
    }
  }
}
