package everyui.theme

import everyui.Color
import everyui.access.Access
import everyui.FxSpec

/** The accent and the states as words: a role deep enough for a glyph or a fill read in a tone of its own hue, lighter
  * on dark grounds, darker on light ones, as little as reads on every ground; one the palette gives in its place; and
  * the audit judging the words' tones at 4.5:1 and the roles' own at 3:1. EveryUI's palettes' words are their own
  * tones already. */
class TonesSpec extends FxSpec {
  // deep, as a theme that caps its colours' lightness has them
  private val deep = Palette.dark.copy(accent = Color.fromHex("397fd0"), danger = Color.fromHex("c84a41"),
    warning = Color.fromHex("c89741"), info = Color.fromHex("419bc8"))
  private val grounds = List(deep.background, deep.surface, deep.raised, deep.control, deep.controlLit)

  private def using[T](p: Palette)(test: => T): T = {
    val was = fx(Theme.palette())
    fx(Theme.palette @= p)
    try test finally fx(Theme.palette @= was)
  }

  "A role's words" should {
    "be read in a lighter tone of its hue on dark grounds, its own tone left for glyphs and fills" in using(deep) {
      val (own, words) = fx((Theme.danger(), Theme.dangerText()))
      own shouldBe Color.fromHex("c84a41")
      grounds.foreach(g => Access.contrast(words, g) should be >= 4.5)
      Tones.hsl(words)._3 should be > Tones.hsl(own)._3
      Tones.hsl(words)._1 shouldBe (Tones.hsl(own)._1 +- 0.01)
      // as little as reads: a step less does not, on the hardest ground
      val less = Tones.fromHsl(Tones.hsl(words)._1, Tones.hsl(words)._2, Tones.hsl(words)._3 - 0.02)
      grounds.map(g => Access.contrast(less, g)).min should be < 4.5
    }
    "be darker on light grounds, and the palette's own where it gives one" in {
      val light = Palette.light.copy(success = Color.fromHex("349d57"))
      using(light) {
        val words = fx(Theme.successText())
        Tones.hsl(words)._3 should be < Tones.hsl(Color.fromHex("349d57"))._3
        List(light.background, light.surface, light.raised, light.control, light.controlLit)
          .foreach(g => Access.contrast(words, g) should be >= 4.5)
      }
      using(deep.copy(dangerText = Some(Color.fromHex("ff9e98")))) {
        fx(Theme.dangerText()) shouldBe Color.fromHex("ff9e98")
      }
    }
    "be the role's own tone in EveryUI's palettes, which read already" in {
      using(Palette.dark)(fx((Theme.accentText(), Theme.dangerText())) shouldBe (Palette.dark.accent, Palette.dark.danger))
      using(Palette.light)(fx(Theme.warningText()) shouldBe Palette.light.warning)
    }
  }

  "The audit" should {
    "judge the words' tones at 4.5 and the roles' own at 3" in {
      // the deep danger reads for words in its lighter tone; as a glyph it is short of 3:1 on the lit control ground
      Palette.audit(deep).filter(_.contains("Text")) shouldBe Nil
      Palette.audit(deep).exists(_.startsWith("danger on controlLit")) shouldBe true
      Palette.audit(deep.copy(dangerText = Some(Color.fromHex("c84a41")))).exists(_.startsWith("dangerText on")) shouldBe
        true
    }
  }
}
