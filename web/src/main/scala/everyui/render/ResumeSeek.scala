package everyui.render

/** A place the player is to land on, `target` seconds in, where a player may not take the first word for it: the start
  * of a source on a TV's own player that drops a time set before it has loaded, a seek that springs back (webOS 3 on
  * an MP4), or the place a sound track was chosen at, where a TV's player starts its pipeline again from 0 (webOS 3's
  * HLS) or a little before. The time is set again as the player says more (`loadedmetadata`, `canplay`, `playing`,
  * `timeupdate`), while it stands short of the target by more than [[ResumeSeek.near]] (a segment's start or
  * a keyframe before it is there), up to [[ResumeSeek.tries]] times, until it has played [[ResumeSeek.settle]] seconds
  * on from where it landed. Another seek takes its place. Until it has landed, the position to save is the target, so
  * a checkpoint taken as it opens does not save the start. Plain Scala, so the desktop's specs walk it as the page
  * does. */
final class ResumeSeek(val target: Double) {
  private var left = ResumeSeek.tries
  private var over = target <= 0.0
  // the playhead where it was first heard at the target (a segment's start, a keyframe before it), -1 before
  private var reached = -1.0

  /** Whether it has landed, been given up, or been overtaken by a seek. */
  def done: Boolean = over

  /** At the player's `event`, with its playhead where it is and whether it is seeking: the time to set, or None. It has
    * landed once it has played on from there ([[ResumeSeek.settle]] seconds, at a `timeupdate` or `playing`); a player
    * that went back from there in the meantime is set again. */
  def heard(event: String, position: Double, seeking: Boolean): Option[Double] =
    if (over || seeking) None
    else if (position > target + ResumeSeek.past) {
      // somewhere else altogether: no slip of the player's, nothing to set again
      over = true
      None
    }
    else if (position >= target - ResumeSeek.near) {
      if (reached < 0.0 || position < reached) reached = position
      if ((event == "timeupdate" || event == "playing") && position >= reached + ResumeSeek.settle) over = true
      None
    }
    else if (left == 0) {
      over = true
      None
    }
    else {
      left -= 1
      reached = -1.0
      Some(target)
    }

  /** Given up: the player's place is its own from now. */
  def cancel(): Unit = over = true

  /** Where it is, for saving: the target until it has landed. */
  def position(now: Double): Double = if (over) now else math.max(now, target)
}

object ResumeSeek {
  /** How many times the time is set again before it is given up. */
  val tries: Int = 6
  /** How near the target counts as there, in seconds (a keyframe before it, a segment's start). */
  val near: Double = 2.0
  /** How long it plays on from where it landed before it has landed for good, in seconds: webOS 3's player, given
    * another sound track, starts again from 0 about half a second later, from where it was. */
  val settle: Double = 2.0
  /** How far past the target the player may be and still be landing on it, in seconds. */
  val past: Double = 30.0
}
