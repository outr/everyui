package everyui.render

import scala.collection.mutable

/** A MotionPacer on a clock and a display of the spec's own, standing in for Android's Handler and Choreographer: time
  * moves only as the spec moves it, and a frame comes only as it says. */
class PacerRig {
  var now = 0L
  // what is posted, by when it is due
  val posted: mutable.Map[Runnable, Long] = mutable.LinkedHashMap.empty
  var frameAsked = false
  var steps = 0
  var live = true
  val pacer = new MotionPacer(25L, 250L, (r, ms) => posted(r) = now + ms, r => posted.remove(r),
    () => frameAsked = true, () => frameAsked = false, () => live, _ => steps += 1)

  /** Time moved on `ms`, what falls due run in turn. */
  def advance(ms: Long): Unit = {
    val until = now + ms
    var due = posted.filter(_._2 <= until).toList.sortBy(_._2).headOption
    while (due.nonEmpty) {
      val (r, at) = due.get
      posted.remove(r)
      now = at
      r.run()
      due = posted.filter(_._2 <= until).toList.sortBy(_._2).headOption
    }
    now = until
  }
  /** The display's next frame: the one asked for comes. */
  def frame(): Unit = if (frameAsked) {
    frameAsked = false
    pacer.framed(now * 1000000L)
  }
  /** `n` gaps of 33 ms, each with its frame. */
  def run(n: Int): Unit = (1 to n).foreach { _ =>
    advance(33L)
    frame()
  }
}
