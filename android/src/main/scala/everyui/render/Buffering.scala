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
  * Between `minMs` and `maxMs` the player stops reading: full at `maxMs`, it reads again only once the buffer has
  * played down to `minMs`, holding the response open and unread all that while. A server drops a response nobody
  * reads after a while (Wasabi after about sixty seconds; a router or a home server at its own). Under the old
  * Balanced (50 s to 120 s) a clip whose bytes stayed under the player's byte target, so that it reached the pause,
  * ended each cycle in a load error and a reconnect, every seventy-odd seconds for as long as it played.
  * So the two are kept under forty-five seconds apart: as much held, refilled more often in smaller pieces.
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

  /** Two minutes held, and — this is the part worth noticing — the SAME wait before it starts as the player's own
    * defaults.
    *
    * The shape is taken from the Jellyfin television client's own "Large" setting, which raises how much is held
    * without touching how long the viewer waits at the start or after a seek. Holding more costs nothing until
    * something goes wrong, while starting later costs a second of every single play; an earlier version of this
    * traded five seconds of start-up for cushion nobody had asked for. Only [[Slow]] makes that trade. */
  val Balanced: Buffering =
    new Buffering("balanced", 80000, 120000, 2500, 5000, 8000, 15000, 3000, 60000, 20000, 120000)

  /** For a link that buffers: four minutes held, and now it IS worth waiting five seconds to start, because the
    * alternative is stopping again thirty seconds in. The Jellyfin client's "Extra Large", with longer patience on
    * a read so that being slow is not mistaken for being broken. */
  val Slow: Buffering =
    new Buffering("slow", 200000, 240000, 5000, 10000, 15000, 30000, 5000, 120000, 60000, 180000)

  def of(name: String): Buffering = name.trim.toLowerCase match {
    case "responsive" => Responsive
    case "slow"       => Slow
    case _            => Balanced
  }
}
