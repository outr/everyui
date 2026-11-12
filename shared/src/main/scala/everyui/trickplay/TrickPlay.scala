package everyui.trickplay

import everyui.component.{ThumbFrame, ThumbnailSource}
import everyui.component.ThumbnailSource.{BifFile, NoThumbnails, TileSheets}
import everyui.render.{ByteRanges, Platform}

/** A video's trick-play thumbnails, fetched as they are asked for and the last few kept (`VideoView.thumbnails`): a
  * tile sheet's thumbnail is its sheet's address and its cell, at once; a BIF's index is read once, and each frame
  * fetched as a range of the file (`ByteRanges`, off the thread that draws) when it is first asked for, one at a
  * time.
  *
  * Only the latest ask is answered: a scrubber moved quickly asks for frame after frame, and what it wants is the one
  * it has stopped at, not each it passed, so a frame that arrives after the scrubber has moved on is kept and not
  * shown, and the frame now wanted is fetched next. Once it has what was asked, it fetches the frames either side, so
  * a scrubber stepped along finds the next one already there. `headers` are sent with each request (the video's). */
class TrickPlay(headers: () => Map[String, String]) {
  private var source: ThumbnailSource = NoThumbnails
  // each source in turn, so what arrives for one no longer used is let go
  private var generation: Int = 0
  private var index: Option[BifIndex] = None
  // frames fetched, each with its picture's address, the most recently used first
  private var kept: List[(Int, String)] = Nil
  // frames that could not be had, not asked for again for this source
  private var missed: List[Int] = Nil
  private var loading: Int = -1
  private var wanted: Int = -1
  // the place asked for before the index was in
  private var wantedSeconds: Double = -1.0
  private var answer: ThumbFrame => Unit = _ => ()

  /** Thumbnails from `next`, what was kept of the last source let go. */
  def use(next: ThumbnailSource): Unit = {
    generation += 1
    kept.foreach(k => ByteRanges.forget(k._2))
    kept = Nil
    missed = Nil
    index = None
    loading = -1
    wanted = -1
    wantedSeconds = -1.0
    source = next
    next match {
      case BifFile(url) => readIndex(url, generation)
      case _ => ()
    }
  }

  /** The thumbnail for `seconds` in, to `show` as soon as it is here: at once where it is (a tile sheet's, a frame
    * kept), later where it has to be fetched, and never where there is none. */
  def frame(seconds: Double, show: ThumbFrame => Unit): Unit = source match {
    case tiles: TileSheets => TileGrid.at(tiles, seconds).foreach(show)
    case BifFile(url) =>
      answer = show
      index match {
        case Some(ix) => want(url, ix, ix.frameAt(seconds))
        case None => wantedSeconds = seconds
      }
    case _ => ()
  }

  // the header and as much of the index as a long film has, and the rest after it where the index is longer still
  private def readIndex(url: String, asked: Int): Unit =
    ByteRanges.read(url, headers(), 0, BifIndex.FirstRead, got => if (asked == generation) got match {
      case Some(bytes) =>
        val end = BifIndex.indexEnd(bytes)
        if (end < 0) Platform.log("TrickPlay: not a BIF file: " + url)
        else if (end <= bytes.length) indexed(url, bytes)
        else ByteRanges.read(url, headers(), 0, end, again => if (asked == generation) again match {
          case Some(all) => indexed(url, all)
          case None => Platform.log("TrickPlay: the BIF index could not be read: " + url)
        })
      case None => Platform.log("TrickPlay: the BIF header could not be read: " + url)
    })

  private def indexed(url: String, bytes: Array[Byte]): Unit = BifIndex.parse(bytes) match {
    case Some(ix) =>
      index = Some(ix)
      if (wantedSeconds >= 0.0) want(url, ix, ix.frameAt(wantedSeconds))
    case None => Platform.log("TrickPlay: the BIF index is not one: " + url)
  }

  private def want(url: String, ix: BifIndex, frame: Int): Unit = if (frame >= 0) {
    wanted = frame
    keptAt(frame) match {
      case Some(address) =>
        // the most recently used first, so it is the last let go
        kept = (frame, address) :: kept.filter(k => k._1 != frame)
        answer(ThumbFrame(address, 1, 1, 0, 0, frame))
      case None => if (loading < 0) fetchNext(url, ix)
    }
  }

  private def keptAt(frame: Int): Option[String] = kept.find(k => k._1 == frame).map(k => k._2)

  private def fetchable(ix: BifIndex, frame: Int): Boolean =
    frame >= 0 && frame < ix.count && keptAt(frame).isEmpty && !missed.contains(frame)

  // the frame wanted, else the ones either side of it, else nothing
  private def fetchNext(url: String, ix: BifIndex): Unit = {
    val next =
      if (fetchable(ix, wanted)) wanted
      else if (fetchable(ix, wanted + 1)) wanted + 1
      else if (fetchable(ix, wanted - 1)) wanted - 1
      else if (fetchable(ix, wanted + 2)) wanted + 2
      else -1
    if (next >= 0) fetch(url, ix, next)
  }

  private def fetch(url: String, ix: BifIndex, frame: Int): Unit = {
    val asked = generation
    loading = frame
    ByteRanges.picture(url, headers(), ix.start(frame), ix.length(frame), got => if (asked == generation) {
      loading = -1
      got match {
        case Some(address) =>
          kept = (frame, address) :: kept
          if (kept.size > TrickPlay.Kept) {
            kept.drop(TrickPlay.Kept).foreach(k => ByteRanges.forget(k._2))
            kept = kept.take(TrickPlay.Kept)
          }
          if (frame == wanted) answer(ThumbFrame(address, 1, 1, 0, 0, frame))
        case None => missed = frame :: missed
      }
      fetchNext(url, ix)
    } else got.foreach(address => ByteRanges.forget(address)))
  }
}

object TrickPlay {
  /** How many fetched frames are kept: a few seconds of a scrubber moved back and forth, a few hundred KB. */
  val Kept: Int = 24
}
