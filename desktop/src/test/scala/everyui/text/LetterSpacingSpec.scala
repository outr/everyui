package everyui.text

import everyui.component.{Button, RichText, TextView}
import everyui.render.LetterSpacing
import everyui.{FxSpec, ui}

/** Letter spacing (`FontFeature.letterSpacing`) widens the words by the spacing after each letter, so what is laid out
  * around them is where it is on the backends that draw the letters apart: 13 letters at 0.5 are 6.5 wider. */
class LetterSpacingSpec extends FxSpec {
  "Letter spacing" should {
    "widen a label by the spacing after each letter" in {
      val label = fx(TextView("Material label", 14.0))
      fx(ui.children += label)
      try {
        val plain = fx(label.size.width())
        fx(label.font.letterSpacing @= 0.5)
        fx(label.size.width()) shouldBe (plain + 7.0 +- 0.001)
        fx(label.font.letterSpacing @= -0.25)
        fx(label.size.width()) shouldBe (plain - 3.5 +- 0.001)
        // a size set after the spacing keeps it
        fx(label.font.size @= 20.0)
        val larger = fx {
          label.font.letterSpacing @= 0.0
          label.size.width()
        }
        fx(label.font.letterSpacing @= 0.5)
        fx(label.size.width()) shouldBe (larger + 7.0 +- 0.001)
        fx(label.font.letterSpacing @= 0.0)
        fx(label.size.width()) shouldBe (larger +- 0.001)
      }
      finally fx(ui.children.clear())
    }

    "count letters, not characters, on the longest line" in {
      LetterSpacing.letters("Café") shouldBe 4
      LetterSpacing.letters("Café") shouldBe 4
      LetterSpacing.letters("👍🏽!") shouldBe 2
      LetterSpacing.width(1.0, "ab\nabcd\nabc") shouldBe 4.0
      LetterSpacing.width(0.0, "abc") shouldBe 0.0
    }

    "space styled runs by their own spacing or the paragraph's" in {
      val runs = List(TextRun("Hello "), TextRun("world", letterSpacing = Some(2.0)))
      LetterSpacing.width(0.5, runs) shouldBe (6 * 0.5 + 5 * 2.0)
      val lines = List(TextRun("a\nbc", letterSpacing = Some(1.0)), TextRun("de", letterSpacing = Some(1.0)))
      LetterSpacing.width(0.0, lines) shouldBe 4.0
      val text = fx(new RichText)
      fx(ui.children += text)
      try {
        val plain = fx {
          text.runs @= List(TextRun("Hello "), TextRun("world"))
          text.node.getWidth()
        }
        fx(text.runs @= runs)
        fx(text.node.getWidth()) shouldBe (plain + 10.0 +- 0.001)
        fx(text.font.letterSpacing @= 0.5)
        fx(text.node.getWidth()) shouldBe (plain + 13.0 +- 0.001)
      }
      finally fx(ui.children.clear())
    }

    "widen a button by its words' spacing" in {
      val button = fx(new Button("Continue"))
      fx(ui.children += button)
      try {
        val plain = fx(button.size.width())
        fx(button.letterSpacing @= 1.0)
        fx(button.size.width()) shouldBe (plain + 8.0 +- 0.001)
      }
      finally fx(ui.children.clear())
    }
  }
}
