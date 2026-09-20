package everyui.render

import android.graphics.{Matrix, SurfaceTexture}
import android.net.Uri
import android.view.{Surface, TextureView}
import androidx.media3.common.{AudioAttributes, C, MediaItem, PlaybackParameters, Player, TrackSelectionOverride, Tracks, VideoSize}
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.{DefaultLoadControl, DefaultRenderersFactory, ExoPlayer}
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import everyui.component.MediaTrack

/** What a video or an audio node plays, Android edition: an ExoPlayer, drawing (for a video) into the node's
  * `TextureView`, which a transform fits to the box, filling it or whole inside it.
  *
  * It replaces `MediaPlayer`, which plays a file well enough and answers none of the questions a television asks of a
  * home network. Four things are only reachable here: what to send with the request, how long to wait for an answer,
  * how much to hold before playing, and how fast the bytes are actually arriving. The first is what lets a server
  * that will not take a token in the address recognise us at all; the rest are what tell a slow link from a dead one,
  * which a player that cannot measure has to guess at, and guesses wrong in the direction of "failed".
  *
  * Everything runs on the main thread, as the player's callbacks do. */
class ExoMedia(texture: Option[TextureView]) {
  private var player: Option[ExoPlayer] = None
  private var surface: Option[Surface] = None
  private var source = ""
  private var playing = true
  private var looping = false
  private var muted = false
  private var volume = 1.0
  private var cover = true
  private var rate = 1.0
  private var videoWidth = 0
  private var videoHeight = 0
  private var said = false
  private var listener: String => Unit = _ => ()
  private var sending: Map[String, String] = Map.empty
  private var holding: Buffering = Buffering.Balanced
  /** The factory the loads come out of, so what a read is given can change between them. */
  private var reading: Option[DefaultHttpDataSource.Factory] = None
  /** Whether reads are currently on the short leash a lifted pause puts them on. */
  private var sharp = false
  /** How far the buffer had reached when it was last seen growing, and when that was. */
  private var reached = 0L
  private var grew = 0.0
  private var watching = 0

