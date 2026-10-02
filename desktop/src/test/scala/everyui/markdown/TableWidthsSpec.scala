package everyui.markdown

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A table's columns sharing its width as a browser's automatic table layout does: each at its widest cell where all
  * fit, shrunk towards its widest word in proportion to what it would give up, and at its widest word, or its share,
  * where even those do not fit. */
class TableWidthsSpec extends AnyWordSpec with Matchers {
  "A table's widths" should {
    "be each column's widest cell where they all fit, as a wide screen lays a table out" in {
      TableWidths(Vector(40.0, 60.0), Vector(80.0, 200.0), 400.0) shouldBe Vector(80.0, 200.0)
      TableWidths(Vector(40.0, 60.0), Vector(80.0, 200.0), 280.0) shouldBe Vector(80.0, 200.0)
    }
    "share the room between the widest words and the widest cells, a long column giving up the most" in {
      val widths = TableWidths(Vector(40.0, 60.0, 50.0), Vector(60.0, 400.0, 50.0), 300.0)
      widths.sum shouldBe 300.0 +- 0.001
      // 150 over the words' 150, shared by what each would take more on one line: 20 and 340
      widths(0) shouldBe (40.0 + 20.0 * 150.0 / 360.0) +- 0.001
      widths(1) shouldBe (60.0 + 340.0 * 150.0 / 360.0) +- 0.001
      // a column whose words are all on one line already keeps its width
      widths(2) shouldBe 50.0
      widths.indices.foreach(i => widths(i) should be >= Vector(40.0, 60.0, 50.0)(i))
    }
    "keep every word whole where not even the widest words fit, the table then wider than the room" in {
      // a path that cannot wrap in the first column: the second keeps its share of the room, not a word a line
      val widths = TableWidths(Vector(380.0, 60.0), Vector(380.0, 160.0), 330.0)
      widths shouldBe Vector(380.0, 160.0)
      TableWidths(Vector(380.0, 60.0), Vector(380.0, 400.0), 330.0) shouldBe Vector(380.0, 165.0)
      // and never below a column's widest word
      TableWidths(Vector(300.0, 200.0), Vector(300.0, 500.0), 330.0) shouldBe Vector(300.0, 200.0)
    }
    "take a cell narrower on one line than its widest word measured as that word" in {
      TableWidths(Vector(90.0), Vector(80.0), 50.0) shouldBe Vector(90.0)
      TableWidths(Vector(90.0), Vector(80.0), 500.0) shouldBe Vector(90.0)
    }
  }
}
