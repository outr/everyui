package everyui.text

import everyui.component.TextView
import everyui.component.types.{TextAlign, WhiteSpace}
import everyui.i18n.Direction
import everyui.{FxSpec, ui}
import javafx.scene.text.{Text, TextAlignment}

/** Text starts on the side the app reads from, whatever script its words are in: a Latin line in Arabic sits on the
  * right with the Arabic lines, and follows a change of language. */
class AlignSpec extends FxSpec {
  private def wrapped(words: String): TextView = new TextView {
    content @= words
    whiteSpace @= WhiteSpace.Normal
    size.width @= 300.0
  }

  private def side(text: TextView): TextAlignment = text.node.fx.asInstanceOf[Text].getTextAlignment

  "Wrapped text" should {
    "start on the right in a right-to-left language, in any script" in {
      val latin = fx(wrapped("Clear"))
      val arabic = fx(wrapped("صافٍ"))
      fx(ui.children ++= List(latin, arabic))
      try {
        fx((side(latin), side(arabic))) shouldBe ((TextAlignment.LEFT, TextAlignment.LEFT))
        fx(Direction.set(true))
        fx((side(latin), side(arabic))) shouldBe ((TextAlignment.RIGHT, TextAlignment.RIGHT))
        fx(latin.align @= TextAlign.End)
        fx(side(latin)) shouldBe TextAlignment.LEFT
        fx(latin.align @= TextAlign.Center)
        fx(side(latin)) shouldBe TextAlignment.CENTER
        fx(latin.align @= TextAlign.Start)
        fx(Direction.set(false))
        fx((side(latin), side(arabic))) shouldBe ((TextAlignment.LEFT, TextAlignment.LEFT))
      }
      finally fx {
        Direction.set(false)
        ui.children.clear()
      }
    }

    "measure a Latin line the same in either direction" in {
      val line = fx(TextView("Clear", 24.0))
      fx(ui.children += line)
      try {
        val across = fx(line.size.width())
        fx(Direction.set(true))
        fx(line.size.width()) shouldBe across
      }
      finally fx {
        Direction.set(false)
        ui.children.clear()
      }
    }
  }
}
