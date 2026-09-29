package everyui.text

import everyui.component.{Button, InputView, RichText, TextView}
import everyui.{FxSpec, ui}
import javafx.scene.image.WritableImage
import javafx.scene.text.Text

/** Italic (`FontFeature.italic`): the words are drawn slanted, the face's own italic where one is registered and the
  * upright letters slanted where not (Open Sans has none bundled), and are measured as drawn. */
class ItalicSpec extends FxSpec {
  // the letters' pixels, where anything is drawn
  private def drawn(text: TextView): Vector[Int] = fx {
    val node = text.node.fx
    val image: WritableImage = node.snapshot(null, null)
    val reader = image.getPixelReader
    (0 until image.getHeight.toInt).flatMap(y => (0 until image.getWidth.toInt).map(x => reader.getArgb(x, y))).toVector
  }

  "Italic" should {
    "slant a label and measure it as drawn" in {
      val label = fx(TextView("-- No Results --", 16.0))
      fx(ui.children += label)
      try {
        val upright = drawn(label)
        val plainWidth = fx(label.size.width())
        fx(label.font.italic @= true)
        fx(label.node.fx.asInstanceOf[Text].getFont.getStyle.toLowerCase) should include("italic")
        val slanted = drawn(label)
        slanted should not be upright
        fx(label.size.width()) shouldBe (fx(label.node.fx.asInstanceOf[Text].getLayoutBounds.getWidth) +- 0.001)
        // a size set after the slant keeps it
        fx(label.font.size @= 20.0)
        fx(label.node.fx.asInstanceOf[Text].getFont.getStyle.toLowerCase) should include("italic")
        fx(label.font.size @= 16.0)
        fx(label.font.italic @= false)
        fx(label.node.fx.asInstanceOf[Text].getFont.getStyle.toLowerCase) should not include "italic"
        drawn(label) shouldBe upright
        fx(label.size.width()) shouldBe (plainWidth +- 0.001)
      }
      finally fx(ui.children.clear())
    }

    "shear a face with no italic of its own about its baseline, keeping its width" in {
      val label = fx(TextView("Searching…", 16.0))
      fx(label.font.family @= "Open Sans")
      fx(ui.children += label)
      def sheared: Boolean = fx(label.node.fx.getTransforms.toArray.exists(_.isInstanceOf[javafx.scene.transform.Shear]))
      try {
        val upright = drawn(label)
        val width = fx(label.size.width())
        sheared shouldBe false
        fx(label.font.italic @= true)
        sheared shouldBe true
        drawn(label) should not be upright
        fx(label.size.width()) shouldBe (width +- 0.001)
        fx(label.font.italic @= false)
        sheared shouldBe false
        drawn(label) shouldBe upright
        // a run marked italic in the same face is sheared too
        val text = fx(RichText(List(TextRun("upright "), TextRun("slanted", italic = true)), 400.0))
        fx(text.font.family @= "Open Sans")
        fx(ui.children += text)
        fx(text.node.fx.asInstanceOf[javafx.scene.text.TextFlow].getChildren.toArray.toList
          .map(_.asInstanceOf[Text].getTransforms.size)) shouldBe List(0, 1)
      }
      finally fx(ui.children.clear())
    }

    "slant a paragraph's runs, and a field's and a button's words" in {
      val text = fx(RichText(List(TextRun("Searching"), TextRun("…", weight = 600)), 400.0))
      val field = fx(new InputView)
      val button = fx(new Button("Retry"))
      fx(ui.children ++= List(text, field, button))
      try {
        def styles: List[String] = fx(text.node.fx.asInstanceOf[javafx.scene.text.TextFlow].getChildren.toArray.toList
          .map(_.asInstanceOf[Text].getFont.getStyle.toLowerCase))
        styles.exists(_.contains("italic")) shouldBe false
        fx(text.font.italic @= true)
        styles.forall(_.contains("italic")) shouldBe true
        fx(field.font.italic @= true)
        fx(field.node.fx.asInstanceOf[javafx.scene.control.TextField].getFont.getStyle.toLowerCase) should include("italic")
        fx(button.italic @= true)
        fx(button.size.width()) should be > 0.0
      }
      finally fx(ui.children.clear())
    }
  }
}
