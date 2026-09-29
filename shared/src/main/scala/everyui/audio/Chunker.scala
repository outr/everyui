package everyui.audio

/** Samples gathered into chunks of `frames` each, however they arrive: a block too small waits for the next, a block
  * too big makes several. What is left at the end comes out as a shorter last chunk ([[rest]]).
  *
  * {{{
  *   val chunks = new Chunker(2048)
  *   chunks.push(resampled).foreach(send)
  *   chunks.rest().foreach(send)   // on stopping
  * }}} */
final class Chunker(val frames: Int) {
  private var filling: Array[Short] = new Array[Short](frames)
  private var count: Int = 0

  /** The chunks `block` (-1.0 to 1.0) completes, in order, as 16-bit samples. */
  def push(block: Array[Float]): List[Array[Short]] = {
    var done = List.empty[Array[Short]]
    var i = 0
    while (i < block.length) {
      filling(count) = Pcm.toShort(block(i))
      count += 1
      if (count == frames) {
        done = filling :: done
        filling = new Array[Short](frames)
        count = 0
      }
      i += 1
    }
    done.reverse
  }

  /** What is gathered and not yet a whole chunk, taken out; none when nothing is. */
  def rest(): Option[Array[Short]] =
    if (count == 0) None
    else {
      val part = new Array[Short](count)
      var i = 0
      while (i < count) {
        part(i) = filling(i)
        i += 1
      }
      count = 0
      Some(part)
    }

  /** How many samples are waiting for a chunk. */
  def waiting: Int = count
}
