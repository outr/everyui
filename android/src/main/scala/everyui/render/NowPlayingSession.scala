package everyui.render

import android.media.MediaMetadata
import android.media.session.{MediaSession, PlaybackState}
import everyui.component.NowPlaying

/** What is playing, told to Android: a media session, which is what the television's own playing card, its assistant
  * ("pause", "next") and a Bluetooth remote's transport keys all go through, and what a Fire TV's Alexa asks.
  *
  * The platform's own session, not Media3's: this build merges no Android resources, and Media3's session brings a
  * notification and its icons along. What arrives from the platform is turned into the keys of the same names and fed
  * to the app where a remote's keys go (Host.key), so a screen that answers the remote's Play and Next answers the
  * assistant's too, with nothing further said. A seek to a place is the one command a key cannot carry; it goes
  * straight to the player, which the screen hears through its playhead as it does any other seek.
  *
  * Everything runs on the main thread: the session is made there, and calls back on it. */
class NowPlayingSession(position: () => Double, seek: Double => Unit) {
  private val session = new MediaSession(Host.activity, "everyui")
  private var about = NowPlaying()
  private var playing = false

  session.setCallback(new MediaSession.Callback {
    override def onPlay(): Unit = press("MediaPlay")
    override def onPause(): Unit = press("MediaPause")
    override def onStop(): Unit = press("MediaStop")
    override def onSkipToNext(): Unit = press("MediaTrackNext")
    override def onSkipToPrevious(): Unit = press("MediaTrackPrevious")
    override def onFastForward(): Unit = press("MediaFastForward")
    override def onRewind(): Unit = press("MediaRewind")
    override def onSeekTo(millis: Long): Unit = {
      seek(millis / 1000.0)
      state(playing)
    }
  })
  // what Android 8 does without being asked; before it, a session hears no transport unless it says it wants to
  if (android.os.Build.VERSION.SDK_INT < 26) session.setFlags(3)
  session.setActive(true)

  /** Its name, who it is by, what it is from and its picture. The picture arrives when it has loaded, and only if it
    * is still what is playing; it is made small first, since the whole of it crosses to another process. */
  def describe(what: NowPlaying): Unit = {
    about = what
    session.setMetadata(metadata(what, None))
    if (what.art.startsWith("http")) Host.bitmapThen(what.art) { b =>
      if (b != null && (about eq what)) {
        val side = 320.0 / math.max(1, math.max(b.getWidth, b.getHeight))
        val small =
          if (side >= 1.0) b
          else android.graphics.Bitmap.createScaledBitmap(b, math.max(1, (b.getWidth * side).toInt),
            math.max(1, (b.getHeight * side).toInt), true)
        session.setMetadata(metadata(what, Some(small)))
      }
    }
  }

  /** Whether it is playing, and where it has reached: said when either changes, and Android runs the clock between. */
  def state(on: Boolean): Unit = {
    playing = on
    session.setPlaybackState(new PlaybackState.Builder()
      .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE |
        PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_FAST_FORWARD |
        PlaybackState.ACTION_REWIND | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
      .setState(if (on) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
        math.max(0L, math.round(position() * 1000.0)), if (on) 1f else 0f)
      .build())
  }

  def release(): Unit =
    try {
      session.setActive(false)
      session.release()
    }
    catch { case t: Throwable => Platform.logError("media: session", t) }

  private def metadata(what: NowPlaying, art: Option[android.graphics.Bitmap]): MediaMetadata = {
    val built = new MediaMetadata.Builder()
      .putString(MediaMetadata.METADATA_KEY_TITLE, what.title)
      .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, what.title)
    if (what.artist.nonEmpty) {
      built.putString(MediaMetadata.METADATA_KEY_ARTIST, what.artist)
      built.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, what.artist)
    }
    if (what.album.nonEmpty) built.putString(MediaMetadata.METADATA_KEY_ALBUM, what.album)
    if (what.art.nonEmpty) built.putString(MediaMetadata.METADATA_KEY_ART_URI, what.art)
    if (what.seconds > 0.0) built.putLong(MediaMetadata.METADATA_KEY_DURATION, math.round(what.seconds * 1000.0))
    art.foreach(b => built.putBitmap(MediaMetadata.METADATA_KEY_ART, b))
    built.build()
  }

  /** A command from the platform, as the remote's key of the same name: pressed and let go. */
  private def press(name: String): Unit = {
    val hook = Host.key
    if (hook != null) {
      hook(name, true)
      hook(name, false)
    }
  }
}
