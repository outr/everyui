package everyui.markdown

import everyui.component.{Container, RichText}
import everyui.focus.Focus
import everyui.kit.Highlight
import everyui.render.RichFlow
import everyui.text.TextRun
import everyui.{FxSpec, ui}

/** Code drawn with as few runs as its colours need, and drawn again as it grows by its new end alone: tokens of one
  * colour with only spaces between are one run (`Highlight`), runs drawn alike are one (`TextRun.merged`), and a code
  * block that grows as an answer streams in keeps the nodes of the lines it had (`RichFlow`, the web's spans alike). */
class CodeRunsSpec extends FxSpec {
  private def onScreen[T](view: MarkdownView)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += view
    }
    try test finally fx {
      ui.children.clear()
      view.dispose()
      Focus.focused @= None
    }
  }

  private def code(view: MarkdownView): RichText = view.children().collectFirst { case c: CodeBlockView => c }.get
    .children().collectFirst { case s: everyui.component.ScrollContainer => s }.get
    .content.children().collectFirst { case r: RichText => r }.get

  private def nodes(r: RichText): Vector[javafx.scene.Node] = {
    val flow = r.node.fx.asInstanceOf[RichFlow]
    (0 until flow.getChildren.size).toVector.map(flow.getChildren.get(_))
  }

  "Code" should {
    "be one run for tokens of one colour with only spaces between, and the same words" in {
      val source = "sealed case class Point(x: Int)\n// one\n// two\nval s = \"a\" \"b\"\n"
      val runs = fx(Highlight.runs(source, "scala"))
      runs.map(_.text).mkString shouldBe source
      // the keywords together, the comment over its two lines, the two strings and the space between them
      runs.filter(_.text == "sealed case class") should have size 1
      runs.filter(_.text == "// one\n// two") should have size 1
      runs.filter(_.text == "\"a\" \"b\"") should have size 1
      // no two runs one after the other drawn alike
      runs.zip(runs.drop(1)).exists(p => p._1.copy(text = "") == p._2.copy(text = "")) shouldBe false
    }

    "join runs drawn alike, and leave runs that differ as they are" in {
      TextRun.merged(List(TextRun("a", color = "#ff0000"), TextRun("b", color = "#ff0000"), TextRun("c"),
        TextRun("d"), TextRun("e", weight = 700))) shouldBe List(TextRun("ab", color = "#ff0000"), TextRun("cd"),
        TextRun("e", weight = 700))
      // pictures are never joined, each its own square
      val emoji = TextRun(":a:", image = "a.png")
      TextRun.merged(List(emoji, emoji)) shouldBe List(emoji, emoji)
      val alone = List(TextRun("x"), TextRun("y", italic = true))
      TextRun.merged(alone) should be theSameInstanceAs alone
    }

    "keep the nodes of the lines it had as it grows, drawing only its new end" in {
      val lines = (1 to 12).map(i => "  def step" + i + "(x: Int): Int = x * " + i + " // twice")
      val view = fx(MarkdownView("```scala\n" + lines.take(6).mkString("\n") + "\n```", 700.0))
      onScreen(view) {
        eventually((code(view).size.height()) > 0.0) shouldBe true
        val before = fx(nodes(code(view)))
        before.size should be > 20
        // a line more, as an answer streams in: every node but the last kept, the same node
        (7 to 12).foreach { n =>
          fx(view.content @= "```scala\n" + lines.take(n).mkString("\n") + "\n```")
        }
        val after = fx(nodes(code(view)))
        after.size should be > before.size
        before.dropRight(1).zip(after).forall(p => p._1 eq p._2) shouldBe true
        // drawn as the runs are, one node to a run that has words
        fx(code(view).runs().count(_.text.nonEmpty)) shouldBe after.size
        fx(after.collect { case t: javafx.scene.text.Text => t.getText }.mkString) shouldBe lines.mkString("\n")
      }
    }
  }
}
