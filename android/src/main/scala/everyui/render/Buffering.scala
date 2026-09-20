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
  * playback starts and after every seek, in exchange for far fewer interruptions.
  *
  * @param idleMs  what a read is given in the moments after a pause is lifted, where a socket that died quietly
  *                while nothing was being asked of it is the likely explanation and finding out quickly is worth
  *                more than patience
  * @param patienceMs how long a single load may go on failing and being retried before it is called lost. The
  *                number of retries is derived from it rather than chosen: six tries at three seconds and six at
  *                thirty are not the same promise, and it is the wall clock the viewer is watching
  * @param stallMs how long the buffer may fail to grow, once there has been a picture, before the stream is given
  *                up on. Progress is the test, not elapsed time: a slow load that is still arriving is never killed
  * @param startupMs the same, before there has ever been a picture — a first load may be doing real work (a
  *                transcode starting up at the far end) for far longer than a stall in the middle deserves */
class Buffering(val name: String, val minMs: Int, val maxMs: Int, val startMs: Int, val resumeMs: Int,
                val connectMs: Int, val readMs: Int, val idleMs: Int, val patienceMs: Int, val stallMs: Int,
                val startupMs: Int)

object Buffering {
  /** A LAN, or a link fast enough that waiting is the only thing it would cost: the player's own defaults. */
  val Responsive: Buffering =
    new Buffering("responsive", 50000, 50000, 2500, 5000, 8000, 8000, 3000, 30000, 20000, 120000)

  /** Two minutes held, five seconds before it starts. What most links want, and what is chosen when nothing else
    * is. */
  val Balanced: Buffering =
    new Buffering("balanced", 120000, 120000, 5000, 10000, 8000, 15000, 3000, 60000, 20000, 120000)

  /** For a link that buffers: five minutes held, fifteen seconds before it starts, half a minute after it stalls,
    * and long enough on a read that being slow is not mistaken for being broken. */
  val Slow: Buffering =
    new Buffering("slow", 300000, 300000, 15000, 30000, 15000, 30000, 5000, 120000, 60000, 180000)

  def of(name: String): Buffering = name.trim.toLowerCase match {
    case "responsive" => Responsive
    case "slow"       => Slow
    case _            => Balanced
  }
}
