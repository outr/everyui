package everyui.audio

import everyui.render.AudioAccess
import reactify.{Channel, Val, Var}

/** Sound played as it arrives: chunks of 16-bit mono PCM at `sampleRate` Hz (text to speech's 16 or 24 kHz),
  * queued by [[play]] and played back to back without gaps. `drained` fires when the queue runs dry after the last
  * chunk; [[stop]] silences it at once and drops what is queued (the person has started talking over it), and fires
  * nothing. `playing` holds whether anything is queued or sounding; `volume` is 0.0 to 1.0.
  *
  * The web plays through Web Audio (buffers scheduled back to back on the context's clock), the desktop through a
  * Java Sound `SourceDataLine`, Android through a streaming `AudioTrack`. A Roku and an Apple TV play nothing
  * (`PcmPlayer.supported` is false): each chunk is over at once.
  *
  * A browser starts sound only after the person has touched the page (its autoplay policy): the page's audio is
  * resumed on the first tap or key, so start voice from a tap, and sound queued before that waits for it.
  *
  * {{{
  *   val player = new PcmPlayer(24000)
  *   player.drained.attach(_ => listen())
  *   speech.onAudio(bytes => player.play(Pcm.fromBytes(bytes)))
  *   // barge-in: the person has started talking over it
  *   if (chunk.level > 0.05 && player.playing()) player.stop()
  * }}} */
final class PcmPlayer(val sampleRate: Int) {
  private val queue = new PlaybackQueue
  private val sounding: Var[Boolean] = Var(false)
  private var closed: Boolean = false

  /** Whether anything is queued or sounding. */
  val playing: Val[Boolean] = sounding
  /** The queue has run dry after the last chunk played. */
  val drained: Channel[Unit] = Channel[Unit]
  /** How loud, from 0.0 (silent) to 1.0 (as it came). */
  val volume: Var[Double] = Var(1.0)

  private val output: PcmOutput = AudioAccess.output(sampleRate, number => ran(number))
  volume.attach(v => output.volume(v))

  /** Queues `samples` to play after what is queued. */
  def play(samples: Array[Short]): Unit = if (!closed && samples.nonEmpty) {
    val number = queue.queue()
    sounding @= true
    output.write(samples, number)
  }

  /** Silences it at once and drops everything queued. */
  def stop(): Unit = if (!closed) {
    queue.stop()
    output.flush()
    sounding @= false
  }

  /** Stops it and lets the platform's output go; it plays nothing after. */
  def close(): Unit = if (!closed) {
    stop()
    closed = true
    output.close()
  }

  private def ran(number: Long): Unit = if (queue.ended(number)) {
    sounding @= false
    drained @= ()
  }
}

object PcmPlayer {
  /** Whether this platform plays PCM as it arrives. */
  def supported: Boolean = AudioAccess.playable()
}
