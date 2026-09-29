package everyui.render

import android.media.{AudioAttributes, AudioFormat, AudioTrack}
import everyui.audio.PcmOutput

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

/** A player's output on Android: a streaming `AudioTrack` at the player's rate, as an assistant's speech (so it follows
  * the assistant's volume and ducks what else is playing), fed from a queue by a thread of its own in slices of 20 ms,
  * so a flush takes hold within one. The track's playback position against the frames written says when it has
  * played everything out. */
final class TrackOutput(sampleRate: Int, drained: Long => Unit) extends PcmOutput {
  private val queue = new LinkedBlockingQueue[(Array[Short], Long, Int)]()
  @volatile private var generation: Int = 0
  @volatile private var open: Boolean = true
  private val track: AudioTrack = {
    val least = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    new AudioTrack.Builder()
      .setAudioAttributes(new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build())
      .setAudioFormat(new AudioFormat.Builder()
        .setSampleRate(sampleRate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build())
      .setTransferMode(AudioTrack.MODE_STREAM)
      .setBufferSizeInBytes(math.max(least, sampleRate / 5 * 2))
      .build()
  }
  track.play()

  private val writer = new Thread(() => feed(), "everyui-pcm-out")
  writer.setDaemon(true)
  writer.start()

  def write(samples: Array[Short], number: Long): Unit = queue.put((samples, number, generation))

  def flush(): Unit = synchronized {
    generation += 1
    queue.clear()
    track.pause()
    track.flush()
    track.play()
  }

  def volume(level: Double): Unit = track.setVolume(math.max(0.0, math.min(1.0, level)).toFloat)

  def close(): Unit = synchronized {
    open = false
    generation += 1
    queue.clear()
    try {
      track.pause()
        track.flush()
        track.release()
      }
      catch { case _: Throwable => () }
    }
  // the playback head counts frames as an unsigned 32-bit number
  private def head(): Long = track.getPlaybackHeadPosition.toLong & 0xffffffffL

  private def feed(): Unit = {
    val slice = math.max(1, sampleRate / 50)
    var written = 0L
    var base = head()
    var sinceFlush = generation
    var last: Option[Long] = None
    try {
      while (open) {
        val item = queue.poll(10, TimeUnit.MILLISECONDS)
        synchronized {
          if (open && sinceFlush != generation) {
            // flushed: the head starts again from where it stands now
            sinceFlush = generation
            base = head()
            written = 0L
            last = None
          }
        }
        if (item != null && item._3 == generation) {
          val (samples, number, made) = item
          var at = 0
          while (at < samples.length && made == generation && open) {
            val n = math.min(slice, samples.length - at)
            val wrote = track.write(samples, at, n, AudioTrack.WRITE_BLOCKING)
            if (wrote > 0) {
              written += wrote
              at += wrote
            }
            else at = samples.length
          }
          if (made == generation) last = Some(number)
        }
        else if (item == null && open && last.nonEmpty && head() - base >= written) {
          val number = last.get
          last = None
          Platform.later(() => drained(number))
        }
      }
    }
    catch {
      case t: Throwable => if (open) Platform.logError("audio: the output stopped", t)
    }
  }
}
