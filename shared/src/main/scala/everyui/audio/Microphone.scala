package everyui.audio

import everyui.render.AudioAccess

/** The microphone, as 16-bit mono PCM chunks at the rate an app asks for (16 kHz for most speech to text), cut into
  * chunks of about `frames` samples (2048 at 16 kHz is 128 ms), each with its level for a meter. The platform records
  * at its own rate and it is resampled here. Everything is heard on the thread that draws.
  *
  * The web records through `getUserMedia` and an AudioWorklet, the desktop through Java Sound's `TargetDataLine`,
  * Android through `AudioRecord`. A Roku and an Apple TV do not (`supported` is false, and a capture ends at once with
  * [[CaptureEnd.Unavailable]]).
  *
  * The first capture asks for the microphone where the platform asks (the browser's prompt, Android's RECORD_AUDIO,
  * which the app's manifest must declare); refused, it ends with [[CaptureEnd.Denied]]. `echoCancellation` asks the
  * platform to take out what the app itself is playing, for talking over a [[PcmPlayer]]: `getUserMedia`'s
  * echoCancellation (with its noise suppression and gain control), Android's VOICE_COMMUNICATION source and its
  * AcousticEchoCanceler. Java Sound has none, so on the desktop it is the system's (a headset, or the sound server's
  * echo cancelling).
  *
  * {{{
  *   val capture = Microphone.capture(16000, 2048)(
  *     chunk => { meter @= chunk.level; transcriber.send(chunk.bytes) },
  *     why => if (why == CaptureEnd.Denied) Toast.show("The microphone is off"))
  *   ...
  *   capture.stop()
  * }}} */
object Microphone {
  /** Whether this platform can record from a microphone at all (whether one is plugged in is found on capturing). */
  def supported: Boolean = AudioAccess.capturable()

  /** Starts recording: `chunk` hears each chunk as it fills, and `ended` why it ended. */
  def capture(sampleRate: Int = 16000, frames: Int = 2048, echoCancellation: Boolean = true)
             (chunk: AudioChunk => Unit, ended: CaptureEnd => Unit): Capture = {
    val capture = new Capture(sampleRate, frames, chunk, ended)
    capture.begin(AudioAccess.capture(sampleRate, echoCancellation, capture.opened, capture.heard, capture.end))
    capture
  }
}
