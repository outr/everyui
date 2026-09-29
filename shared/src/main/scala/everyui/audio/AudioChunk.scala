package everyui.audio

/** A stretch of captured sound: `samples` 16-bit mono PCM at `sampleRate` Hz, and its `level` (the root mean square,
  * 0.0 to 1.0) for a meter. `bytes` is the same as little-endian bytes, the usual wire form for speech to text. */
case class AudioChunk(samples: Array[Short], sampleRate: Int, level: Double) {
  /** The samples as little-endian bytes, two to a sample. */
  def bytes: Array[Byte] = Pcm.toBytes(samples)

  /** How long it lasts. */
  def seconds: Double = samples.length.toDouble / sampleRate
}
