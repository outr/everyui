package everyui.audio

/** A stream of samples at `from` Hz turned into one at `to` Hz, a block at a time, carrying where it was between
  * blocks so the joins make no clicks. Down (a microphone's 48 kHz to speech's 16 kHz) each sample out is the average
  * of the stretch of input it stands for, which keeps most of what is above the new rate's limit from folding back
  * in as noise; up, it interpolates between neighbours. The same rate passes through.
  *
  * {{{
  *   val down = new Resampler(48000, 16000)
  *   val out = down.push(block)   // a third as many, give or take one
  * }}} */
final class Resampler(val from: Int, val to: Int) {
  /** Input samples to one output sample. */
  private val step: Double = from.toDouble / to
  // down: the input still to go into the output sample being gathered, and what has gone in so far
  private var room: Double = step
  private var gathered: Double = 0.0
  // up: where the next output sample falls, counted from `last` (the previous block's last sample) as 0
  private var at: Double = 1.0
  private var last: Float = 0.0f

  /** The samples `block` (-1.0 to 1.0) makes at the new rate. */
  def push(block: Array[Float]): Array[Float] =
    if (from == to) cut(block, block.length)
    else if (step > 1.0) down(block)
    else up(block)

  private def down(block: Array[Float]): Array[Float] = {
    val out = new Array[Float]((block.length / step).toInt + 2)
    var n = 0
    var i = 0
    while (i < block.length) {
      val x = block(i)
      var left = 1.0
      while (left > 1e-9) {
        val take = if (room < left) room else left
        gathered += x * take
        room -= take
        left -= take
        if (room <= 1e-9) {
          out(n) = (gathered / step).toFloat
          n += 1
          gathered = 0.0
          room += step
        }
      }
      i += 1
    }
    cut(out, n)
  }

  private def up(block: Array[Float]): Array[Float] = {
    val out = new Array[Float](((block.length + 1) / step).toInt + 2)
    var n = 0
    while (at <= block.length) {
      val i = at.toInt
      val f = (at - i).toFloat
      val a = if (i == 0) last else block(i - 1)
      val b = if (i >= block.length) a else block(i)
      out(n) = a + (b - a) * f
      n += 1
      at += step
    }
    if (block.nonEmpty) {
      at -= block.length
      last = block(block.length - 1)
    }
    cut(out, n)
  }

  private def cut(out: Array[Float], n: Int): Array[Float] = {
    val kept = new Array[Float](n)
    var i = 0
    while (i < n) {
      kept(i) = out(i)
      i += 1
    }
    kept
  }
}
