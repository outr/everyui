package everyui.component

/** Where a video's trick-play thumbnails are ([[VideoView.thumbnails]]): the small pictures a scrubber shows of the
  * place it is at while it is moved, which a store's review asks of anything long enough to seek through (a Roku's
  * certification, 4.7, for VOD over fifteen minutes). Its cases' names are their own, since a Roku channel's type names
  * are global. */
sealed trait ThumbnailSource

object ThumbnailSource {
  /** None: nothing is shown. */
  case object NoThumbnails extends ThumbnailSource

  /** A BIF file (Roku's Base Index Frames, as Plex and Emby serve them): a header, an index of each frame's time and
    * place in the file, and the frames as JPEGs one after another. Its header and index are read once, and each frame
    * by a range of bytes when it is asked for, so a film's thousands of frames are never all fetched. The server must
    * answer a `Range` request (206); the video's own [[MediaSupport.headers]] go with each request. */
  case class BifFile(url: String) extends ThumbnailSource

  /** Tile sheets (as Jellyfin serves its trickplay): pictures each holding `columns` by `rows` thumbnails, `width` by
    * `height` pixels each, left to right and then down, one every `intervalMs` milliseconds from the start, `count` in
    * all. `template` is a sheet's address with `{index}` where its number goes (0 for the first). A sheet is shown as
    * any picture is, cut to the one thumbnail wanted. */
  case class TileSheets(template: String, intervalMs: Double, width: Double, height: Double, columns: Int, rows: Int,
                        count: Int) extends ThumbnailSource
}
