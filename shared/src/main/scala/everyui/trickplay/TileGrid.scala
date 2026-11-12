package everyui.trickplay

import everyui.component.ThumbFrame
import everyui.component.ThumbnailSource.TileSheets

/** Where a thumbnail is among tile sheets (`ThumbnailSource.TileSheets`, Jellyfin's trickplay): the n-th thumbnail is
  * on sheet n / (columns × rows), left to right and then down within it. Every sheet is the whole grid, the last one's
  * unused cells left empty, as Jellyfin writes them. */
object TileGrid {
  /** The thumbnail shown at `seconds`: one every `intervalMs` from the start, the first before it and the last after
    * the end; -1 where there are none. */
  def frameAt(seconds: Double, intervalMs: Double, count: Int): Int =
    if (count <= 0 || intervalMs <= 0.0) -1
    else math.max(0, math.min(count - 1, math.floor(seconds * 1000.0 / intervalMs).toInt))

  /** Which sheet thumbnail `frame` is on. */
  def sheet(frame: Int, columns: Int, rows: Int): Int = frame / math.max(1, columns * rows)

  /** Its column on its sheet, from 0. */
  def column(frame: Int, columns: Int, rows: Int): Int = (frame % math.max(1, columns * rows)) % math.max(1, columns)

  /** Its row on its sheet, from 0. */
  def row(frame: Int, columns: Int, rows: Int): Int = (frame % math.max(1, columns * rows)) / math.max(1, columns)

  /** A sheet's address: the template with its number for `{index}`. */
  def address(template: String, sheet: Int): String = template.replace("{index}", sheet.toString)

  /** The thumbnail shown at `seconds`, as it is drawn; None where the sheets have none. */
  def at(tiles: TileSheets, seconds: Double): Option[ThumbFrame] = {
    val frame = frameAt(seconds, tiles.intervalMs, tiles.count)
    if (frame < 0 || tiles.columns <= 0 || tiles.rows <= 0) None
    else Some(ThumbFrame(address(tiles.template, sheet(frame, tiles.columns, tiles.rows)), tiles.columns, tiles.rows,
      column(frame, tiles.columns, tiles.rows), row(frame, tiles.columns, tiles.rows), frame))
  }
}
