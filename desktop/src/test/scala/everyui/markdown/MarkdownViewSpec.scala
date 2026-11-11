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
          List("ParagraphView", "ParagraphView", "ListView", "CodeBlockView", "MarkdownTable", "QuoteView")
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
        // the work of each append counted, which no load changes: its words fitted and its containers placed, the
        // most any one append does
        val names = Vector("fits", "placed")
        def counts(): Vector[Int] = fx(names.map(everyui.perf.PerfCounters.counts.toMap))
        val was = fx(everyui.perf.PerfCounters.on)
        fx {
          everyui.perf.PerfCounters.on = true
          everyui.perf.PerfCounters.reset()
        }
        val most = Array.fill(names.length)(0)
        // and timed in the FX thread's own time (fxWorked), what each turn left for later (the layout) among it
        val took = try fxWorked {
          var before = counts()
          while (at < doc.length) {
            at = math.min(doc.length, at + 16)
            val upTo = at
            fx(view.content @= doc.substring(0, upTo))
            appends += 1
            val after = counts()
            names.indices.foreach(n => most(n) = math.max(most(n), after(n) - before(n)))
            before = after
          }
        } finally fx {
          everyui.perf.PerfCounters.reset()
          everyui.perf.PerfCounters.on = was
        }
        val each = took / appends
        val blocks = fx(view.blocks.size)
        info(f"${doc.length} characters, 16 at a time, drawn: $each%.2f ms worked an append; the most an append " +
          s"fitted ${most(0)} words and placed ${most(1)} containers, of $blocks blocks")
        blocks shouldBe Markdown.parse(doc).size
        // an append fits the words of its last block and places what holds them (2 and 22 at most here), not the
        // hundreds of blocks before it
        most(0) should be <= 8
        most(1) should be <= 50
        // and 5 ms of the thread's own time an append at most, several times what one costs on a busy machine
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
    "know how wide its lines are drawn on their own, where it measures, once they have been read" in {
      val view = fx {
        val v = new MarkdownView("Short words.\n\nA little longer, these.", measures = true)
        v.size.width @= 420.0
        v
      }
      onScreen(view) {
        eventually(view.natural().exists(_ > 0.0)) shouldBe true
        val widest = fx(view.natural().get)
        widest should be < 420.0
        fx(view.content @= "Short.")
        eventually(view.natural().exists(_ < widest)) shouldBe true
      }
    }
  }
}
