package everyui.render

/** Starting a source `target` seconds in, on a player that may not take the first word for it (a TV's own player that
  * drops a time set before it has loaded, or one sought to before it could): the time is set again as the player says
  * more (`loadedmetadata`, `canplay`, `playing`, `timeupdate`), while it stands short of the target, until it has
  * played on from there, up to [[ResumeSeek.tries]] times. A seek of the viewer's or the app's ends it. Until it has
  * landed, the position to save is the target, so a checkpoint taken as it opens does not save the start. Plain Scala,
  * so the desktop's specs walk it as the page does. */
final class ResumeSeek(val target: Double) {
  private var left = ResumeSeek.tries
  private var over = target <= 0.0

  /** Whether it has landed, been given up, or been overtaken by a seek. */
  def done: Boolean = over

  /** At the player's `event`, with its playhead where it is and whether it is seeking: the time to set, or None. It has
    * landed once it is playing on from the target (a `timeupdate` or `playing` at or a little past it, not seeking). */
  def heard(event: String, position: Double, seeking: Boolean): Option[Double] =
    if (over || seeking) None
    else if (position >= target - ResumeSeek.near) {
      if ((event == "timeupdate" || event == "playing") && position <= target + ResumeSeek.past) over = true
      None
    }
    else if (left == 0) {
      over = true
      None
    }
    else {
      left -= 1
      Some(target)
    }

  /** A seek of the viewer's or the app's: the resume is over. */
  def cancel(): Unit = over = true

  /** Where it is, for saving: the target until it has landed. */
  def position(now: Double): Double = if (over) now else math.max(now, target)
}

object ResumeSeek {
  /** How many times the time is set again before it is given up. */
  val tries: Int = 6
  /** How near the target counts as there, in seconds (a keyframe before it, a segment's start). */
  val near: Double = 2.0
  /** How far past the target it may have played and still be resuming from it. */
  val past: Double = 30.0
}
