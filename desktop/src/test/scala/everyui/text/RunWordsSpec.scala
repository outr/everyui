package everyui.text

import everyui.component.RichText
import everyui.focus.Focus
import everyui.render.RichFlow
import everyui.{FxSpec, ui}

/** Runs whose words alone change keep their nodes (`RichFlow`, the web's spans and text nodes alike): a line whose time
  * moves on each second, "9:42:13 AM · 12s" with its code span and link before it, writes its new words into the nodes
  * it has and makes none, while a run drawn otherwise than before is made again from there. */
class RunWordsSpec extends FxSpec {
  private def nodes(r: RichText): Vector[javafx.scene.Node] = {
    val flow = r.node.fx.asInstanceOf[RichFlow]
    (0 until flow.getChildren.size).toVector.map(flow.getChildren.get(_))
  }

  private def words(r: RichText): String =
    nodes(r).collect { case t: javafx.scene.text.Text => t.getText }.mkString

  private def line(seconds: Int, code: String = "sbt test"): List[TextRun] = List(TextRun("Ran "),
    TextRun(code, family = "Roboto Mono", background = "#33669922"), TextRun(" in "),
    TextRun("everyui", href = "https://example.com"), TextRun(" · 9:42:13 AM · " + seconds.toString + "s"))

  "Runs" should {
    "keep their nodes as their words alone change, and make again those drawn otherwise" in {
      val view = fx {
        val r = new RichText
        r.runs @= line(1)
        ui.children.clear()
        ui.children += r
        r
      }
      try {
        val before = fx(nodes(view))
        before should have size 5
        (2 to 12).foreach(s => fx(view.runs @= line(s, if (s % 2 == 0) "sbt test" else "sbt tests")))
        val after = fx(nodes(view))
        after.zip(before).forall(p => p._1 eq p._2) shouldBe true
        fx(words(view)) shouldBe "Ran sbt test in everyui · 9:42:13 AM · 12s"
        // a run drawn otherwise (bold now) is made again, the runs before it kept
        fx(view.runs @= line(13).updated(2, TextRun(" in ", weight = 700)))
        val bold = fx(nodes(view))
        bold.take(2).zip(before).forall(p => p._1 eq p._2) shouldBe true
        bold.drop(2).zip(before.drop(2)).exists(p => p._1 eq p._2) shouldBe false
        fx(words(view)) shouldBe "Ran sbt test in everyui · 9:42:13 AM · 13s"
      } finally fx {
        ui.children.clear()
        view.dispose()
        Focus.focused @= None
      }
    }
  }
}
