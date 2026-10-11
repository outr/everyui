package everyui.audio

/** What the centre mix (MediaSupport.centerMix) does with a stream on its way out, from what the stream is and what
  * the output takes, apart from any player so it can be decided, and checked, with the output's capabilities given.
  *
  * A stream with a centre among more than two channels is folded to two where the output plays two ([[Fold]]), and
  * keeps every channel, its centre raised in place, where the output plays more or says nothing ([[Lift]]); anything
  * else is left as it is ([[Leave]]). An encoded stream (AC-3, E-AC-3, DTS) that the output would take as it is, to
  * decode itself, is decoded here instead while the centre mix is at a level other than 0, where there is a decoder
  * for it here ([[decodeHere]]), so the centre can be reached at all. */
enum CenterRoute {
  /** Nothing to do: no centre among more than two channels. */
  case Leave
  /** Folded to two channels, the centre at its level in the fold ([[Downmix.mix]]). */
  case Fold
  /** Every channel kept, the centre raised or lowered in place ([[Downmix.lift]]). */
  case Lift
}

object CenterRoute {
  // Android's PCM encodings: 16-bit, float, 24-bit packed and 32-bit
  private val Pcm: Set[Int] = Set(2, 4, 21, 22)

  /** What becomes of a decoded stream of `channels` going out to an output that plays `output` channels (0 where it
    * does not say). */
  def of(channels: Int, output: Int): CenterRoute =
    if (!Downmix.applies(channels)) Leave
    else if (output == 1 || output == 2) Fold
    else Lift

  /** Whether a stream the output would take encoded (`passes`) is decoded here instead: while the centre is at a
    * level other than 0, and only where there is a decoder for it here (`decodable`). At 0 it passes as ever. */
  def decodeHere(db: Double, passes: Boolean, decodable: Boolean): Boolean =
    Downmix.clamp(db) != 0.0 && passes && decodable

  /** Whether the centre mix can reach a stream: one decoded here always, an encoded one only where it could be
    * decoded here, since one passed through goes out as it came. */
  def supported(encoded: Boolean, decodable: Boolean): Boolean = !encoded || decodable

  /** The most channels an output takes as PCM: from its profiles where it gives them (each an Android encoding and its
    * channel masks; a Dolby or DTS profile is what it takes encoded, and does not count), else its channel counts,
    * else 0. */
  def pcmChannels(profiles: Seq[(Int, Seq[Int])], counts: Seq[Int]): Int = {
    val profiled = profiles.filter(p => Pcm.contains(p._1)).flatMap(_._2).map(m => Integer.bitCount(m))
      .foldLeft(0)(math.max)
    if (profiled > 0) profiled else counts.foldLeft(0)(math.max)
  }
}
