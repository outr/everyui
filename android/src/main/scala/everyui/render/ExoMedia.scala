package everyui.render

import android.net.Uri
import android.view.{SurfaceView, TextureView, View}
import androidx.media3.common.{AudioAttributes, C, MediaItem, PlaybackParameters, Player, TrackSelectionOverride, Tracks, VideoSize}
import androidx.media3.exoplayer.{DefaultLoadControl, DefaultRenderersFactory, ExoPlayer}
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import everyui.component.{MediaSidecar, MediaTrack}

/** What a video or an audio node plays, Android edition: an ExoPlayer, drawing (for a video) into a view in the node's
  * [[VideoFrame]], which lays that view out at the picture's shape.
  *
  * The view is a `TextureView` unless the video is `direct`: then it is a `SurfaceView`, whose picture never passes
  * through the app's drawing at all. The display composes it on its own plane at the file's own resolution, so a 4K
  * file is shown in 4K rather than scaled into a 1080p window, HDR and Dolby Vision reach the television, and a SHIELD
  * upscales it with its own processor. What it cannot do is what a picture in the app's layers can: fade, turn, or be
  * cut by a rounded box. A film wants the first; a picture behind a screen wants the second.
  *
  * It replaces `MediaPlayer`, which plays a file well enough and answers none of the questions a television asks of a
  * home network. Four things are only reachable here: what to send with the request, how long to wait for an answer,
  * how much to hold before playing, and how fast the bytes are actually arriving. The first is what lets a server
  * that will not take a token in the address recognise us at all; the rest are what tell a slow link from a dead one,
  * which a player that cannot measure has to guess at, and guesses wrong in the direction of "failed".
  *
  * Everything runs on the main thread, as the player's callbacks do. */
class ExoMedia(frame: Option[VideoFrame]) {
  private var player: Option[ExoPlayer] = None
  private var direct = false
  private var picture: Option[View] = None
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
  /** What the text track is saying right now: media3 decodes it, and the app draws it (see onCues). */
  private var cueText: String = ""
  private var listener: String => Unit = _ => ()
  private var sending: Map[String, String] = Map.empty
  private var holding: Buffering = Buffering.Balanced
  /** How far the buffer had reached when it was last seen growing, and when that was. */
  private var reached = 0L
  private var grew = 0.0
  private var watching = 0
  /** What stops the player hearing that the app has gone away and come back. */
  private var hooks: List[() => Unit] = Nil

  frame.foreach(_ => show())

  /** What to send with the request, and with every request that comes out of it: an HLS playlist's segments and keys
    * are fetched by the same factory, so they carry it too. Kept for the next source: the factory is built with the
    * player. */
  def setHeaders(headers: Map[String, String]): Unit = sending = headers

  /** Subtitle files to offer beside the file's own tracks. Kept for the next source: they go in with it. */
  def setSidecars(files: List[MediaSidecar]): Unit = sidecars = files
  private var sidecars: List[MediaSidecar] = Nil

