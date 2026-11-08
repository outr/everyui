package everyui.component

import everyui.component.types.TextAlign
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.{FxSpec, ui}

/** A wide button's glyph and words: in the middle by default, at the start or the end inside its padding where
  * `contentAlign` says, the start the reading direction's. */
class ButtonContentAlignSpec extends FxSpec {
  private def line(b: Button): Component = b.children().collectFirst { case c: Container => c }.get

  "A wide button" should {
    "put its glyph and words in the middle, at the start or at the end, mirrored right to left" in {
      val b = fx(new Button("Users", glyph = Some(MaterialIcon.Settings)))
      fx {
        b.minWidth @= 300.0
        ui.children.clear()
        ui.children += b
      }
      try {
        val pad = 13.0 * 1.1
        val width = fx(line(b).size.width())
        fx(b.size.width()) shouldBe 300.0
        fx(line(b).position.left()) shouldBe (300.0 - width) / 2.0 +- 0.01
        fx(b.contentAlign @= TextAlign.Start)
        fx(line(b).position.left()) shouldBe pad +- 0.01
        fx(b.contentAlign @= TextAlign.End)
        fx(line(b).position.left()) shouldBe 300.0 - width - pad +- 0.01
        fx(Direction.set(true))
        fx(line(b).position.left()) shouldBe pad +- 0.01
        fx(b.contentAlign @= TextAlign.Start)
        fx(line(b).position.left()) shouldBe 300.0 - width - pad +- 0.01
        fx(b.contentAlign @= TextAlign.Center)
        fx(line(b).position.left()) shouldBe (300.0 - width) / 2.0 +- 0.01
      } finally fx {
        Direction.set(false)
        ui.children.clear()
      }
    }
  }
}
