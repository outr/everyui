package everyui.kit

import everyui.layout.LayoutPass
import everyui.render.Platform

/** Work done a piece at a time: as many pieces a turn as [[Turns.budgetMs]] allows, and always one, with a frame drawn
  * between turns, then `done`. For what has to be made before it is shown but need not be made at once (a rail's
  * cards, a long list): made in one turn, it holds the frame of whatever is on the screen meanwhile. */
object Turns {
  /** What a turn may take before the rest waits for the next: well inside a frame. */
  val budgetMs: Double = 10.0

  def run(steps: List[() => Any], done: () => Unit): Unit = Platform.later { () =>
    val started = Platform.ticks()
    var left = steps
    var made = 0
    // laid out once a turn, not once for each piece
    LayoutPass.batch {
      while (left.nonEmpty && (made == 0 || Platform.ticks() - started < budgetMs)) {
        left.head()
        left = left.tail
        made += 1
      }
    }
    if (left.isEmpty) done() else run(left, done)
  }
}
