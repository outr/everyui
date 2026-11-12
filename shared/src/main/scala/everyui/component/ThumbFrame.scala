package everyui.component

/** One trick-play thumbnail as it is shown ([[VideoView.thumbnail]]): a picture at `address`, read as a grid `across`
  * by `down`, of which the cell at `column` and `row` (from 0) is the thumbnail. A BIF's frame is a picture of its own,
  * one by one; a tile sheet's is a cell of the sheet. `frame` is which thumbnail it is, from 0 at the start. */
case class ThumbFrame(address: String, across: Int, down: Int, column: Int, row: Int, frame: Int)
