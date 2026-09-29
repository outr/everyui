package everyui.markdown

import everyui.component.{Component, RichText}
import everyui.focus.Focus
import everyui.render.RichFlow
import everyui.{FxSpec, ui}

/** Markdown drawn on the desktop: a component per block, laid out to the width given and as tall as its blocks; a
  * streamed answer that keeps its earlier blocks' components and draws a growing paragraph or code block in place;
  * runs in their own styles; links followed by a click or by Enter. */
class MarkdownViewSpec extends FxSpec {
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

  "A Markdown view" should {
    "draw a component for each block, as wide as it is given and as tall as its blocks" in {
      val view = fx(MarkdownView("# Title\n\nSome *words* here.\n\n- one\n- two\n\n```scala\nval x = 1\n```\n\n" +
        "| a | b |\n|---|--:|\n| 1 | 2 |\n\n> quoted\n\n---", 420.0))
      onScreen(view) {
        eventually(view.size.height() > 100.0) shouldBe true
        fx(view.children().map(_.getClass.getSimpleName).toList.take(6)) shouldBe
          List("ParagraphView", "ParagraphView", "ListView", "CodeBlockView", "TableView", "QuoteView")
        fx(view.children().forall(c => c.size.width() == 420.0)) shouldBe true
        val bottom = fx(view.children().map(c => c.position.top() + c.size.height()).max)
        fx(view.size.height()) shouldBe bottom +- 1.0
        // narrower, the paragraph wraps onto more lines and the view grows
        val tall = fx(view.size.height())
        fx(view.content @= "word " * 60)
        val wide = fx(view.size.height())
        fx(view.size.width @= 200.0)
        eventually(view.size.height() > wide * 1.5) shouldBe true
        tall should be > 0.0
      }
    }
    "keep the components of the blocks before the last as an answer streams in, and draw the last in place" in {
      val view = fx(MarkdownView("", 500.0))
      onScreen(view) {
        fx(view.content @= "# Title\n\nFirst para")
        val heading = fx(view.children()(0))
        val para = fx(view.children()(1))
        fx(view.content @= "# Title\n\nFirst paragraph, *longer*")
        fx(view.children()(0)) should be theSameInstanceAs heading
        fx(view.children()(1)) should be theSameInstanceAs para
        fx(view.content @= "# Title\n\nFirst paragraph, *longer*\n\n```js\nlet a")
        val code = fx(view.children()(2))
        fx(view.content @= "# Title\n\nFirst paragraph, *longer*\n\n```js\nlet a = 1;\nlet b")
        fx(view.children()(2)) should be theSameInstanceAs code
        fx(code.asInstanceOf[CodeBlockView].code()) shouldBe "let a = 1;\nlet b"
        fx(view.children()(1)) should be theSameInstanceAs para
      }
    }
    "cost per append what its last block does, measured on the desktop" in {
      val section = "## Section\n\nSome *words* and `code` and a [link](/x), then more words to fill a line.\n\n" +
        "- an item\n- another item\n\n```scala\nval x = List(1, 2, 3).map(_ * 2)\n```\n\n"
      val doc = section * (10240 / section.length + 1)
      val view = fx(MarkdownView("", 600.0))
      onScreen(view) {
        var at = 0
        var appends = 0
        val start = System.nanoTime()
        while (at < doc.length) {
          at = math.min(doc.length, at + 16)
          val upTo = at
          fx(view.content @= doc.substring(0, upTo))
          appends += 1
        }
        val each = (System.nanoTime() - start) / 1e6 / appends
        info(f"${doc.length} characters, 16 at a time, drawn: $each%.2f ms an append (the FX thread round trip included)")
        fx(view.blocks.size) shouldBe Markdown.parse(doc).size
        each should be < 5.0
      }
    }
    "draw runs in their own styles, and follow a link by a click or by Enter" in {
      var followed: List[String] = Nil
      val view = fx(new MarkdownView("A [first](/one) and **bold** and [second](https://two.example).",
        onLink = href => followed = followed :+ href))
      fx(view.size.width @= 400.0)
      onScreen(view) {
        val para = fx(view.children().head.asInstanceOf[ParagraphView])
        val text = fx(para.children().head.asInstanceOf[RichText])
        val flow = fx(text.node.fx.asInstanceOf[RichFlow])
        fx(flow.getChildren.size) shouldBe 7
        val link = fx(flow.getChildren.get(1).asInstanceOf[javafx.scene.text.Text])
        fx(link.getText) shouldBe "first"
        fx(link.isUnderline) shouldBe true
        fx(javafx.event.Event.fireEvent(link, new javafx.scene.input.MouseEvent(
          javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1,
          false, false, false, false, true, false, false, true, false, false, null)))
        followed shouldBe List("/one")
        fx(para.focusable()) shouldBe true
        fx(para.focus())
        fx {
          Focus.key("Enter", true)
          Focus.key("Enter", false)
        }
        // two links: a menu to choose from, its first entry chosen with Enter
        fx {
          Focus.key("Enter", true)
          Focus.key("Enter", false)
        }
        followed shouldBe List("/one", "/one")
      }
    }
  }
}
