package everyui.text

import everyui.component.*
import everyui.component.types.WhiteSpace
import everyui.focus.Focus
import everyui.markdown.MarkdownView
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import javafx.scene.text.Text
import reactify.*

/** A box of lines that holds code (`TextArea.code`), on the desktop's real nodes: EveryUI's monospaced face, its lines
  * kept whole and scrolled sideways, a tab's stops four columns apart, literal, on a code block's wash; read-only or
  * edited; and a note's look again once it is not code. */
class TextAreaCodeSpec extends FxSpec {
  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      ui.children ++= parts
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def input(area: TextArea): InputView = area.field.get
  private def box(area: TextArea): javafx.scene.control.TextArea =
    input(area).node.fx.asInstanceOf[javafx.scene.control.TextArea]
  // the text node of the box's skin, which draws its words
  private def words(area: TextArea): Text = {
    box(area).applyCss()
    box(area).lookup(".content .text").asInstanceOf[Text]
  }

  "A text area holding code" should {
    "be set in the monospaced face, unwrapped, with its tab stops, on a code block's wash" in {
      val (area, note) = fx((new TextArea(width = 200.0, grow = true), new TextArea(width = 200.0, grow = true)))
      onScreen(Column(8.0)(area, note)) {
        fx {
          area.code @= true
          area.readOnly @= true
          area.value @= "def f(): Int = {\n\tval long = \"a line that runs a long way past the box\"\n\t1\n}"
          note.value @= area.value()
        }
        fx(input(area).font.family()) shouldBe TextArea.codeFace
        fx(input(area).whiteSpace()) shouldBe WhiteSpace.Pre
        fx(input(area).tabSize()) shouldBe 4
        fx(input(area).literal()) shouldBe true
        fx(box(area).isWrapText) shouldBe false
        fx(box(area).getFont.getFamily) shouldBe "Roboto Mono"
        eventually(Option(words(area)).exists(_.getTabSize == 4)) shouldBe true
        fx(box(area).isEditable) shouldBe false
        fx(area.backgroundColor()) shouldBe MarkdownView.codeColour()
        // grown to its four lines, where a note grows to the lines its long one wraps into
        fx(area.size.height()) should be < fx(note.size.height())
      }
    }

    "be edited as code, and be a note again once it is not" in {
      val area = fx(new TextArea(width = 200.0))
      onScreen(area) {
        val family = fx(input(area).font.family())
        fx(box(area).isWrapText) shouldBe true
        fx(input(area).tabSize()) shouldBe 8
        fx(area.code @= true)
        fx(box(area).isEditable) shouldBe true
        fx(box(area).isWrapText) shouldBe false
        fx(area.focus())
        fx(area.editing()) shouldBe true
        fx(box(area).appendText("x\ty"))
        fx(area.value()) shouldBe "x\ty"
        // drawn with its tab to the fourth column: as wide as "x   y"
        val spaced = fx {
          val t = new Text("x   y")
          t.setFont(box(area).getFont)
          t.getLayoutBounds.getWidth
        }
        eventually(math.abs(words(area).getLayoutBounds.getWidth - spaced) < 0.5) shouldBe true
        fx(area.code @= false)
        fx(input(area).font.family()) shouldBe family
        fx(box(area).isWrapText) shouldBe true
        fx(input(area).tabSize()) shouldBe 8
        fx(input(area).literal()) shouldBe false
        fx(area.backgroundColor()) shouldBe Theme.controlLit()
      }
    }
  }

  "Code's tabs as spaces" should {
    "go to the next stop on each line" in {
      TextArea.spaced("a\tb", 4) shouldBe "a   b"
      TextArea.spaced("\tx\n\t\ty", 4) shouldBe "    x\n        y"
      TextArea.spaced("abcd\te", 4) shouldBe "abcd    e"
      TextArea.spaced("plain", 4) shouldBe "plain"
    }
  }
}
