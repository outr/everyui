package everyui.component

import everyui.component.types.TextRange
import everyui.text.TextArea
import everyui.{FxSpec, ui}
import javafx.scene.control.TextInputControl

/** What is chosen in a field: read, set, followed, and replaced as if typed, which the field's undo takes back. */
class SelectionSpec extends FxSpec {
  private def area(words: String): (TextArea, InputView) = fx {
    ui.children.clear()
    val a = new TextArea("Message")
    a.value @= words
    ui.children += a
    (a, a.field.get)
  }

  "A field" should {
    "say what is chosen, and choose what the app asks, within its words" in {
      val (_, input) = area("make this bold")
      fx(input.select(5, 9))
      fx(input.selection) shouldBe TextRange(5, 9)
      fx(input.selected()) shouldBe TextRange(5, 9)
      fx(input.select(9, 500))
      fx(input.selection) shouldBe TextRange(9, 14)
      fx(input.moveCaret(3))
      fx(input.selection) shouldBe TextRange(3, 3)
      fx(input.selected()).collapsed shouldBe true
    }
    "replace a range as if typed, the caret after it, and its undo taking it back" in {
      val (a, input) = area("make this bold")
      fx(input.replace(5, 9, "**this**"))
      fx(a.value()) shouldBe "make **this** bold"
      fx(input.selection) shouldBe TextRange(13, 13)
      fx(input.node.fx.asInstanceOf[TextInputControl].undo())
      fx(a.value()) shouldBe "make this bold"
    }
    "follow a choice the person makes in the platform's field" in {
      val (_, input) = area("one two three")
      fx(input.node.fx.asInstanceOf[TextInputControl].selectRange(4, 7))
      fx(input.selected()) shouldBe TextRange(4, 7)
    }
    "be had from a TextInput as from a TextArea" in {
      val field = fx {
        ui.children.clear()
        val t = new TextInput("Name")
        t.value @= "Ada Lovelace"
        ui.children += t
        t.field
      }
      field.nonEmpty shouldBe true
      fx(field.get.select(0, 3))
      fx(field.get.selection) shouldBe TextRange(0, 3)
    }
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    super.afterAll()
  }
}
