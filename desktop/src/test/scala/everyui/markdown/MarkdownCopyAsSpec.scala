package everyui.markdown

import everyui.component.{Component, Container}
import everyui.focus.Focus
import everyui.{FxSpec, ui}

/** What a Markdown view's components say they are when words selected across them are copied (`Component.copyAs`),
  * which the web reads back into clean HTML and prose: each block's tag, a list's items and the bullets left out, a
  * code block's header left out, and a table's rows and cells, drawn plain or as a `DataTable`. */
class MarkdownCopyAsSpec extends FxSpec {
  private def onScreen[T](view: MarkdownView)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += view
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  // every component under `c`, `c` first
  private def all(c: Component): List[Component] = c :: (c match {
    case k: Container => k.children().toList.flatMap(all)
    case _ => Nil
  })
  private def tags(c: Component): List[String] = all(c).map(_.copyAs()).filter(_.nonEmpty)

  "A Markdown view" should {
    "say what each block is when it is copied" in {
      val view = fx(new MarkdownView("## Title\n\nSome **words**.\n\n- one\n- two\n\n3. three\n\n```scala\nval x = 1\n```" +
        "\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n> quoted\n\n---"))
      fx {
        view.richTables @= false
        view.size.width @= 500.0
      }
      onScreen(view) {
        eventually(view.size.height() > 100.0) shouldBe true
        fx(view.children().map(_.copyAs()).toList) shouldBe List("h2", "p", "ul", "ol 3", "pre", "", "blockquote", "hr")
        // a list's items, their bullets left out
        fx(tags(view.children()(2))) shouldBe List("ul", "li", "none", "p", "li", "none", "p")
        // a code block's language and Copy button left out
        fx(tags(view.children()(4))) shouldBe List("pre", "none", "none")
        fx(tags(view.children()(5))) shouldBe List("table", "tr", "th", "th", "tr", "td", "td")
        fx(tags(view.children()(6))) shouldBe List("blockquote", "p")
      }
    }

    "say a sorting table's rows and cells too" in {
      val view = fx(MarkdownView("| a | b |\n|---|---|\n| 1 | 2 |", 500.0))
      onScreen(view) {
        eventually(view.size.height() > 20.0) shouldBe true
        fx(view.children().head.asInstanceOf[MarkdownTable].engage())
        val found = fx(tags(view.children().head))
        found should contain allOf ("table", "tr", "th", "td")
        found.count(_ == "th") shouldBe 2
        found.count(_ == "td") shouldBe 2
      }
    }
  }

  "Each kind of block" should {
    "copy as its tag" in {
      MarkdownBlocks.copyAs(MdBlock.Heading(9, Nil)) shouldBe "h6"
      MarkdownBlocks.copyAs(MdBlock.BulletList(ordered = true, 1, tight = true, Nil)) shouldBe "ol"
      MarkdownBlocks.copyAs(MdBlock.Html("<b>")) shouldBe "pre"
      MarkdownBlocks.copyAs(MdBlock.Definitions(Nil)) shouldBe "none"
    }
  }
}
