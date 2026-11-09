package everyui.widget

import everyui.font.MaterialIcon
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Which rows within a row share its width (`WidgetNode.stretches`): one with a `Spacer`, a column, or a row that has
  * either, through `Scaled`, so a tapped row of a bar, words and a `Spacer` beside a check box fills what the box
  * leaves; one of words, pictures and buttons alone is as wide as its parts. */
class WidgetStretchSpec extends AnyWordSpec with Matchers {
  private val box = WidgetNode.Picture(WidgetPicture.Icon(MaterialIcon.CheckBoxOutlineBlank), 24.0)
  private val bar = WidgetNode.Picture(WidgetPicture.Icon(MaterialIcon.Remove), 4.0)
  private val words = WidgetNode.Column(Vector(WidgetNode.Text("Water the plants"), WidgetNode.Text("Today")))

  "A row's parts" should {
    "have room to fill with a Spacer or a column" in {
      WidgetNode.stretches(Vector(bar, words, WidgetNode.Spacer)) shouldBe true
      WidgetNode.stretches(Vector(WidgetNode.Text("Water"), WidgetNode.Spacer)) shouldBe true
      WidgetNode.stretches(Vector(bar, words)) shouldBe true
    }
    "have room to fill with a row that has either, however deep and however scaled" in {
      val tapped = WidgetNode.Row(Vector(bar, words, WidgetNode.Spacer), tap = Some(WidgetTap.Open("/tasks/42")))
      WidgetNode.stretches(Vector(WidgetNode.Row(Vector(box)), tapped)) shouldBe true
      WidgetNode.stretches(Vector(WidgetNode.Row(Vector(WidgetNode.Row(Vector(WidgetNode.Spacer)))))) shouldBe true
      WidgetNode.stretches(Vector(WidgetNode.Scaled(1.25, WidgetNode.Row(Vector(words))))) shouldBe true
      WidgetNode.stretches(Vector(WidgetNode.Scaled(0.9, WidgetNode.Spacer))) shouldBe true
    }
    "be as wide as themselves with words, pictures and buttons alone" in {
      WidgetNode.stretches(Vector.empty) shouldBe false
      WidgetNode.stretches(Vector(box)) shouldBe false
      WidgetNode.stretches(Vector(WidgetNode.Row(Vector(box), tap = Some(WidgetTap.Action("tick"))),
        WidgetNode.Text("9:41"), WidgetNode.Button("Done", None, WidgetTap.Action("done")))) shouldBe false
      WidgetNode.stretches(Vector(WidgetNode.Row(Vector(WidgetNode.Text("Water"), bar)))) shouldBe false
      WidgetNode.stretches(Vector(WidgetNode.Items(Vector(words), "None"))) shouldBe false
    }
  }
}