  texture.foreach { t =>
    t.setSurfaceTextureListener(new TextureView.SurfaceTextureListener {
      override def onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int): Unit = {
        surface = Some(new Surface(st))
        player.foreach(_.setVideoSurface(surface.get))
        fit()
      }
      override def onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int): Unit = fit()
      override def onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = {
        player.foreach(_.setVideoSurface(null))
        surface.foreach(_.release())
        surface = None
        true
      }
      override def onSurfaceTextureUpdated(st: SurfaceTexture): Unit = ()
    })
  }

  /** What to send with the request, and with every request that comes out of it: an HLS playlist's segments and keys
    * are fetched by the same factory, so they carry it too. Kept for the next source: the factory is built with the
    * player. */
  def setHeaders(headers: Map[String, String]): Unit = sending = headers

  /** How much to hold before playing, and how long to wait for bytes: see [[Buffering]]. Takes effect on the next
    * source, since a load control belongs to the player it was built with. */
  def setBuffering(named: String): Unit = holding = Buffering.of(named)

  def setSource(url: String): Unit = if (url != source) {
    source = url
    close()
    if (url.nonEmpty) {
      try {
        val http = new DefaultHttpDataSource.Factory()
          .setAllowCrossProtocolRedirects(true)
          .setConnectTimeoutMs(holding.connectMs)
          .setReadTimeoutMs(holding.readMs)
        reading = Some(http)
        if (sending.nonEmpty) {
          val map = new java.util.HashMap[String, String]()
          sending.foreach(h => map.put(h._1, h._2))
          http.setDefaultRequestProperties(map)
        }
        val load = new DefaultLoadControl.Builder()
          .setBufferDurationsMs(holding.minMs, holding.maxMs, holding.startMs, holding.resumeMs)
          .build()
        val made = new ExoPlayer.Builder(Host.activity)
          .setRenderersFactory(new DefaultRenderersFactory(Host.activity)
            .setEnableDecoderFallback(true))
          .setTrackSelector(new DefaultTrackSelector(Host.activity))
          // how long one load may go on failing before it is lost is the profile's patience, and the number of
          // attempts follows from it rather than being picked: the viewer is watching a clock, not a counter
          .setMediaSourceFactory(new DefaultMediaSourceFactory(http)
            .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy {
              override def getMinimumLoadableRetryCount(dataType: Int): Int =
                math.max(3, holding.patienceMs / math.max(1000, holding.readMs))
            }))
          .setLoadControl(load)
          .build()
        made.setAudioAttributes(new AudioAttributes.Builder()
          .setUsage(C.USAGE_MEDIA)
          .setContentType(if (texture.nonEmpty) C.AUDIO_CONTENT_TYPE_MOVIE else C.AUDIO_CONTENT_TYPE_MUSIC)
          .build(), false)
        surface.foreach(made.setVideoSurface)
        made.setRepeatMode(if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF)
        made.setVolume(if (muted) 0f else volume.toFloat)
        if (rate != 1.0) made.setPlaybackParameters(new PlaybackParameters(rate.toFloat))
        made.addListener(new Player.Listener {
          override def onPlaybackStateChanged(state: Int): Unit = {
            // READY is the first moment there is a picture to show, and the only one worth saying: it is said once,
            // because every rebuffer passes through READY again on its way back
            if (state == Player.STATE_READY && !said) {
              said = true
              listener("ready")
            }
            else if (state == Player.STATE_ENDED && !looping) listener("ended")
          }
          override def onVideoSizeChanged(size: VideoSize): Unit = {
            videoWidth = size.width
            videoHeight = size.height
            fit()
          }
          override def onPlayerError(error: androidx.media3.common.PlaybackException): Unit = {
            Platform.log("media: " + url + " failed (" + error.getErrorCodeName + ")")
            listener("error")
          }
        })
        made.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        made.prepare()
        made.setPlayWhenReady(playing)
        player = Some(made)
        watch()
      } catch {
        case t: Throwable =>
          Platform.logError("media: " + url, t)
          listener("error")
      }
    }
  }

  def setPlaying(on: Boolean): Unit = {
    // a pause being lifted is the one moment a short read timeout is the right instrument: a connection nobody has
    // asked anything of for a while may have been dropped by the server or by whatever sits in front of it, with
    // nothing said either way, and the only way to find out is to ask and not wait long. It is the wrong instrument
    // for every other moment, which is why it goes back to the profile's own patience as soon as bytes arrive
    if (on && !playing && said) leash(true)
    playing = on
    player.foreach(_.setPlayWhenReady(on))
    if (on) watch()
  }

  /** Reads on the short leash of a lifted pause, or back on the profile's own. */
  private def leash(short: Boolean): Unit = if (short != sharp) {
    sharp = short
    reading.foreach(_.setReadTimeoutMs(if (short) holding.idleMs else holding.readMs))
  }

  def setLoop(on: Boolean): Unit = {
    looping = on
    player.foreach(_.setRepeatMode(if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF))
  }

  def setMuted(on: Boolean): Unit = {
    muted = on
    loudness()
  }

  def setVolume(v: Double): Unit = {
    volume = v
    loudness()
  }

  def setCover(on: Boolean): Unit = {
    cover = on
    fit()
  }

  def setRate(r: Double): Unit = {
    rate = r
    player.foreach(p => try p.setPlaybackParameters(new PlaybackParameters(r.toFloat))
    catch { case t: Throwable => Platform.logError("media: rate " + r.toString, t) })
  }

  /** The languages it can be heard and read in, as the stream carries them. The id is the group's index and the
    * track's within it, which is what [[selectTrack]] takes back. */
  def tracks(): List[MediaTrack] = player.toList.flatMap { p =>
    try {
      val groups = p.getCurrentTracks.getGroups
      (0 until groups.size()).toList.flatMap { g =>
        val group = groups.get(g)
        val kind = group.getType match {
          case C.TRACK_TYPE_AUDIO => MediaTrack.Audio
          case C.TRACK_TYPE_TEXT  => MediaTrack.Text
          case _                  => ""
        }
        if (kind.isEmpty) Nil
        else (0 until group.length).toList.flatMap { t =>
          if (!group.isTrackSupported(t)) Nil
          else {
            val format = group.getTrackFormat(t)
            val language = Option(format.language).map(_.trim).filterNot(l => l.isEmpty || l == "und").getOrElse("")
            val label = Option(format.label).map(_.trim).getOrElse("")
            List(MediaTrack(g.toString + ":" + t.toString, kind, language, label))
          }
        }
      }
    }
    catch { case t: Throwable => Platform.logError("media: tracks", t); Nil }
  }

  /** Hear or read a track, by the id it was given out under. An empty id means the stream's own choice for sound,
    * and none at all for text. */
  def selectTrack(kind: String, id: String): Unit = player.foreach { p =>
    try {
      val which = if (kind == MediaTrack.Text) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
      val from = p.getTrackSelectionParameters.buildUpon().clearOverridesOfType(which)
      val chosen = id.split(':')
      if (id.isEmpty) {
        // no text track at all is a choice the selector has a word for; sound falls back to what the stream says
        from.setTrackTypeDisabled(which, kind == MediaTrack.Text)
      }
      else if (chosen.length == 2) {
        for (g <- chosen(0).toIntOption; t <- chosen(1).toIntOption) {
          val groups = p.getCurrentTracks.getGroups
          if (g >= 0 && g < groups.size()) {
            from.setTrackTypeDisabled(which, false)
            from.addOverride(new TrackSelectionOverride(groups.get(g).getMediaTrackGroup, t))
          }
        }
      }
      p.setTrackSelectionParameters(from.build())
    }
    catch { case t: Throwable => Platform.logError("media: track " + kind + " " + id, t) }
  }

  /** How large the picture is, as the decoder reports it. */
  def size(): (Double, Double) = (videoWidth.toDouble, videoHeight.toDouble)

  /** What the link has been carrying, in bits a second, as the player has measured it — or 0 where it has not
    * measured anything yet. What a retry should ask for is a fraction of this, rather than a number chosen in
    * advance and equally wrong for a link managing thirty megabits and one managing two. */
  def bandwidth(): Long =
    try androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.getSingletonInstance(Host.activity).getBitrateEstimate
    catch { case _: Throwable => 0L }

  def position(): Double = player.map(_.getCurrentPosition / 1000.0).getOrElse(0.0)

  def seek(seconds: Double): Unit = player.foreach(_.seekTo(math.round(seconds * 1000.0)))

  def onEvent(f: String => Unit): Unit = listener = f

  def release(): Unit = {
    source = ""
    close()
  }

  private def loudness(): Unit = player.foreach(_.setVolume(if (muted) 0f else volume.toFloat))

  /** The watch on whether anything is still arriving, once a second while there is a player.
    *
    * What it measures is PROGRESS, never elapsed time: the deadline moves forward every time the buffer grows, so a
    * load that is slow but working is never cut off, however long it takes. Only a stream that has stopped arriving
    * altogether runs its budget out — a short one once there has been a picture, a far longer one before there ever
    * was, since a first load may be waiting on a transcode starting up at the far end.
    *
    * Media3 raises no error for this on its own: a source that connects and then goes quiet leaves the player
    * buffering indefinitely, which is what a viewer describes as "it just sat there". Saying `error` hands it to
    * whatever chose the address, which has somewhere else to try. */
  private def watch(): Unit = {
    watching += 1
    grew = Platform.ticks()
    beat(watching)
  }

  private def beat(mine: Int): Unit = Platform.after(1.0, () => player match {
    case Some(p) if mine == watching =>
      val buffered = try p.getBufferedPosition catch { case _: Throwable => reached }
      val waiting = playing && p.getPlaybackState == Player.STATE_BUFFERING
      if (buffered > reached) {
        reached = buffered
        grew = Platform.ticks()
        // bytes are arriving again, so a read gets the patience the profile asked for rather than the short leash
        leash(false)
      }
      else if (waiting) {
        val budget = if (said) holding.stallMs else holding.startupMs
        if (Platform.ticks() - grew > budget) {
          watching += 1
          Platform.log("media: " + source + " stopped arriving (" + (budget / 1000).toString + "s without progress)")
          listener("error")
        }
      }
      else grew = Platform.ticks()
      if (mine == watching) beat(mine)
    case _ => ()
  })

  private def close(): Unit = {
    player.foreach { p =>
      try p.stop() catch { case _: Throwable => () }
      p.release()
    }
    player = None
    said = false
    reading = None
    sharp = false
    reached = 0L
    watching += 1
    videoWidth = 0
    videoHeight = 0
  }

  // A TextureView stretches the picture over the whole view; the transform undoes that, scaling it to fill the view
  // (cover, the view's bounds cutting the rest) or to fit whole inside it, about the centre. The view does not cut
  // what the transform puts past its edges, so its bounds are set as its clip.
  private def fit(): Unit = texture.foreach { t =>
    val w = t.getWidth.toDouble
    val h = t.getHeight.toDouble
    t.setClipBounds(new android.graphics.Rect(0, 0, t.getWidth, t.getHeight))
    if (w > 0 && h > 0 && videoWidth > 0 && videoHeight > 0) {
      val scale = if (cover) math.max(w / videoWidth, h / videoHeight) else math.min(w / videoWidth, h / videoHeight)
      val m = new Matrix()
      m.setScale((videoWidth * scale / w).toFloat, (videoHeight * scale / h).toFloat, (w / 2).toFloat, (h / 2).toFloat)
      t.setTransform(m)
    }
  }
}
