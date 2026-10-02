package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File

/** A face cut down to some of its characters (FontSubset): an icon face numbered afresh with only its icons' glyphs,
  * and a face for words keeping every glyph's number, its layout tables and the glyphs its substitutions reach. */
class FontSubsetSpec extends AnyWordSpec with Matchers {
  private val fonts = Seq("assets/fonts", "../assets/fonts").map(new File(_)).find(_.isDirectory).get
  private lazy val icons = Sfnt.read(new File(fonts, "MaterialIcons-Regular.ttf"))
  private lazy val words = Sfnt.read(new File(fonts, "OpenSans-Regular.ttf"))
  // add, close, search
  private val three = Set(0xE145, 0xE5CD, 0xE8B6)

  "FontSubset.compact" should {
    lazy val cut = Sfnt(FontSubset.compact(icons, three).bytes)

    "map only the characters asked for, each to a glyph of its own after .notdef" in {
      cut.characters.keySet shouldBe three
      cut.numGlyphs shouldBe 4
      cut.characters.values.toSet shouldBe Set(1, 2, 3)
    }

    "keep each glyph's outline and advance as the whole face has them" in {
      val (advance, _) = icons.metrics
      val (cutAdvance, _) = cut.metrics
      three.foreach { c =>
        val outline = icons.glyph(icons.characters(c))
        // padded to a four-byte boundary in the subset
        cut.glyph(cut.characters(c)).take(outline.length).toSeq shouldBe outline.toSeq
        cutAdvance(cut.characters(c)) shouldBe advance(icons.characters(c))
      }
    }

    "leave out the tables that name glyphs by number, and the glyph names" in {
      Seq("GSUB", "GPOS", "GDEF", "kern").foreach(t => cut.tables.contains(t) shouldBe false)
      Sfnt.u32(cut.table("post"), 0) shouldBe 0x00030000L
      cut.bytes.length should be < 8000
    }
  }

  "FontSubset.retained" should {
    val fi = Set('f'.toInt, 'i'.toInt)
    lazy val both = Sfnt(FontSubset.retained(words, fi).bytes)
    // what f and i make together and neither alone: their ligature
    lazy val ligature = GsubClosure(words, fi.map(words.characters) + 0) --
      GsubClosure(words, Set(words.characters('f'.toInt), 0)) -- GsubClosure(words, Set(words.characters('i'.toInt), 0))

    "keep every glyph's number, and map only the characters asked for" in {
      both.numGlyphs shouldBe words.numGlyphs
      both.characters shouldBe words.characters.filter(c => fi(c._1))
    }

    "keep the layout tables as they are" in {
      Seq("GSUB", "GPOS", "GDEF").foreach(t => both.table(t).toSeq shouldBe words.table(t).toSeq)
    }

    "keep the outlines the characters reach, a ligature of them too, and empty the rest" in {
      ligature should not be empty
      (fi.map(words.characters) ++ ligature).foreach { g =>
        val outline = words.glyph(g)
        both.glyph(g).take(outline.length).toSeq shouldBe outline.toSeq
      }
      both.glyph(words.characters('a'.toInt)) shouldBe empty
      val f = Sfnt(FontSubset.retained(words, Set('f'.toInt)).bytes)
      ligature.foreach(g => f.glyph(g) shouldBe empty)
    }

    "keep the metrics of what it keeps, and none of the rest" in {
      val (advance, bearing) = words.metrics
      val (kept, keptBearing) = both.metrics
      fi.map(words.characters).foreach { g => kept(g) shouldBe advance(g); keptBearing(g) shouldBe bearing(g) }
      kept(words.characters('a'.toInt)) shouldBe 0
    }
  }
}
