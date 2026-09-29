package everyui.render

import everyui.audio.{CaptureEnd, PcmOutput, SilentOutput}

/** Voice audio (`everyui.audio`): a remote's microphone belongs to the system's own search, and there is no way to
  * play raw PCM, so this platform does neither. A capture ends at once as unavailable; what is played is over at
  * once, unheard. */
object AudioAccess {
  def capturable(): Boolean = false

  def playable(): Boolean = false

  def capture(rate: Int, echoCancellation: Boolean, opened: Int => Unit, heard: Array[Float] => Unit,
              ended: CaptureEnd => Unit): () => Unit = {
    ended(CaptureEnd.Unavailable)
    () => ()
  }

  def output(sampleRate: Int, drained: Long => Unit): PcmOutput = new SilentOutput(drained)
}
