package everyui.render

import android.graphics.{Matrix, SurfaceTexture}
import android.media.{AudioAttributes, MediaPlayer}
import android.net.Uri
import android.view.{Surface, TextureView}
import everyui.component.MediaTrack

/** What a video or an audio node plays, Android edition: a `MediaPlayer`, drawing (for a video) into the node's
  * `TextureView`, which a transform fits to the box, filling it or whole inside it. The player is made again for each
  * source; the settings outlive it. Everything runs on the main thread, as the player's callbacks do. */
class AndroidMedia(texture: Option[TextureView]) {
  private var player: Option[MediaPlayer] = None
  private var prepared = false
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
  private var listener: String => Unit = _ => ()

  texture.foreach { t =>
    t.setSurfaceTextureListener(new TextureView.SurfaceTextureListener {
      override def onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int): Unit = {
        surface = Some(new Surface(st))
        player.foreach(_.setSurface(surface.get))
        fit()
      }
      override def onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int): Unit = fit()
      override def onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = {
        player.foreach(_.setSurface(null))
        surface.foreach(_.release())
        surface = None
        true
      }
      override def onSurfaceTextureUpdated(st: SurfaceTexture): Unit = ()
    })
  }

  /** What to send with the request. Kept until the next source is set: a `MediaPlayer` is given its headers with
    * its address and cannot be told afterwards. */
  def setHeaders(headers: Map[String, String]): Unit = sending = headers

  private var sending: Map[String, String] = Map.empty

  def setSource(url: String): Unit = if (url != source) {
    source = url
    close()
    if (url.nonEmpty) {
      try {
        val made = new MediaPlayer()
        made.setAudioAttributes(new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(if (texture.nonEmpty) AudioAttributes.CONTENT_TYPE_MOVIE else AudioAttributes.CONTENT_TYPE_MUSIC)
          .build())
        // with the headers where there are any: a playlist fetched this way passes them on to its own segments and
        // keys, which is what an HLS transcode behind an authenticating server needs
        if (sending.isEmpty) made.setDataSource(Host.activity, Uri.parse(url))
        else {
          val map = new java.util.HashMap[String, String]()
          sending.foreach(h => map.put(h._1, h._2))
          made.setDataSource(Host.activity, Uri.parse(url), map)
        }
        surface.foreach(made.setSurface)
        made.setOnPreparedListener(_ => {
          prepared = true
          made.setLooping(looping)
          loudness()
          if (rate != 1.0) try made.setPlaybackParams(made.getPlaybackParams.setSpeed(rate.toFloat)) catch { case _: Throwable => () }
          if (playing) made.start()
          if (texture.isEmpty) listener("ready")
        })
        made.setOnVideoSizeChangedListener((_, w, h) => {
          videoWidth = w
          videoHeight = h
          fit()
        })
        made.setOnInfoListener((_, what, _) => {
          if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) listener("ready")
          false
        })
        made.setOnCompletionListener(_ => if (!looping) listener("ended"))
        made.setOnErrorListener((_, what, extra) => {
          Platform.log("media: " + url + " failed (" + what + ", " + extra + ")")
          listener("error")
          true
        })
        player = Some(made)
        made.prepareAsync()
      } catch {
        case t: Throwable =>
          Platform.logError("media: " + url, t)
          listener("error")
      }
    }
  }

  def setPlaying(on: Boolean): Unit = {
    playing = on
    if (prepared) player.foreach(p => if (on) p.start() else if (p.isPlaying) p.pause())
  }

  def setLoop(on: Boolean): Unit = {
    looping = on
    if (prepared) player.foreach(_.setLooping(on))
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

  /** How fast it runs. `setPlaybackParams` starts a player that was paused, so a rate set while paused is kept and
    * given to it when it plays again. */
  def setRate(r: Double): Unit = {
    rate = r
    if (prepared && playing) player.foreach { p =>
      try p.setPlaybackParams(p.getPlaybackParams.setSpeed(r.toFloat))
      catch { case t: Throwable => Platform.logError("media: rate " + r.toString, t) }
    }
  }

  /** The languages it can be heard and read in, as the file carries them. The index is the id: it is what
    * `selectTrack` takes. */
  def tracks(): List[MediaTrack] = if (!prepared) Nil else player.toList.flatMap { p =>
    try p.getTrackInfo.toList.zipWithIndex.flatMap { case (info, i) =>
      val kind = info.getTrackType match {
        case MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO    => MediaTrack.Audio
        case MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE => MediaTrack.Text
        case MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT => MediaTrack.Text
        case _                                               => ""
      }
      val language = Option(info.getLanguage).map(_.trim).filterNot(l => l.isEmpty || l == "und").getOrElse("")
      if (kind.isEmpty) Nil else List(MediaTrack(i.toString, kind, language))
    }
    catch { case t: Throwable => Platform.logError("media: tracks", t); Nil }
  }

  /** Hear or read a track, by the index it was given out under. An empty id leaves the file's own choice alone,
    * except for text, where it means none: a subtitle is deselected rather than reverted. */
  def selectTrack(kind: String, id: String): Unit = if (prepared) player.foreach { p =>
    try {
      if (id.nonEmpty) id.toIntOption.foreach(p.selectTrack)
      else if (kind == MediaTrack.Text) tracks().filter(_.kind == MediaTrack.Text)
        .flatMap(_.id.toIntOption).foreach(i => try p.deselectTrack(i) catch { case _: Throwable => () })
    }
    catch { case t: Throwable => Platform.logError("media: track " + kind + " " + id, t) }
  }

  /** How large the picture is, as the decoder reports it. */
  def size(): (Double, Double) = (videoWidth.toDouble, videoHeight.toDouble)

  def position(): Double = if (prepared) player.map(_.getCurrentPosition / 1000.0).getOrElse(0.0) else 0.0

  def seek(seconds: Double): Unit = if (prepared) player.foreach(_.seekTo(math.round(seconds * 1000.0).toInt))

  def onEvent(f: String => Unit): Unit = listener = f

  def release(): Unit = {
    source = ""
    close()
  }

  private def loudness(): Unit = if (prepared) player.foreach { p =>
    val level = if (muted) 0f else volume.toFloat
    p.setVolume(level, level)
  }

  private def close(): Unit = {
    player.foreach { p =>
      try p.reset() catch { case _: Throwable => () }
      p.release()
    }
    player = None
    prepared = false
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
