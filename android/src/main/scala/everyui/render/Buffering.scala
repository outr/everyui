package everyui.render

/** How much a player holds before it plays, and how long it waits for bytes before calling them lost.
  *
  * One number cannot serve both ends of this. A short read timeout is right for a stream that has gone quiet
  * because the socket died while paused — a home server or the router in front of it drops an idle connection
  * without saying so, and the stall only surfaces minutes later when the buffer drains. It is wrong for a link that
  * is merely busy: three seconds of slowness counts as an error, a handful of retries burn through in half a
  * minute, and a stream that was working is reported as a failed connection.
  *
  * So the viewer chooses instead. Everything a slow link needs moves together: a deeper buffer is worth nothing if
  * the reads time out before it can be filled, and patience is worth nothing if there is nowhere to put what
  * arrives. The cost is honest and should be said where it is chosen — a bigger buffer means a longer wait before
  * playback starts and after every seek, in exchange for far fewer interruptions. */
class Buffering(val name: String, val minMs: Int, val maxMs: Int, val startMs: Int, val resumeMs: Int,
                val connectMs: Int, val readMs: Int)

object Buffering {
  /** A LAN, or a link fast enough that waiting is the only thing it would cost: the player's own defaults. */
  val Responsive: Buffering = new Buffering("responsive", 50000, 50000, 2500, 5000, 8000, 8000)

  /** Two minutes held, five seconds before it starts. What most links want, and what is chosen when nothing else
    * is. */
  val Balanced: Buffering = new Buffering("balanced", 120000, 120000, 5000, 10000, 8000, 15000)

  /** For a link that buffers: five minutes held, fifteen seconds before it starts, half a minute after it stalls,
    * and long enough on a read that being slow is not mistaken for being broken. */
  val Slow: Buffering = new Buffering("slow", 300000, 300000, 15000, 30000, 15000, 30000)

  def of(name: String): Buffering = name.trim.toLowerCase match {
    case "responsive" => Responsive
    case "slow"       => Slow
    case _            => Balanced
  }
}
