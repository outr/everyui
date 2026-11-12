package everyui.trickplay

import everyui.component.ThumbFrame
import everyui.component.ThumbnailSource.TileSheets
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Where a thumbnail is among tile sheets (TileGrid): which sheet, which cell, and the frame for a place at its
  * edges. */
class TileGridSpec extends AnyWordSpec with Matchers {
  // 10 seconds apart, 5 across and 4 down (20 a sheet), 45 in all: sheets 0 and 1 full, sheet 2 holding 5
  private val tiles = TileSheets("https://media.example/trickplay/320/{index}.jpg", 10000.0, 320.0, 180.0, 5, 4, 45)

  "Tile sheets" should {
    "number their thumbnails one an interval from the start" in {
      TileGrid.frameAt(0.0, 10000.0, 45) shouldBe 0
      TileGrid.frameAt(9.99, 10000.0, 45) shouldBe 0
      TileGrid.frameAt(10.0, 10000.0, 45) shouldBe 1
      TileGrid.frameAt(445.0, 10000.0, 45) shouldBe 44
    }
    "keep to the first and the last past their ends" in {
      TileGrid.frameAt(-3.0, 10000.0, 45) shouldBe 0
      TileGrid.frameAt(100000.0, 10000.0, 45) shouldBe 44
      TileGrid.frameAt(5.0, 10000.0, 0) shouldBe -1
      TileGrid.frameAt(5.0, 0.0, 45) shouldBe -1
    }
    "place each on its sheet left to right, then down" in {
      TileGrid.sheet(0, 5, 4) shouldBe 0
      TileGrid.sheet(19, 5, 4) shouldBe 0
      TileGrid.sheet(20, 5, 4) shouldBe 1
      TileGrid.sheet(44, 5, 4) shouldBe 2
      (TileGrid.column(7, 5, 4), TileGrid.row(7, 5, 4)) shouldBe (2, 1)
      (TileGrid.column(19, 5, 4), TileGrid.row(19, 5, 4)) shouldBe (4, 3)
      (TileGrid.column(20, 5, 4), TileGrid.row(20, 5, 4)) shouldBe (0, 0)
      (TileGrid.column(44, 5, 4), TileGrid.row(44, 5, 4)) shouldBe (4, 0)
    }
    "name a sheet by its number" in {
      TileGrid.address(tiles.template, 2) shouldBe "https://media.example/trickplay/320/2.jpg"
    }
    "give the thumbnail for a place as its sheet and its cell" in {
      TileGrid.at(tiles, 0.0) shouldBe Some(ThumbFrame("https://media.example/trickplay/320/0.jpg", 5, 4, 0, 0, 0))
      TileGrid.at(tiles, 275.0) shouldBe Some(ThumbFrame("https://media.example/trickplay/320/1.jpg", 5, 4, 2, 1, 27))
      TileGrid.at(tiles, 99999.0) shouldBe Some(ThumbFrame("https://media.example/trickplay/320/2.jpg", 5, 4, 4, 0, 44))
      TileGrid.at(tiles.copy(count = 0), 5.0) shouldBe None
    }
  }
}
