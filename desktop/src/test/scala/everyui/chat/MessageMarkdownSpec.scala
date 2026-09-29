package everyui.chat

import everyui.component.RichText
import everyui.component.types.TextDirection
import everyui.focus.Focus
import everyui.markdown.{ListView, ParagraphView}
import everyui.{FxSpec, ui}
import reactify.*

/** A message's Markdown on the desktop's real nodes: as narrow as a short reply, as wide as its widest line up to the
  * widest a bubble's words may be, all of that for code or a table, its own line breaks kept, a streamed message
  * growing in place, and each paragraph and list running the way its own letters do. */
class MessageMarkdownSpec extends FxSpec {
  private def onScreen[T](m: MessageMarkdown)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += m
    }
    try test finally fx {
      ui.children.clear()
      m.dispose()
      Focus.focused @= None
    }
  }

  private def lines(m: MessageMarkdown): Vector[RichText] = m.view.children().collect { case p: ParagraphView =>
    p.children().collect { case r: RichText => r }
  }.flatten

  "A message's Markdown" should {
    "be as narrow as a short reply, and as wide as its widest line up to the widest" in {
      val text = Var("OK")
      val m = fx(new MessageMarkdown(text, Val(400.0)))
      onScreen(m) {
        eventually(m.size.width() > 5.0 && m.size.width() < 40.0) shouldBe true
        fx(text @= "A longer line, with **some** words")
        eventually(m.size.width() > 150.0 && m.size.width() < 400.0) shouldBe true
        // the widest of its lines, a list's past its indent
        fx(text @= "Short\n\n- a list item that is rather longer than the paragraph above it")
        eventually(m.size.width() > 300.0) shouldBe true
        fx(m.view.children().exists(_.isInstanceOf[ListView])) shouldBe true
        // longer than the widest: the widest, wrapped
        fx(text @= "word " * 200)
        eventually(m.size.width() == 400.0) shouldBe true
        fx(lines(m).head.size.height()) should be > 100.0
      }
    }

    "take all the widest for code or a table, however short" in {
      val m = fx(new MessageMarkdown(Val("```\nx\n```"), Val(360.0)))
      onScreen(m) {
        eventually(m.size.width() == 360.0) shouldBe true
      }
      val t = fx(new MessageMarkdown(Val("| a | b |\n|---|---|\n| 1 | 2 |"), Val(360.0)))
      onScreen(t) {
        eventually(t.size.width() == 360.0) shouldBe true
      }
    }

    "keep the line breaks it was typed with" in {
      val m = fx(new MessageMarkdown(Val("one\ntwo\nthree"), Val(400.0)))
      onScreen(m) {
        eventually(lines(m).size == 3) shouldBe true
        fx(m.size.width()) should be < 60.0
      }
    }

    "grow in place as it streams, the blocks before the last kept" in {
      val text = Var("# Plan\n\nFirst")
      val m = fx(new MessageMarkdown(text, Val(400.0)))
      onScreen(m) {
        val heading = fx(m.view.children().head)
        (1 to 20).foreach(i => fx(text @= text() + " word" + i))
        fx(m.view.children().head) should be theSameInstanceAs heading
        eventually(m.size.width() == 400.0) shouldBe true
      }
    }

    "run each paragraph the way its own letters do, and put an Arabic list's bullets on its right" in {
      val m = fx(new MessageMarkdown(Val("Hello\n\n- مرحبا بكم\n- شكرا"), Val(400.0)))
      onScreen(m) {
        eventually(m.size.width() > 40.0) shouldBe true
        fx(lines(m).map(_.direction()).distinct) shouldBe Vector(TextDirection.Auto)
        val list = fx(m.view.children().collectFirst { case l: ListView => l }.get)
        // the bullets (TextViews) sit on the right of the list, the items' words to their left
        val (bullets, width) = fx((list.children().collect { case row: everyui.component.Container => row.children() }.flatten.collect {
          case t: everyui.component.TextView =>
          t.position.left() + t.size.width() }, list.size.width()))
        bullets.size shouldBe 2
        bullets.foreach(right => right should be > width - 24.0)
      }
    }
  }
}
