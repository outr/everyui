package everyui.text

import everyui.component.{RichText, TextView}
import everyui.component.types.FontWeight
import everyui.render.FontWeights
import everyui.{FxSpec, ui}
import javafx.scene.text.{Font, Text, TextFlow}

/** A weight of 600 is drawn heavier than 400 on the desktop: JavaFX weighs a family as bold or not, and answered
  * `FontWeight.SEMI_BOLD` with the regular face. It takes the family's lightest face at or above the weight, its bold
  * where it has no semibold (FontWeights), and Open Sans at 600 is the bundled Open Sans SemiBold. */
class FontWeightSpec extends FxSpec {
  // the label's face, and its width, at `weight`
  private def label(family: String, weight: Int, italic: Boolean = false): (Font, Double) = {
    val text = fx(TextView("Release planning", 16.0))
    fx {
      text.font.family @= family
      text.font.weight @= new FontWeight(weight, "")
      text.font.italic @= italic
      ui.children += text
    }
    try fx((text.node.fx.asInstanceOf[Text].getFont, text.size.width()))
    finally fx(ui.children -= text)
  }

  private def weightOf(font: Font): Int = FontWeights.weightOf(font.getName + " " + font.getStyle)

  "A weight" should {
    "draw 600 and 700 heavier than 400 in the system family" in {
      val (regular, regularWidth) = label("", 400)
      val (semibold, semiboldWidth) = label("", 600)
      val (bold, _) = label("", 700)
      weightOf(regular) shouldBe 400
      weightOf(semibold) should be >= 600
      weightOf(bold) shouldBe 700
      semibold should not be regular
      semiboldWidth should be > regularWidth
      // the system family, as JavaFX has it, has no semibold: 600 is its bold
      if (!fx(Font.getFontNames("System")).toArray.exists(_.toString.toLowerCase.contains("semibold"))) semibold shouldBe bold
    }

    "draw 600 in the bundled Open Sans SemiBold" in {
      val (regular, regularWidth) = label("Open Sans", 400)
      val (semibold, semiboldWidth) = label("Open Sans", 600)
      regular.getName shouldBe "Open Sans Regular"
      semibold.getName shouldBe "Open Sans SemiBold"
      semiboldWidth should be > regularWidth
      // and a family looked up by weight finds it, once loaded
      fx(FontWeights("Open Sans", 600, false, 16.0).getName) shouldBe "Open Sans SemiBold"
    }

    "keep 600 heavy when slanted" in {
      val (regular, _) = label("", 400, italic = true)
      val (semibold, _) = label("", 600, italic = true)
      semibold.getStyle.toLowerCase should include("italic")
      weightOf(regular) shouldBe 400
      weightOf(semibold) should be >= 600
      val (openSans, _) = label("Open Sans", 600, italic = true)
      weightOf(openSans) should be >= 600
    }

    "draw a paragraph's 600 runs heavier" in {
      val text = fx(RichText(List(TextRun("Unread "), TextRun("title", weight = 600), TextRun(" bold", weight = 700)), 400.0))
      fx(ui.children += text)
      try {
        val weights = fx(text.node.fx.asInstanceOf[TextFlow].getChildren.toArray.toList.map(n => weightOf(n.asInstanceOf[Text].getFont)))
        weights.head shouldBe 400
        weights(1) should be >= 600
        weights(2) shouldBe 700
      }
      finally fx(ui.children -= text)
    }

    "read a face's weight from its name or style" in {
      FontWeights.weightOf("Regular") shouldBe 400
      FontWeights.weightOf("SemiBold Italic") shouldBe 600
      FontWeights.weightOf("Demi Bold") shouldBe 600
      FontWeights.weightOf("Bold") shouldBe 700
      FontWeights.weightOf("ExtraBold") shouldBe 800
      FontWeights.weightOf("Extra Light") shouldBe 200
      FontWeights.weightOf("Light") shouldBe 300
      FontWeights.weightOf("Medium") shouldBe 500
      FontWeights.weightOf("Black") shouldBe 900
    }
  }
}
