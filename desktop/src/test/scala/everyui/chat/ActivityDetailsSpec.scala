package everyui.chat

import everyui.component.{Component, Container, TextView}
import everyui.focus.Focus
import everyui.markdown.MarkdownView
import everyui.{FxSpec, ui}
import reactify.*

/** What an opened tool call shows is there to be read and copied: its details' words selectable, whoever made them
  * (input and output as text, an app's Markdown with its code), and copied with their structure (the headings as
  * paragraphs, the text as code, Markdown's blocks as they are tagged), while the line that opens and closes the call
  * stays a thing to click. */
class ActivityDetailsSpec extends FxSpec {
  private def all(c: Component): Vector[Component] = c +: (c match {
    case k: Container => k.children().flatMap(all)
    case _ => Vector.empty
  })

  // whether its words can be selected: it, or something it is in, is selectable
  private def reads(c: Component): Boolean = c.selectable() || c.parent().exists(reads)

  private def shown[T](c: Component)(test: => T): T = {
    fx(ui.children += c)
    try test finally fx {
      ui.children -= c
      c.dispose()
      Focus.focused @= None
    }
  }

  private def opened(details: () => Component): (ActivityView, Component) = {
    val made = fx(details())
    val a = ChatEntry.activity("call", "Ran the tests", ActivityStatus.Succeeded, Some(() => made),
      body = "`sbt test`")
    fx(a.expanded @= true)
    (fx(new ActivityView(a, 600.0, () => Some(made))), made)
  }

  private def box(v: ActivityView): Container = v.children().head.asInstanceOf[Container]
  private def line(v: ActivityView): Container = box(v).children().head.asInstanceOf[Container]

  "An opened tool call's details" should {
    "have input and output as selectable text, copied as code under paragraphs that say which is which" in {
      val (v, made) = opened(ChatEntry.textDetails("sbt test", "412 passed"))
      shown(v) {
        eventually(v.size.height() > 0.0) shouldBe true
        val texts = fx(all(made).collect { case t: TextView => t })
        fx(texts.map(_.content())) shouldBe Vector(ChatWords.input, "sbt test", ChatWords.output, "412 passed")
        fx(texts.map(_.copyAs())) shouldBe Vector("p", "pre", "p", "pre")
        fx(texts.forall(reads)) shouldBe true
      }
    }

    "have an app's own details selectable, a Markdown result and its code keeping their tags" in {
      val (v, made) = opened(() => MarkdownView("Found **3** files:\n\n```\na.scala\nb.scala\n```", 400.0))
      shown(v) {
        eventually(v.size.height() > 0.0) shouldBe true
        fx(made.parent().exists(_.selectable())) shouldBe true
        fx(made.asInstanceOf[MarkdownView].children().map(_.copyAs()).toList) shouldBe List("p", "pre")
        fx(all(made).collect { case t: TextView => t }.filter(_.copyAs() != "none").forall(reads)) shouldBe true
      }
    }

    "leave the line that opens and closes the call unselectable, and the body selectable as it was" in {
      val (v, _) = opened(ChatEntry.textDetails("sbt test", "412 passed"))
      shown(v) {
        eventually(v.size.height() > 0.0) shouldBe true
        fx(box(v).selectable()) shouldBe false
        fx(all(line(v)).exists(reads)) shouldBe false
        fx(all(line(v)).collect { case t: TextView => t.content() }) should contain("Ran the tests")
        fx(v.bodyView.exists(_.selectable())) shouldBe true
      }
    }
  }
}
