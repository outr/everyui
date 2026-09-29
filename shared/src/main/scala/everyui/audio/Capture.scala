package everyui.audio

/** A microphone recording, from [[Microphone.capture]]: the platform's blocks at its own rate, resampled to
  * `sampleRate` and cut into chunks of `frames`, each handed to `chunk` with its level. [[stop]] ends it; `ended`
  * hears why it ended, once, after the last chunk (the samples short of a whole chunk come as a shorter last one). */
final class Capture private[audio] (val sampleRate: Int, val frames: Int, chunk: AudioChunk => Unit,
                                    ended: CaptureEnd => Unit) {
  private val chunks = new Chunker(frames)
  private var resampler: Option[Resampler] = None
  private var over: Boolean = false
  private var halt: () => Unit = () => ()

  /** Whether it is still recording (or waiting for the microphone to open). */
  def running: Boolean = !over

  /** The rate the platform records at, once the microphone is open. */
  def deviceRate: Option[Int] = resampler.map(_.from)

  /** Stops recording: what was gathered comes as a last chunk, then `ended` hears [[CaptureEnd.Stopped]]. */
  def stop(): Unit = if (!over) {
    halt()
    end(CaptureEnd.Stopped)
  }

  private[audio] def begin(stop: () => Unit): Unit =
    if (over) stop() else halt = stop

  private[audio] def opened(rate: Int): Unit = if (!over) resampler = Some(new Resampler(rate, sampleRate))

  private[audio] def heard(block: Array[Float]): Unit = if (!over) {
    resampler.foreach { r =>
      val done = chunks.push(r.push(block))
      done.foreach(deliver)
    }
  }

  private[audio] def end(why: CaptureEnd): Unit = if (!over) {
    over = true
    chunks.rest().foreach(deliver)
    ended(why)
  }

  private def deliver(samples: Array[Short]): Unit = chunk(AudioChunk(samples, sampleRate, Pcm.level(samples)))
}
