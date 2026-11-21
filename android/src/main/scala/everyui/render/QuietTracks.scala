package everyui.render

import android.media.AudioTrack
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.audio.{AudioSink, DefaultAudioSink}

/** The AudioTracks one player makes, kept so the main thread can silence them itself ([[hush]]) when the player is
  * let go. Media3 stops and releases a player on its own thread, after whatever that thread is doing: a decoder being
  * made can hold it for seconds (a SHIELD making its mp3 decoder as WebView started), and a sound already going out
  * plays on meanwhile. An AudioTrack takes its volume and a pause from any thread, so the sound stops now, whenever
  * the player gets round to its stop; and a track made after the hush (the stalled thread starting its sound late) is
  * made silent. */
@annotation.nowarn("cat=deprecation")
class QuietTracks extends DefaultAudioSink.AudioTrackProvider {
  @volatile private var hushed = false
  private var made: List[AudioTrack] = Nil

  override def getAudioTrack(config: AudioSink.AudioTrackConfig, attributes: AudioAttributes, session: Int,
                             context: android.content.Context): AudioTrack = {
    val track = DefaultAudioSink.AudioTrackProvider.DEFAULT.getAudioTrack(config, attributes, session, context)
    synchronized { made = track :: made.filter(_.getState != AudioTrack.STATE_UNINITIALIZED) }
    if (hushed) QuietTracks.silence(track)
    track
  }

  /** Every track made so far silenced and paused, and any made from now on silent. */
  def hush(): Unit = {
    hushed = true
    synchronized(made).foreach(QuietTracks.silence)
  }
}

object QuietTracks {
  private def silence(track: AudioTrack): Unit = {
    try track.setVolume(0f) catch { case _: Throwable => () }
    try if (track.getPlayState == AudioTrack.PLAYSTATE_PLAYING) track.pause() catch { case _: Throwable => () }
  }
}
