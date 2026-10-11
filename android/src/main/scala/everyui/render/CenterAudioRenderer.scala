package everyui.render

import android.content.Context
import android.os.Handler
import androidx.media3.common.{Format, MimeTypes}
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.audio.{AudioRendererEventListener, MediaCodecAudioRenderer}
import androidx.media3.exoplayer.mediacodec.{MediaCodecAdapter, MediaCodecSelector}

/** Media3's audio renderer, which decides once, as a format comes, whether to pass it to the sink as it is or decode
  * it, and so would keep passing a receiver its E-AC-3 after the centre mix asked for it decoded ([[CenterGate]]), or
  * keep decoding it after it was set back to 0. This one decides again when the gate changes, at once and in place:
  * it hands itself the format it is playing as though it had just come, which is Media3's own way through a change
  * of format mid-stream. What is already read goes out as it was; the next sample is decoded (or passed); the sink
  * plays out what it holds and makes a new AudioTrack for the new kind, so the gap is the sink's change of track,
  * and the position is kept. Protected sound, never passed through here, is left to Media3. */
class CenterAudioRenderer(context: Context, codecs: MediaCodecAdapter.Factory, selector: MediaCodecSelector,
                          fallback: Boolean, handler: Handler, events: AudioRendererEventListener,
                          gate: CenterGate)
  extends MediaCodecAudioRenderer(context, codecs, selector, fallback, handler, events, gate) {
  private var playing: Option[Format] = None
  private var seen = gate.changes

  override protected def onInputFormatChanged(holder: FormatHolder): androidx.media3.exoplayer.DecoderReuseEvaluation = {
    playing = if (holder.drmSession == null) Option(holder.format) else None
    super.onInputFormatChanged(holder)
  }

  override def render(positionUs: Long, elapsedRealtimeUs: Long): Unit = {
    val now = gate.changes
    if (now != seen) {
      seen = now
      playing.foreach { format =>
        val passing = isBypassEnabled
        val decoding = getCodec != null
        if (!MimeTypes.AUDIO_RAW.equals(format.sampleMimeType) && (passing || decoding) &&
          passing != gate.supportsFormat(format)) {
          Platform.log("media: centre mix: " + (if (passing) "decoding " + CenterGate.named(format) + " here from now"
          else "passing " + CenterGate.named(format) + " through as it is from now"))
          val holder = new FormatHolder
          holder.format = format
          onInputFormatChanged(holder)
        }
      }
    }
    super.render(positionUs, elapsedRealtimeUs)
  }

  override protected def onDisabled(): Unit = {
    playing = None
    super.onDisabled()
  }
}
