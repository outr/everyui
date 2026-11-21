package everyui.render

/** Players let go whose release did not finish in time, kept until it has (ExoMedia).
  *
  * Media3 waits on the main thread for a player's own thread to release it, for so long ([[ExoMedia]]'s release
  * timeout), and then gives up waiting, not releasing: the release stays queued on that thread behind whatever held it,
  * and happens when it gets there. Until then the player is a leftover: silenced again at every [[sweep]] (a track its
  * thread made since is made silent too), and seen to have finished once `done` says so, its thread ended. Each sweep
  * says which finished, and how long after they were let go. Plain Scala, for the desktop's specs. */
class Leftovers[A](done: A => Boolean, quiet: A => Unit) {
  // each with its name and when it was let go
  private var held: List[(A, String, Double)] = Nil

  /** `player`, named `name` (its address) in the log, let go `at` seconds on the app's clock and not yet released. */
  def keep(player: A, name: String, at: Double): Unit =
    if (!held.exists(_._1 == player)) held = held :+ ((player, name, at))

  def size: Int = held.size
  def isEmpty: Boolean = held.isEmpty

  /** Those released since the last sweep, each with how long after it was let go, taken out; the rest silenced again. */
  def sweep(now: Double): List[(String, Double)] = {
    val (finished, waiting) = held.partition(h => Leftovers.safely(done(h._1)))
    held = waiting
    waiting.foreach(h => Leftovers.safely { quiet(h._1); true })
    finished.map(h => (h._2, now - h._3))
  }

  /** Those still waiting, each with how long it has. */
  def waiting(now: Double): List[(String, Double)] = held.map(h => (h._2, now - h._3))
}

object Leftovers {
  /** What an error a player says is. `Report`: the player playing the source said it, and the app is told. `Keep`: a
    * player already let go timed out releasing, which belongs to the source it played, not the one now playing, and
    * makes it a leftover. `Log`: anything else from a player let go, logged and nothing more. */
  def blame(current: Boolean, timedOut: Boolean): String =
    if (current) Report else if (timedOut) Keep else Log

  val Report = "report"
  val Keep = "keep"
  val Log = "log"

  private def safely(f: => Boolean): Boolean = try f catch { case _: Throwable => false }
}
