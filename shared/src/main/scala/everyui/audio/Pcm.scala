package everyui.audio

/** 16-bit PCM, as voice audio travels: samples as `Short`s (full scale is -32768 to 32767), and as the little-endian
  * bytes a server sends and takes (two to a sample, low byte first). Written as loops over indices, for the Roku's
  * compiler.
  *
  * {{{
  *   transcriber.send(Pcm.toBytes(chunk.samples))
  *   player.play(Pcm.fromBytes(bytes))
  * }}} */
object Pcm {
  /** `samples` as little-endian bytes. */
  def toBytes(samples: Array[Short]): Array[Byte] = {
    val bytes = new Array[Byte](samples.length * 2)
    var i = 0
    while (i < samples.length) {
      // in arithmetic rather than bit operations, which the Roku's compiler does not take
      val s = samples(i).toInt
      val u = if (s < 0) s + 65536 else s
      bytes(i * 2) = (u % 256).toByte
      bytes(i * 2 + 1) = (u / 256).toByte
      i += 1
    }
    bytes
  }

  /** Little-endian bytes as samples; an odd last byte is dropped. */
  def fromBytes(bytes: Array[Byte]): Array[Short] = {
    val samples = new Array[Short](bytes.length / 2)
    var i = 0
    while (i < samples.length) {
      val low = bytes(i * 2).toInt
      samples(i) = (bytes(i * 2 + 1).toInt * 256 + (if (low < 0) low + 256 else low)).toShort
      i += 1
    }
    samples
  }

  /** One sample from -1.0 to 1.0, clipped there. */
  def toShort(value: Float): Short = {
    val clipped = if (value > 1.0f) 1.0f else if (value < -1.0f) -1.0f else value
    if (clipped < 0.0f) (clipped * 32768.0f).toShort else (clipped * 32767.0f).toShort
  }

  /** One sample as -1.0 to 1.0. */
  def toFloat(sample: Short): Float = if (sample < 0) sample / 32768.0f else sample / 32767.0f

  /** The samples' loudness, their root mean square from 0.0 (silence) to 1.0 (full scale; a full-scale sine is about
    * 0.707). What a level meter shows. */
  def level(samples: Array[Short]): Double =
    if (samples.isEmpty) 0.0
    else {
      var sum = 0.0
      var i = 0
      while (i < samples.length) {
        val v = samples(i) / 32768.0
        sum += v * v
        i += 1
      }
      math.sqrt(sum / samples.length)
    }

  /** A sine at `frequency` Hz, `seconds` long at `sampleRate`, at `amplitude` (0.0 to 1.0) of full scale: a tone to
    * play, or to test with. */
  def tone(frequency: Double, seconds: Double, sampleRate: Int, amplitude: Double = 0.2): Array[Short] = {
    val samples = new Array[Short]((seconds * sampleRate).round.toInt)
    var i = 0
    while (i < samples.length) {
      samples(i) = toShort((math.sin(2.0 * math.Pi * frequency * i / sampleRate) * amplitude).toFloat)
      i += 1
    }
    samples
  }
}
