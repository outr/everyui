package everyui.rich

import everyui.rich.BlockKind.*
import everyui.{FxSpec, ui}

/** The rich-text editor where the platform has no editing surface of its own (the desktop, for now): its Markdown
  * edited in a text area, and the commands an app's toolbar gives. */
class RichEditorFallbackSpec extends FxSpec {
  "The rich-text editor on the desktop" should {
    "edit the document as its Markdown, both ways, and undo what the app changed" in {
      val editor = fx {
        ui.children.clear()
        val e = new RichTextEditor(RichDoc.fromMarkdown("Hello **world**"))
        ui.children += e
        e
      }
      val area = fx(editor.children().collectFirst { case a: everyui.text.TextArea => a }.get)
      fx(area.value()) shouldBe "Hello **world**"
      fx(area.value @= "# Title\n\n- one")
      fx(editor.document().blocks.map(_.kind)) shouldBe Vector(Heading(1), Bullet(0))
      fx(editor.setMarkdown("plain"))
      fx(area.value()) shouldBe "plain"
      fx(editor.canUndo()) shouldBe true
      fx(editor.select(RichRange(RichPos(0, 0), RichPos(0, 5))))
      fx(editor.toggleMark(Mark.Bold))
      fx(editor.markdown) shouldBe "**plain**"
      fx(editor.marks()) shouldBe Set(Mark.Bold)
      fx(editor.undo())
      fx(editor.markdown) shouldBe "plain"
      fx(editor.redo())
      fx(editor.markdown) shouldBe "**plain**"
      fx(ui.children.clear())
    }
  }
}
