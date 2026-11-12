package everyui.trickplay

/** A BIF file's index (Roku's Base Index Frames, as Plex and Emby serve trick-play thumbnails): when each frame is,
  * and where its JPEG is in the file. The file begins with a 64 byte header (the magic `89 42 49 46 0d 0a 1a 0a`, a
  * version, the number of frames and the milliseconds each timestamp counts, 0 meaning 1000), then an entry of two
  * little-endian 32 bit numbers per frame (its timestamp, its offset in the file), then one more whose offset is where
  * the last frame ends; the frames follow. A frame runs from its offset to the next entry's.
  *
  * `times` are each frame's timestamps (in units of `unitMs`), `offsets` the entries' offsets, one more than the
  * frames. */
case class BifIndex(unitMs: Double, times: Vector[Int], offsets: Vector[Int]) {
  /** How many frames. */
  def count: Int = times.size

  /** The frame shown at `seconds`: the last one whose time is not past it, the first before it and the last after the
    * end; -1 where there are none. */
  def frameAt(seconds: Double): Int =
    if (times.isEmpty) -1
    else {
      val ms = seconds * 1000.0
      // the first frame whose time is past `ms`, by halves, and the one before it
      var low = 0
      var high = times.size
      while (low < high) {
        val mid = (low + high) / 2
        if (times(mid) * unitMs <= ms) low = mid + 1 else high = mid
      }
      math.max(0, low - 1)
    }

  /** Where frame `i`'s JPEG starts in the file. */
  def start(i: Int): Int = offsets(i)

  /** How many bytes frame `i`'s JPEG is. */
  def length(i: Int): Int = offsets(i + 1) - offsets(i)
}

object BifIndex {
  /** The header's size, which the index follows. */
  val HeaderSize: Int = 64

  /** How much of the file to ask for first: the header and the index of a film of two hours at a frame every two
    * seconds; a longer index is asked for again whole ([[indexEnd]]). */
  val FirstRead: Int = HeaderSize + 8 * 3602

  /** Bytes as a BIF's header has them: a little-endian unsigned 32 bit number at `at`, as an Int (past 2^31 it comes
    * out negative, as the index's last timestamp does). Each byte is taken as 0 to 255 whether it came signed (a JVM's,
    * a browser's) or not (a Roku's byte array). */
  def word(bytes: Array[Byte], at: Int): Int =
    (bytes(at) & 255) | ((bytes(at + 1) & 255) << 8) | ((bytes(at + 2) & 255) << 16) | ((bytes(at + 3) & 255) << 24)

  /** Whether `bytes` begin as a BIF does. */
  def isBif(bytes: Array[Byte]): Boolean =
    bytes.length >= HeaderSize && (bytes(0) & 255) == 0x89 && (bytes(1) & 255) == 0x42 && (bytes(2) & 255) == 0x49 &&
      (bytes(3) & 255) == 0x46 && (bytes(4) & 255) == 0x0d && (bytes(5) & 255) == 0x0a &&
      (bytes(6) & 255) == 0x1a && (bytes(7) & 255) == 0x0a

  /** How many bytes from the start the header and the whole index take, from a header; -1 where `bytes` are not one. */
  def indexEnd(bytes: Array[Byte]): Int =
    if (!isBif(bytes)) -1
    else {
      val frames = word(bytes, 12)
      if (frames < 0) -1 else HeaderSize + 8 * (frames + 1)
    }

  /** The index of the BIF whose first bytes are `bytes`, which must reach past its index ([[indexEnd]]); None where
    * they do not, are not a BIF, or name frames that run backwards. */
  def parse(bytes: Array[Byte]): Option[BifIndex] = {
    val end = indexEnd(bytes)
    if (end < 0 || bytes.length < end) None
    else {
      val frames = word(bytes, 12)
      val unit = word(bytes, 16)
      val times = Vector.tabulate(frames)(i => word(bytes, HeaderSize + 8 * i))
      val offsets = Vector.tabulate(frames + 1)(i => word(bytes, HeaderSize + 8 * i + 4))
      var ordered = true
      var i = 0
      while (i < frames) {
        if (offsets(i + 1) < offsets(i) || offsets(i) < end) ordered = false
        i += 1
      }
      if (ordered) Some(BifIndex(if (unit <= 0) 1000.0 else unit.toDouble, times, offsets)) else None
    }
  }
}
