package everyui.render

/** When the running motions are stepped (`Motion`): `gapMillis` after a step, the next is asked for (`post`), and on the
  * display frame after that (`frame`) the motions are stepped (`framed`), and so on while any is `live`.
  *
  * A frame asked for may never come: one asked as the app goes away (a window behind another gets no vsync, its surface
  * let go) or as its surface is made again (a fold or unfold, a turn of the phone, a change of window). The step it
  * waited for was the only thing that asked for the next, so every motion stood still for as long as the app ran
  * after (Voidcraft on a Pixel Fold, 0.1.28: "the spinners show but don't animate", after each return to the app). So
  * going away drops whatever is asked (`away`), coming back asks afresh (`back`), and a frame not come `stallMillis`
  * after it was due is given up on and asked for again (`watch`).
  *
  * The platform is handed in (Android's main Handler and Choreographer in `Motion`), so the pacing runs on the JVM in
  * a spec. */
final class MotionPacer(gapMillis: Long, stallMillis: Long, post: (Runnable, Long) => Unit, unpost: Runnable => Unit,
                        frame: () => Unit, unframe: () => Unit, live: () => Boolean, steps: Long => Unit) {
  // whether the next step is asked for, and whether the app is away
  private var asked = false
  private var gone = false
  // the frame after the gap, asked for once the gap is over
  private val askFrame: Runnable = () => frame()
  // a frame not come long after it was due: given up on, and asked for again
  private val watch: Runnable = () => if (asked && !gone) {
    drop()
    ask()
  }

  /** Whether a step is asked for now. */
  def waiting: Boolean = asked

  /** Whether the app is away now. */
  def isAway: Boolean = gone

  /** The next step asked for, `gapMillis` on, unless it already is, the app is away or nothing moves. */
  def ask(): Unit = if (!asked && !gone && live()) {
    asked = true
    post(askFrame, gapMillis)
    post(watch, gapMillis + stallMillis)
  }

  /** The frame asked for has come, at `now` (the Choreographer's nanoseconds): the motions stepped, and the next asked. */
  def framed(now: Long): Unit = {
    asked = false
    unpost(watch)
    if (live()) {
      steps(now)
      ask()
    }
  }

  /** The app gone away: nothing asked is left waiting for a frame that may not come. */
  def away(): Unit = {
    gone = true
    drop()
  }

  /** The app back: the steps asked for afresh. */
  def back(): Unit = {
    gone = false
    drop()
    ask()
  }

  private def drop(): Unit = {
    unpost(askFrame)
    unpost(watch)
    unframe()
    asked = false
  }
}
