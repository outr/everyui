package everyui.audio

/** A sound player where the device has one player to share (a Roku): what it is asked (a source, playing or not, an
  * effect) routed to its [[SoundOutput]]. Played through the player, it takes its turn on `stack`, from whoever has it,
  * and gives it back as it stops, ends or fails; an effect whose source the platform mixes ([[EffectSource.mixes]])
  * plays over everything instead, once each time its source is set or it is turned on, from its start, and never takes
  * the player. An effect it cannot mix is reported and played through the player. */
class SoundTurns(out: SoundOutput, stack: PlayerStack) extends SoloPlayer {
  private var url: String = ""
  private var startAt: Double = 0.0
  private var volume: Double = 1.0
  private var wanted: Boolean = true
  private var effect: Boolean = false

  def soloName(): String = {
    val q = url.indexOf('?')
    val path = if (q >= 0) url.substring(0, q) else url
    out.id() + " " + path.substring(path.lastIndexOf('/') + 1)
  }
  def yieldPlayer(): Unit = out.yieldPlayer()
  def reclaimPlayer(): Unit = out.reclaimPlayer()

  /** Played as an effect from the next source on. */
  def setEffect(on: Boolean): Unit = effect = on
  def setVolume(level: Double): Unit = volume = level
  /** Where the next source starts: an effect asked to start part way (a player woken where it left off) stays quiet. */
  def setStart(seconds: Double): Unit = startAt = seconds

  def setSource(source: String): Unit = {
    url = source
    val from = startAt
    startAt = 0.0
    if (effect && source.nonEmpty && !EffectSource.mixes(source))
      out.report("effect " + source, "audio: " + out.id() + " effect " + source + " is " + EffectSource.unfit(source) +
        ", which a SoundEffect cannot play: played through the player instead")
    if (mixing()) {
      if (wanted && from <= 0.0) out.playEffect(source, volume) else out.loadEffect(source)
    } else {
      // the player taken before this one starts, and given up once it has stopped
      if (source.nonEmpty && wanted && stack.take(this)) out.handedOff()
      out.source(source)
      if (source.isEmpty || !wanted) stack.leave(this)
    }
  }

  def setPlaying(on: Boolean): Unit = {
    val was = wanted
    wanted = on
    if (mixing()) {
      // played as it is turned on, never again by being told what it already is
      if (on && !was) out.playEffect(url, volume) else if (!on) out.stopEffect(url)
    } else {
      if (on && url.nonEmpty && stack.take(this)) out.handedOff()
      out.playing(on)
      if (!on) stack.leave(this)
    }
  }

  /** What the player said: done with it, at its end or failing, it goes back to the one it was taken from. */
  def heard(event: String): Unit = if (event == "ended" || event == "error") stack.leave(this)

  /** Its player let go (stopped first, by the caller). */
  def released(): Unit = stack.leave(this)

  // an effect whose source the platform mixes; any other goes through the player
  private def mixing(): Boolean = effect && EffectSource.mixes(url)
}
