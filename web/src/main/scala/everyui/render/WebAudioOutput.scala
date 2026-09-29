package everyui.render

import everyui.audio.{PcmOutput, PlaybackSchedule}

import scala.scalajs.js
import scala.scalajs.js.typedarray.Float32Array

/** A player's output on the web: each chunk an AudioBuffer at the player's rate (the context resamples it), started
  * where the one before ends on the context's clock ([[PlaybackSchedule]]), through a gain for the volume. The last
  * chunk's end is the output run dry; a flush stops every buffer still to play. */
final class WebAudioOutput(sampleRate: Int, drained: Long => Unit) extends PcmOutput {
  private val ctx: js.Dynamic = AudioAccess.context()
  private val gain: js.Dynamic = ctx.createGain()
  gain.connect(ctx.destination)
  private val schedule = new PlaybackSchedule()
  private var sounding: List[js.Dynamic] = Nil
  private var last: Long = 0L

  def write(samples: Array[Short], number: Long): Unit = {
    AudioAccess.resume()
    val buffer = ctx.createBuffer(1, samples.length, sampleRate)
    val data = new Float32Array(samples.length)
    var i = 0
    while (i < samples.length) {
      data(i) = everyui.audio.Pcm.toFloat(samples(i))
      i += 1
    }
    buffer.copyToChannel(data, 0)
    val source = ctx.createBufferSource()
    source.buffer = buffer
    source.connect(gain)
    last = number
    sounding = source :: sounding
    source.onended = (() => {
      sounding = sounding.filterNot(_ eq source)
      source.disconnect()
      if (number == last) drained(number)
    }): js.Function0[Unit]
    source.start(schedule.start(ctx.currentTime.asInstanceOf[Double], samples.length.toDouble / sampleRate))
  }

  def flush(): Unit = {
    sounding.foreach(s => try s.stop() catch { case _: Throwable => () })
    sounding = Nil
    schedule.reset()
  }

  def volume(level: Double): Unit = gain.gain.value = math.max(0.0, math.min(1.0, level))

  def close(): Unit = {
    flush()
    gain.disconnect()
  }
}
