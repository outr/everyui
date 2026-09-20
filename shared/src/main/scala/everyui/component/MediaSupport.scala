package everyui.component

import everyui.component.types.Prop
import everyui.focus.Focus
import everyui.render.Platform

/** What plays over time, as a [[VideoView]] and an [[AudioPlayer]] share it: where from, whether it is playing, again
  * at its end, how loud, and what happened to it. It lets its player go once it has been on the screen and is no
  * longer (a scene taken down with it inside), so nothing plays on out of sight. */
trait MediaSupport { self: Component =>
  /** What to send with the request for the media, and with every request that comes out of it — an HLS playlist's
    * segments and keys as much as the playlist itself. A server that will not take its token in the address has no
    * other way to know who is asking.
    *
    * Set these BEFORE [[source]]: they are read when the request is made, and a source already playing does not go
    * back and ask again. A platform that cannot send headers with its media says so by ignoring them. */
  lazy val headers: Prop[Map[String, String]] =
    new Prop[Map[String, String]](Map.empty, h => node.setMediaHeaders(h))

  /** A URL: a file, or an HLS stream (`.m3u8`). Empty stops it. */
  lazy val source: Prop[String] = new Prop[String]("", s => node.setMediaSource(s))
  lazy val playing: Prop[Boolean] = new Prop[Boolean](true, p => node.setMediaPlaying(p))
  lazy val loop: Prop[Boolean] = new Prop[Boolean](false, l => node.setMediaLoop(l))
  lazy val muted: Prop[Boolean] = new Prop[Boolean](false, m => node.setMediaMuted(m))
  /** 0 to 1. */
  lazy val volume: Prop[Double] = new Prop[Double](1.0, v => node.setMediaVolume(math.max(0.0, math.min(1.0, v))))
  /** How fast it runs: 1 is as it was recorded, 2 twice as quick, 0.5 half. A platform that cannot vary the rate
    * keeps playing at 1 and says so through [[rates]]. */
  lazy val rate: Prop[Double] = new Prop[Double](1.0, r => node.setMediaRate(math.max(0.1, math.min(4.0, r))))

  /** How much to hold before playing, how long to wait for bytes, and how much patience a stall is given: one of
    * `responsive`, `balanced` (what an unset value means) and `slow`.
    *
    * It is one choice rather than a handful of millisecond fields because the parts only make sense together: a
    * deeper buffer is worth nothing if the reads time out before it can be filled, and patience is worth nothing if
    * there is nowhere to put what arrives. Set it BEFORE [[source]] — a player holds what it was built with — and a
    * platform whose player cannot be told ignores it. */
  lazy val buffering: Prop[String] = new Prop[String]("balanced", b => node.setMediaBuffering(b))

  /** Whether this platform can play at a rate other than 1. */
  def rates: Boolean = node.mediaRates()

  /** What the connection has been carrying, in bits a second, as the player has measured it while playing — 0 where
    * it has measured nothing, and on a platform whose player does not count.
    *
    * It is worth asking at the moment something fails: what to try instead is a fraction of what the link has been
    * seen to manage, where a number chosen in advance is equally wrong for a link managing thirty megabits and one
    * managing two. */
  def bandwidth: Long = node.mediaBandwidth()

  /** How large the picture actually is, from the decoder — what is on the screen, rather than what was asked for.
    * None until it is ready, and on a platform that does not say. */
  def videoSize: Option[(Double, Double)] = {
    val size = node.mediaSize()
    if (size._1 > 0.0 && size._2 > 0.0) Some(size) else None
  }

  /** The tracks the thing playing carries — the languages it can be heard in, and read in. Empty until it is ready
    * (the `ready` event), and empty for good on a platform that does not tell. */
  def tracks: List[MediaTrack] = node.mediaTracks()

  def audioTracks: List[MediaTrack] = tracks.filter(_.kind == MediaTrack.Audio)
  def textTracks: List[MediaTrack] = tracks.filter(_.kind == MediaTrack.Text)

  /** The track being heard, by its id; None leaves the file's own choice alone. */
  lazy val audioTrack: Prop[Option[String]] =
    new Prop[Option[String]](None, t => node.selectMediaTrack(MediaTrack.Audio, t.getOrElse("")))

  /** The track being read, by its id; None shows none. */
  lazy val textTrack: Prop[Option[String]] =
    new Prop[Option[String]](None, t => node.selectMediaTrack(MediaTrack.Text, t.getOrElse("")))

  private var listeners: List[String => Unit] = Nil
  private var seated = false
  private var unseen = 0
  private var released = false
  /** Where it had reached when it was last put away, and what it was playing. */
  private var asleep: Option[(String, Double)] = None

  node.onMediaEvent(e => listeners.foreach(_(e)))
  watch()

  /** Hear `ready` (it is showing, or sounding), `ended` (it reached its end, and does not loop) and `error`. */
  def events(f: String => Unit): Unit = listeners = listeners :+ f

  /** Seconds in. */
  def playhead(): Double = node.getMediaPosition()

  def seek(seconds: Double): Unit = node.seekMedia(seconds)

  /** Stop it and let its player go for good: it plays no more, and being shown again will not bring it back. For a
    * screen that is finished with what it was playing. */
  def release(): Unit = if (!released) {
    released = true
    asleep = None
    node.releaseMedia()
  }

  // twice a second: on the screen, or off it — taken down with a scene, or a page left behind
  private def watch(): Unit = Platform.after(0.5, () => if (!released) {
    val up = Focus.showing(self)
    if (up) {
      seated = true
      unseen = 0
      asleep.foreach(was => wake(was._1, was._2))
    }
    else {
      unseen += 1
      // off the screen after having been on it, or built and never shown in ten minutes
      if (asleep.isEmpty && (seated || unseen > 1200)) sleep()
    }
    watch()
  })

  /** Out of sight: the player goes, and where it had reached is kept.
    *
    * A player is the expensive thing a platform hands out — a decoder, a surface, a socket — and nothing should hold
    * one for a page nobody is looking at, least of all go on playing into it. But letting it go used to be the end
    * of it: a page built once and come back to (a weather screen, a title's page) set its source the first time and
    * had nothing to say the second, so the second visit was a black rectangle. Sleeping is the same saving with the
    * page none the wiser. */
  private def sleep(): Unit = {
    asleep = Some((source(), playhead()))
    node.releaseMedia()
  }

  /** Back in sight: the same thing playing, from where it left off. */
  private def wake(url: String, at: Double): Unit = {
    asleep = None
    if (url.nonEmpty) {
      node.setMediaHeaders(headers())
      node.setMediaBuffering(buffering())
      node.setMediaSource(url)
      node.setMediaLoop(loop())
      node.setMediaMuted(muted())
      node.setMediaVolume(volume())
      node.setMediaRate(rate())
      node.setMediaPlaying(playing())
      if (at > 0.0) Platform.after(0.5, () => node.seekMedia(at))
    }
  }
}