  /** What to play, with the subtitle files beside it, each with its URL as its id — as the Flutter player builds it
    * — and read as what its name says: WebVTT, SubStation, else SubRip. */
  private def item(url: String): MediaItem = {
    val subs = new java.util.ArrayList[MediaItem.SubtitleConfiguration]()
    sidecars.foreach { s =>
      val lower = s.url.toLowerCase
      val mime =
        if (lower.endsWith(".vtt")) androidx.media3.common.MimeTypes.TEXT_VTT
        else if (lower.endsWith(".ass") || lower.endsWith(".ssa")) androidx.media3.common.MimeTypes.TEXT_SSA
        else androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
      subs.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url)).setMimeType(mime)
        .setLanguage(if (s.language.isEmpty) null else s.language)
        .setLabel(if (s.label.nonEmpty) s.label else if (s.language.nonEmpty) s.language else "Subtitles")
        .setId(s.url).build())
    }
    new MediaItem.Builder().setUri(Uri.parse(url)).setSubtitleConfigurations(subs).build()
  }

  /** How much to hold before playing, and how long to wait for bytes: see [[Buffering]]. Takes effect on the next
    * source, since a load control belongs to the player it was built with. */
  def setBuffering(named: String): Unit = holding = Buffering.of(named)

  /** Whether what is fetched is kept on the device and read from there next time ([[ClipCache]]). Takes effect on
    * the next source, whose reads are what go through the store. */
  def setCached(on: Boolean): Unit = keeping = on
  private var keeping = false

  def setSource(url: String): Unit = if (url != source) {
    source = url
    close()
    if (url.nonEmpty) {
      try {
        // OkHttp fetches the media, not Media3's own data source, and the difference is not academic: the default
        // one is HttpURLConnection, which opens a fresh connection for every ranged request and has no pool to
        // reuse. A home server that pauses for a few seconds then gets a torn-down connection and an immediate
        // reconnect from a client that has just given up on it — which is slower to produce bytes than waiting
        // would have been, so each failure feeds the next. That is the "plays a second, buffers, plays a few
        // seconds" report, measured on 2026-09-20 against a Jellyfin library where the SAME file played cleanly in
        // another client on the same device, network and server. What that client did differently was OkHttp with
        // its pool, its keep-alive and no short read timeout.
        val client = new okhttp3.OkHttpClient.Builder()
          .connectTimeout(holding.connectMs.toLong, java.util.concurrent.TimeUnit.MILLISECONDS)
          .readTimeout(holding.readMs.toLong, java.util.concurrent.TimeUnit.MILLISECONDS)
          .retryOnConnectionFailure(true)
          .build()
        val http = new androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(client)
        if (sending.nonEmpty) {
          val map = new java.util.HashMap[String, String]()
          sending.foreach(h => map.put(h._1, h._2))
          http.setDefaultRequestProperties(map)
        }
        val load = new DefaultLoadControl.Builder()
          .setBufferDurationsMs(holding.minMs, holding.maxMs, holding.startMs, holding.resumeMs)
          .build()
        val made = new ExoPlayer.Builder(Host.activity)
          .setRenderersFactory(renderers(url).setEnableDecoderFallback(true))
          .setTrackSelector(new DefaultTrackSelector(Host.activity))
          // how long one load may go on failing before it is lost is the profile's patience, and the number of
          // attempts follows from it rather than being picked: the viewer is watching a clock, not a counter
          .setMediaSourceFactory(new DefaultMediaSourceFactory(if (keeping) ClipCache.around(http) else http)
            .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy {
              override def getMinimumLoadableRetryCount(dataType: Int): Int =
                math.max(3, holding.patienceMs / math.max(1000, holding.readMs))
            }))
          .setLoadControl(load)
          .build()
        sounding(made)
        attach(made)
        made.setRepeatMode(if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF)
        made.setVolume(if (muted) 0f else math.min(1.0, volume).toFloat)
        if (rate != 1.0) made.setPlaybackParameters(new PlaybackParameters(rate.toFloat))
        // the languages are usually said before there is a player to say them to, so each one is told as it is made
        prefer(made)
        // nobody is watching a player behind another app, or a sleeping screen: it holds while the app is away and
        // carries on as it was asked to when it is back — where what it was asked was to play
        hooks = List(Platform.onAway(() => made.setPlayWhenReady(false)),
          Platform.onResume(() => made.setPlayWhenReady(playing)))
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
          override def onIsPlayingChanged(on: Boolean): Unit = session.foreach(_.state(on))
          override def onPositionDiscontinuity(was: Player.PositionInfo, now: Player.PositionInfo, reason: Int): Unit =
            session.foreach(_.state(made.isPlaying))
          // held or let go by Android rather than the app — another app took the sound, or gave it back — which the
          // app is told, so what it shows as playing is what is
          override def onPlayWhenReadyChanged(on: Boolean, reason: Int): Unit =
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
              reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
              playing = on
              listener(if (on) "resumed" else "paused")
            }
          override def onVideoSizeChanged(size: VideoSize): Unit = {
            videoWidth = size.width
            videoHeight = size.height
            aspect = if (size.pixelWidthHeightRatio > 0f) size.pixelWidthHeightRatio.toDouble else 1.0
            fit()
          }
          // The subtitles themselves. Media3 decodes the text and hands it over, but nothing draws it: that is
          // media3-ui's SubtitleView, and this build merges no Android resources. The words go up to the app
          // instead, which already draws text on every platform and can put them where this player wants them.
          override def onCues(group: androidx.media3.common.text.CueGroup): Unit = {
            val list = group.cues
            // the subtitles that are pictures (PGS, VobSub) are drawn here, over the picture: there are no words in them
            // for the app to draw
            frame.foreach(_.cues((0 until list.size()).toList.map(i => list.get(i))))
            val words = new StringBuilder
            var i = 0
            while (i < list.size()) {
              val text = Option(list.get(i).text).map(_.toString.trim).getOrElse("")
              if (text.nonEmpty) {
                if (words.nonEmpty) words.append('\n')
                words.append(text)
              }
              i += 1
            }
            val now = words.toString
            if (now != cueText) {
              cueText = now
              listener("cues")
            }
          }
          override def onPlayerError(error: androidx.media3.common.PlaybackException): Unit = {
            Platform.log("media: " + url + " failed (" + error.getErrorCodeName + ")")
            listener("error")
          }
        })
        made.setMediaItem(item(url))
        made.prepare()
        made.setPlayWhenReady(playing)
        player = Some(made)
        announce()
        // a boost asked for before there was a player to boost
        if (volume > 1.0) loudness()
        watch()
      } catch {
        case t: Throwable =>
          Platform.logError("media: " + url, t)
          listener("error")
      }
    }
  }

  def setPlaying(on: Boolean): Unit = {
    playing = on
    player.foreach(_.setPlayWhenReady(on))
    if (on) watch()
  }

  /** What is playing, for Android's now-playing ([[NowPlayingSession]]): a session while there is a player and a
    * name to give it, and the sound's focus with it. */
  def setNowPlaying(what: everyui.component.NowPlaying): Unit = {
    about = what
    player.foreach(sounding)
    announce()
  }
  private var about = everyui.component.NowPlaying()
  private var session: Option[NowPlayingSession] = None

  /** Whether to listen to the sound for its spectrum ([[SpectrumTap]]). Takes effect on the next source: the tap is
    * built into the player's audio path. */
  def setSpectrum(on: Boolean): Unit = listening = on
  private var listening = false
  private var heard: List[Double] = Nil

  /** The spectrum last heard, or nothing. */
  def bands(): List[Double] = heard

  /** The player's renderers, with the spectrum tap in its audio path where it was asked for. What it hears is handed
    * over on the main thread, and only while it is still this source playing. */
  private def renderers(url: String): DefaultRenderersFactory =
    if (!listening) new DefaultRenderersFactory(Host.activity)
    else {
      val tap = new SpectrumTap(levels => Host.activity.runOnUiThread(() => if (source == url && player.nonEmpty) {
        heard = levels.toList
        listener("bands")
      }))
      new DefaultRenderersFactory(Host.activity) {
        override protected def buildAudioSink(context: android.content.Context, float: Boolean,
                                              params: Boolean): androidx.media3.exoplayer.audio.AudioSink =
          new androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
            .setAudioProcessors(Array[androidx.media3.common.audio.AudioProcessor](tap))
            .build()
      }
    }

  /** What the sound is, said to Android: a film or music, and whether it takes the sound over from other apps —
    * only what is playing as the point of the screen (it has a now-playing), never sound under something else. */
  private def sounding(p: ExoPlayer): Unit = p.setAudioAttributes(new AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(if (frame.nonEmpty) C.AUDIO_CONTENT_TYPE_MOVIE else C.AUDIO_CONTENT_TYPE_MUSIC)
    .build(), about.title.nonEmpty)

  /** The session, made, told what is playing, or let go, to match. */
  private def announce(): Unit = player match {
    case Some(p) if about.title.nonEmpty =>
      val s = session.getOrElse {
        val made = new NowPlayingSession(() => position(), at => seek(at))
        session = Some(made)
        made
      }
      s.describe(about)
      s.state(p.isPlaying)
    case _ =>
      session.foreach(_.release())
      session = None
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

  /** What to hear and read this in, where the file carries a choice.
    *
    * Said to the selector rather than acted on once the tracks are known: a preference applied after the fact lets
    * the file's own default track start first and then switches, which is a moment of the wrong language out loud.
    * An empty language clears the preference rather than asking for a track that has none. */
  def languages(audio: String, text: String): Unit = {
    hear = audio.trim
    read = text.trim
    player.foreach(prefer)
  }

  /** The languages last asked for, kept for every player made after: a source is a new player. */
  private var hear = ""
  private var read = ""

  private def prefer(p: ExoPlayer): Unit =
    try {
      val from = p.getTrackSelectionParameters.buildUpon()
      if (hear.isEmpty) from.setPreferredAudioLanguages() else from.setPreferredAudioLanguage(hear)
      if (read.isEmpty) from.setPreferredTextLanguages() else from.setPreferredTextLanguage(read)
      p.setTrackSelectionParameters(from.build())
    }
    catch { case t: Throwable => Platform.logError("media: languages", t) }

  /** What is being said on screen at this moment, as the text track has it, or "" while nothing is. */
  def cues(): String = cueText

  /** How large the picture is, as the decoder reports it. */
  def size(): (Double, Double) = (videoWidth.toDouble, videoHeight.toDouble)

  /** Whether the player is waiting for something to play rather than playing. */
  def buffering(): Boolean = player.exists(_.getPlaybackState == Player.STATE_BUFFERING)

  /** What the link has been carrying, in bits a second, as the player has measured it — or 0 where it has not
    * measured anything yet. What a retry should ask for is a fraction of this, rather than a number chosen in
    * advance and equally wrong for a link managing thirty megabits and one managing two. */
  def bandwidth(): Long =
    try androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.getSingletonInstance(Host.activity).getBitrateEstimate
    catch { case _: Throwable => 0L }

  def position(): Double = player.map(_.getCurrentPosition / 1000.0).getOrElse(0.0)
  /** How long it is, or 0 where the player does not know yet (ExoPlayer says TIME_UNSET, a large negative). */
  def duration(): Double = player.map(_.getDuration / 1000.0).filter(_ > 0.0).getOrElse(0.0)

  def seek(seconds: Double): Unit = player.foreach(_.seekTo(math.round(seconds * 1000.0)))

  def onEvent(f: String => Unit): Unit = listener = f

  def release(): Unit = {
    source = ""
    close()
  }

  /** The volume, and past full volume a boost: the player's own volume stops at 1, so what is asked for beyond it is
    * made up by a loudness enhancer on the player's audio session, in the millibels it wants (a gain of 2 is +6 dB),
    * as the Flutter app's player does it. */
  private def loudness(): Unit = player.foreach { p =>
    p.setVolume(if (muted) 0f else math.min(1.0, volume).toFloat)
    if (volume > 1.0 && !muted) {
      if (enhancer.isEmpty) enhancer =
        try Some(new android.media.audiofx.LoudnessEnhancer(p.getAudioSessionId))
        catch { case t: Throwable => Platform.logError("media: no loudness enhancer", t); None }
      enhancer.foreach { e =>
        e.setTargetGain((2000.0 * math.log10(volume)).toInt)
        e.setEnabled(true)
      }
    }
    else enhancer.foreach(_.setEnabled(false))
  }

  private var enhancer: Option[android.media.audiofx.LoudnessEnhancer] = None

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
    hooks.foreach(release => release())
    hooks = Nil
    enhancer.foreach(e => try e.release() catch { case _: Throwable => () })
    enhancer = None
    player.foreach { p =>
      try p.stop() catch { case _: Throwable => () }
      p.release()
    }
    player = None
    heard = Nil
    // the session outlives one source for the next (a queue's tracks), and goes with the last
    if (source.isEmpty) announce()
    said = false
    reached = 0L
    watching += 1
    videoWidth = 0
    videoHeight = 0
    aspect = 1.0
    cueText = ""
    frame.foreach(_.cues(Nil))
  }

  /** Drawn by the display itself (a `SurfaceView`) or in the app's layers (a `TextureView`). A player keeps playing
    * across the change: it is only told where to draw next. */
  def setDirect(on: Boolean): Unit = if (on != direct) {
    direct = on
    if (frame.nonEmpty) show()
  }

  /** The view the picture goes into, made for how it is drawn, in place of the one before. */
  private def show(): Unit = frame.foreach { f =>
    player.foreach(detach)
    val made: View = if (direct) new SurfaceView(Host.activity) else new TextureView(Host.activity)
    f.hold(made)
    picture = Some(made)
    player.foreach(attach)
    fit()
  }

  private def attach(p: ExoPlayer): Unit = picture.foreach {
    case s: SurfaceView => p.setVideoSurfaceView(s)
    case t: TextureView => p.setVideoTextureView(t)
    case _ => ()
  }

  private def detach(p: ExoPlayer): Unit = picture.foreach {
    case s: SurfaceView => p.clearVideoSurfaceView(s)
    case t: TextureView => p.clearVideoTextureView(t)
    case _ => ()
  }

  /** How wide one of the file's pixels is for its height: 1 but for an anamorphic file, whose pixels are wider. */
  private var aspect = 1.0

  /** The picture at its own shape in the box. A `SurfaceView`'s buffer is also held at the file's own size, which is
    * what lets the display, not the app, scale it to the screen. */
  private def fit(): Unit = frame.foreach { f =>
    f.fit(videoWidth * aspect, videoHeight.toDouble, cover)
    picture.foreach {
      case s: SurfaceView =>
        if (videoWidth > 0 && videoHeight > 0) s.getHolder.setFixedSize(videoWidth, videoHeight)
        else s.getHolder.setSizeFromLayout()
      case _ => ()
    }
  }
}
