package everyui.component

import everyui.component.types.Prop
import everyui.focus.Focus
import everyui.render.Platform
import reactify.Var

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
    own(new Prop[Map[String, String]](Map.empty, h => node.setMediaHeaders(h)))
  /** Subtitle files to offer beside the file's own tracks, set before `source`: they go in with the source. Where the
    * platform cannot take them, nothing. */
  lazy val sidecars: Prop[List[MediaSidecar]] =
    own(new Prop[List[MediaSidecar]](Nil, s => node.setMediaSidecars(s)))

  /** A URL: a file, or an HLS stream (`.m3u8`). Empty stops it. */
  lazy val source: Prop[String] = own(new Prop[String]("", s => node.setMediaSource(s)))

  /** `url` opened afresh, `from` seconds in: where it starts is said with it, so the player's first request is for
    * that part of the file, rather than the start of it followed by a seek that races the opening. The same address
    * as the one playing is opened again all the same — a retry of what failed is a new start, not nothing. */
  def open(url: String, from: Double): Unit = {
    if (source() == url) source @= ""
    retrying @= None
    node.setMediaStart(math.max(0.0, from))
    source @= url
  }
  lazy val playing: Prop[Boolean] = own(new Prop[Boolean](true, p => node.setMediaPlaying(p)))
  lazy val loop: Prop[Boolean] = own(new Prop[Boolean](false, l => node.setMediaLoop(l)))
  lazy val muted: Prop[Boolean] = own(new Prop[Boolean](false, m => node.setMediaMuted(m)))
  /** 0 to 1, and past 1 a boost up to 4 — as loud as the platform can make it, where it can make something louder
    * than it was mixed at all (Android's loudness enhancer); elsewhere anything past 1 is simply full volume. */
  lazy val volume: Prop[Double] = own(new Prop[Double](1.0, v => node.setMediaVolume(math.max(0.0, math.min(4.0, v)))))
  /** How fast it runs: 1 is as it was recorded, 2 twice as quick, 0.5 half. A platform that cannot vary the rate
    * keeps playing at 1 and says so through [[rates]]. */
  lazy val rate: Prop[Double] = own(new Prop[Double](1.0, r => node.setMediaRate(math.max(0.1, math.min(4.0, r)))))

  /** How much to hold before playing, how long to wait for bytes, and how much patience a stall is given: one of
    * `responsive`, `balanced` (what an unset value means) and `slow`.
    *
    * It is one choice rather than a handful of millisecond fields because the parts only make sense together: a
    * deeper buffer is worth nothing if the reads time out before it can be filled, and patience is worth nothing if
    * there is nowhere to put what arrives. Set it BEFORE [[source]] — a player holds what it was built with — and a
    * platform whose player cannot be told ignores it. */
  lazy val buffering: Prop[String] = own(new Prop[String]("balanced", b => node.setMediaBuffering(b)))

  /** Kept on the device once fetched, and played from there every time after: for a short clip that comes round
    * again and again (a weather scene's loop), which would otherwise be downloaded afresh at each showing. Set it
    * BEFORE [[source]], and only for something small that is shown repeatedly: a long film or a live stream has
    * nothing to gain and a great deal of room to take. A platform with no store of its own for media (a browser,
    * which keeps what its own cache allows) ignores it. */
  lazy val keepClip: Prop[Boolean] = own(new Prop[Boolean](false, c => node.setMediaCached(c)))

  /** What is playing, for the platform's own now-playing: a television's playing card and its assistant, a phone's
    * lock screen, the transport keys of whatever else is listening. Set on the one thing a screen is FOR — the track,
    * the book, the film — and never on sound under something else (a game's music, a clip to name): it also takes
    * the sound over from other apps while it plays, and gives it back when they take it. The platform's own Play,
    * Pause, Next and Previous arrive as the keys of those names, so a screen hears them where it hears the remote.
    * A title of "" is none; a platform with no now-playing of its own ignores it. */
  lazy val nowPlaying: Prop[NowPlaying] = own(new Prop[NowPlaying](NowPlaying(), n => node.setNowPlaying(n)))

  /** Listen to the sound as well as play it: [[bands]] follows its spectrum, for a visualizer. Set it BEFORE
    * [[source]]; a platform that cannot hear its own output leaves `bands` empty, and a visualizer draws without. */
  lazy val spectrum: Prop[Boolean] = own(new Prop[Boolean](false, s => node.setMediaSpectrum(s)))

  /** The sound's spectrum as it plays, where [[spectrum]] asked for it: levels 0 to 1, low to high, about thirty times
    * a second. Empty until it is heard, and on a platform that cannot hear it. */
  val bands: Var[List[Double]] = Var(Nil)

  /** Whether it is waiting for something to play rather than playing: the pause a viewer knows as the picture
    * freezing with nothing said about it.
    *
    * Not every platform will say, and one that does may say it late — so what is shown to a viewer is best decided
    * from this AND from whether the playhead is moving, which is true everywhere and needs nothing of the player. */
  def waiting: Boolean = node.mediaBuffering()

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

  /** What the player decoded, and the last error it met ([[MediaDiagnostics]]): once it is ready, for a report of
    * what played; after an `error`, for what went wrong. Empty on a platform that does not say. */
  def diagnostics: MediaDiagnostics = node.mediaDiagnostics()

  /** What is being said on screen at this moment, as the chosen text track has it, and "" while nothing is.
    *
    * Only where the platform hands the words over instead of drawing them: Android decodes the text and draws
    * none of it, so the app must. A browser and a Roku paint their own, and answer "" here. */
  def cues: String = node.mediaCues()

  /** The subtitles on screen at this moment as the file places and styles them ([[MediaCue]]): where the platform
    * hands them over (Android), and Nil while nothing is said and wherever the platform paints its own. Pushed, as
    * [[said]] is. */
  val spoken: Var[List[MediaCue]] = Var(Nil)

  /** The last load the player is retrying on its own, while it is still trying: what a slow opening is actually
    * waiting on. None until one fails, and on a platform whose player does not say. */
  val retrying: Var[Option[MediaRetry]] = Var(None)

  /** What to hear and read this in, where the file carries a choice: a language tag each, or "" for the file's
    * own. Said before the source is set, so the choice is made with it rather than corrected after it. */
  def languages(audio: String, text: String): Unit = node.setMediaLanguages(audio, text)

  def audioTracks: List[MediaTrack] = tracks.filter(_.kind == MediaTrack.Audio)
  def textTracks: List[MediaTrack] = tracks.filter(_.kind == MediaTrack.Text)

  /** The track being heard, by its id; None leaves the file's own choice alone. */
  lazy val audioTrack: Prop[Option[String]] =
    own(new Prop[Option[String]](None, t => node.selectMediaTrack(MediaTrack.Audio, t.getOrElse(""))))

  /** What its captions are ([[TextChoice]]), and the way to choose them:
    *
    *  - `TextFromDevice` (what it starts as): the device's own caption setting ([[everyui.text.Captions]]) decides:
    *    with its captions on, the track in its caption language, else the first; with them off, none. A text language
    *    asked for through [[languages]] that the stream has is the app's choice over it. Where the device says nothing
    *    (a browser, the desktop), none.
    *  - `TextOff`: none, whatever the device says.
    *  - `TextTrack(id)`: that track ([[textTracks]]), whatever the device says.
    *
    * A choice holds for every source after, until another is made. A change the viewer makes to the device's setting
    * while it plays (a Roku's own options menu, Android's caption settings) is theirs: it goes back to
    * `TextFromDevice`. On a Roku, captions are drawn by the device's caption mode, which only the device keeps, so
    * `TextOff` writes it Off and `TextTrack` writes it On, as Roku's own caption menu does (Roku's closed-caption
    * guidance, and its certification's 4.8); elsewhere the choice is this app's alone.
    *
    * {{{
    *   video.captions @= TextChoice.TextOff
    *   video.captions @= TextChoice.TextTrack(video.textTracks.head.id)
    *   video.captions @= TextChoice.TextFromDevice
    * }}} */
  lazy val captions: Prop[TextChoice] = own(new Prop[TextChoice](TextChoice.TextFromDevice, c => {
    node.setMediaText(c)
    textTrack @= TextChoice.asTrack(c)
    if (c != TextChoice.TextFromDevice && deviceWatch.isEmpty && !released)
      deviceWatch = Some(everyui.text.Captions.listen(_ => captions @= TextChoice.TextFromDevice))
  }))
  private var deviceWatch: Option[() => Unit] = None

  /** [[captions]] as an id, kept for code written before it, and mapped both ways: None is `TextFromDevice` (the
    * device decides, NOT none), Some("") is `TextOff`, and Some(id) is `TextTrack(id)`. Setting None after a track
    * therefore hands the captions back to the device rather than turning them off: say `TextOff` (or Some("")) for
    * that. */
  lazy val textTrack: Prop[Option[String]] =
    own(new Prop[Option[String]](None, t => captions @= TextChoice.fromTrack(t)))

  private var listeners: List[String => Unit] = Nil
  private var seated = false
  private var unseen = 0
  private var released = false
  /** Where it had reached when it was last put away, and what it was playing. */
  private var asleep: Option[(String, Double)] = None

  /** The words on screen, as they change. Pushed rather than polled: half a second of lag is nothing on a
    * playhead and plainly wrong on a subtitle, which has to appear with the line being spoken. */
  val said: Var[String] = Var("")

  node.onMediaEvent { e =>
    if (e == "cues") {
      said @= node.mediaCues()
      spoken @= node.mediaCueList()
    }
    if (e == "retry") retrying @= node.mediaRetry()
    else if (e == "ready") retrying @= None
    if (e == "bands") bands @= node.mediaBands()
    else if (e != "retry") listeners.foreach(_(e))
  }
  watch()

  /** Hear `ready` (it is showing, or sounding), `ended` (it reached its end, and does not loop) and `error` — and,
    * with [[nowPlaying]] set, `paused` and `resumed` where the platform stopped or restarted it itself (another app
    * took the sound, then gave it back), so what the screen shows can follow. */
  def events(f: String => Unit): Unit = listeners = listeners :+ f

  /** Seconds in. */
  def playhead(): Double = node.getMediaPosition()

  /** How many seconds long, or 0 where the player cannot say — a stream still opening, a format that carries no
    * length, or a platform with nothing to ask. A caller showing progress must treat 0 as "not yet known" rather
    * than as an empty clip, since it becomes known a moment after playing starts. */
  def duration(): Double = node.getMediaDuration()

  def seek(seconds: Double): Unit = node.seekMedia(seconds)

  /** Stop it and let its player go for good: it plays no more, and being shown again will not bring it back. For a
    * screen that is finished with what it was playing. */
  def release(): Unit = if (!released) {
    released = true
    deviceWatch.foreach(stop => stop())
    deviceWatch = None
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
      node.setMediaCached(keepClip())
      node.setMediaSpectrum(spectrum())
      node.setNowPlaying(nowPlaying())
      // from where it was, said with the source rather than sought to after it
      node.setMediaStart(at)
      node.setMediaSource(url)
      node.setMediaLoop(loop())
      node.setMediaMuted(muted())
      node.setMediaVolume(volume())
      node.setMediaRate(rate())
      node.setMediaPlaying(playing())
    }
  }
}
