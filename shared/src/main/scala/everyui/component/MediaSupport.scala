package everyui.component

import everyui.component.types.Prop
import everyui.focus.Focus
import everyui.render.Platform

/** What plays over time, as a [[VideoView]] and an [[AudioPlayer]] share it: where from, whether it is playing, again
  * at its end, how loud, and what happened to it. It lets its player go once it has been on the screen and is no
  * longer (a scene taken down with it inside), so nothing plays on out of sight. */
trait MediaSupport { self: Component =>
  /** A URL: a file, or an HLS stream (`.m3u8`). Empty stops it. */
  lazy val source: Prop[String] = new Prop[String]("", s => node.setMediaSource(s))
  lazy val playing: Prop[Boolean] = new Prop[Boolean](true, p => node.setMediaPlaying(p))
  lazy val loop: Prop[Boolean] = new Prop[Boolean](false, l => node.setMediaLoop(l))
  lazy val muted: Prop[Boolean] = new Prop[Boolean](false, m => node.setMediaMuted(m))
  /** 0 to 1. */
  lazy val volume: Prop[Double] = new Prop[Double](1.0, v => node.setMediaVolume(math.max(0.0, math.min(1.0, v))))

  private var listeners: List[String => Unit] = Nil
  private var seated = false
  private var unseen = 0
  private var released = false

  node.onMediaEvent(e => listeners.foreach(_(e)))
  watch()

  /** Hear `ready` (it is showing, or sounding), `ended` (it reached its end, and does not loop) and `error`. */
  def events(f: String => Unit): Unit = listeners = listeners :+ f

  /** Seconds in. */
  def playhead(): Double = node.getMediaPosition()

  def seek(seconds: Double): Unit = node.seekMedia(seconds)

  /** Stop it and let its player go: it plays no more. */
  def release(): Unit = if (!released) {
    released = true
    node.releaseMedia()
  }

  // twice a second: on the screen, or taken off it for good (or never shown in ten minutes)
  private def watch(): Unit = Platform.after(0.5, () => if (!released) {
    val up = Focus.showing(self)
    if (up) seated = true else unseen += 1
    if ((seated && !up) || (!seated && unseen > 1200)) release() else watch()
  })
}
