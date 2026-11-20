package everyui.component

import everyui.component.types.TextAlign
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.{FxSpec, ui}

/** A wide button's glyph and words: in the middle by default, at the start or the end inside its padding where
  * `contentAlign` says, the start the reading direction's, the glyph before the words as they read. */
class ButtonContentAlignSpec extends FxSpec {
  // where the run of its glyph and words starts, how wide it is, and whether the glyph is left of the words
  private def run(b: Button): (Double, Double, Boolean) = {
    val parts = b.children()
    val left = parts.map(_.position.left()).min
    val right = parts.map(p => p.position.left() + p.size.width()).max
    val glyph = parts.collectFirst { case g: MaterialIconView => g }.get
    val words = parts.collectFirst { case w: TextView if !w.isInstanceOf[MaterialIconView] => w }.get
    (left, right - left, glyph.position.left() < words.position.left())
  }

  private def at(b: Button, start: Double, width: Double, glyphLeft: Boolean): Unit = {
    val (left, wide, before) = fx(run(b))
    left shouldBe start +- 0.01
    wide shouldBe width +- 0.01
    before shouldBe glyphLeft
  }

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
        val (_, width, _) = fx(run(b))
        width should be > 13.0 * 1.4
        fx(b.size.width()) shouldBe 300.0
        at(b, (300.0 - width) / 2.0, width, glyphLeft = true)
        fx(b.contentAlign @= TextAlign.Start)
        fx(run(b)._1) shouldBe pad +- 0.01
        fx(b.contentAlign @= TextAlign.End)
        fx(run(b)._1) shouldBe 300.0 - width - pad +- 0.01
        fx(Direction.set(true))
        at(b, pad, width, glyphLeft = false)
        fx(b.contentAlign @= TextAlign.Start)
        fx(run(b)._1) shouldBe 300.0 - width - pad +- 0.01
        fx(b.contentAlign @= TextAlign.Center)
        at(b, (300.0 - width) / 2.0, width, glyphLeft = false)
      } finally fx {
        Direction.set(false)
        ui.children.clear()
      }
    }
  }
}
