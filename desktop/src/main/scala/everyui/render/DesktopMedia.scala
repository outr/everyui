package everyui.render

import javafx.scene.layout.Pane
import javafx.scene.media.{Media, MediaPlayer, MediaView}
import everyui.component.MediaTrack
import javafx.scene.shape.Rectangle
import javafx.util.Duration

/** What a video or an audio node plays, JavaFX edition: a `MediaPlayer` (and, for a video, a `MediaView` in the node's
  * pane, clipped to it, filling it or shown whole). The player is made again for each source; the settings outlive it. */
class DesktopMedia(pane: Option[Pane]) {
  private var player: Option[MediaPlayer] = None
  private var view: Option[MediaView] = None
  private var source = ""
  private var playing = true
  private var looping = false
  private var muted = false
  private var volume = 1.0
  private var rate = 1.0
  private var cover = true
  private var listener: String => Unit = _ => ()

  pane.foreach { p =>
    val clip = new Rectangle()
    clip.widthProperty.bind(p.widthProperty)
    clip.heightProperty.bind(p.heightProperty)
    p.setClip(clip)
    p.widthProperty.addListener((_, _, _) => fit())
    p.heightProperty.addListener((_, _, _) => fit())
  }

  def setSource(url: String): Unit = if (url != source) {
    source = url
    close()
    if (url.nonEmpty) {
      try {
        val made = new MediaPlayer(new Media(url))
        made.setCycleCount(if (looping) MediaPlayer.INDEFINITE else 1)
        made.setMute(muted)
        made.setVolume(volume)
        made.setOnReady(() => {
          fit()
          if (playing) made.play()
          listener("ready")
        })
        made.setOnEndOfMedia(() => if (!looping) listener("ended"))
        made.setOnError(() => listener("error"))
        player = Some(made)
        pane.foreach { p =>
          val v = new MediaView(made)
          v.setPreserveRatio(true)
          p.getChildren.add(v)
          view = Some(v)
        }
      } catch {
        case t: Throwable =>
          Platform.logError("media: " + url, t)
          listener("error")
      }
    }
  }

  def setPlaying(on: Boolean): Unit = {
    playing = on
    player.foreach(p => if (on) p.play() else p.pause())
  }

  def setLoop(on: Boolean): Unit = {
    looping = on
    player.foreach(_.setCycleCount(if (on) MediaPlayer.INDEFINITE else 1))
  }

  def setMuted(on: Boolean): Unit = {
    muted = on
    player.foreach(_.setMute(on))
  }

  def setVolume(v: Double): Unit = {
    volume = math.min(1.0, v) // JavaFX cannot boost
    player.foreach(_.setVolume(volume))
  }

  /** How fast it runs: JavaFX takes a rate on the player itself. */
  def setRate(r: Double): Unit = {
    rate = r
    player.foreach(_.setRate(r))
  }

  /** What the file carries. JavaFX names its tracks but gives no way to choose between them, so they are listed
    * and choosing one does nothing — better a list that says what is there than a menu that lies about it. */
  def tracks(): List[MediaTrack] = player.toList.flatMap { p =>
    val all = p.getMedia.getTracks
    (0 until all.size()).toList.flatMap { i =>
      val t = all.get(i)
      val kind = t match {
        case _: javafx.scene.media.AudioTrack    => MediaTrack.Audio
        case _: javafx.scene.media.SubtitleTrack => MediaTrack.Text
        case _                                   => ""
      }
      val language = Option(t.getLocale).map(_.getLanguage).getOrElse("")
      val label = Option(t.getName).getOrElse("")
      if (kind.isEmpty) Nil else List(MediaTrack(i.toString, kind, language, label))
    }
  }

  def setCover(on: Boolean): Unit = {
    cover = on
    fit()
  }

  /** How large the picture is, as the media reports it. */
  def size(): (Double, Double) =
    player.map(p => (p.getMedia.getWidth.toDouble, p.getMedia.getHeight.toDouble)).getOrElse((0.0, 0.0))

  def position(): Double = player.map(_.getCurrentTime.toSeconds).getOrElse(0.0)
  /** How long it is, or 0 where it is not known yet (JavaFX answers an UNKNOWN duration as NaN). */
  def duration(): Double = player.map(_.getTotalDuration.toSeconds).filter(d => d > 0.0 && !d.isNaN).getOrElse(0.0)

  def seek(seconds: Double): Unit = player.foreach(_.seek(Duration.seconds(seconds)))

  def onEvent(f: String => Unit): Unit = listener = f

  def release(): Unit = {
    source = ""
    close()
  }

  private def close(): Unit = {
    player.foreach { p =>
      p.stop()
      p.dispose()
    }
    player = None
    view.foreach(v => pane.foreach(_.getChildren.remove(v)))
    view = None
  }

  // cover: scaled to the larger of the two fits and centred, the pane's clip cutting the rest; else whole and centred
  private def fit(): Unit = for (p <- pane; v <- view; made <- player) {
    val media = made.getMedia
    val w = p.getWidth
    val h = p.getHeight
    val vw = media.getWidth.toDouble
    val vh = media.getHeight.toDouble
    if (w > 0 && h > 0 && vw > 0 && vh > 0) {
      val scale = if (cover) math.max(w / vw, h / vh) else math.min(w / vw, h / vh)
      v.setFitWidth(vw * scale)
      v.setFitHeight(vh * scale)
      v.setLayoutX((w - vw * scale) / 2.0)
      v.setLayoutY((h - vh * scale) / 2.0)
    }
  }
}
