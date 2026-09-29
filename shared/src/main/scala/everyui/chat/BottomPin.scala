package everyui.chat

import reactify.*

/** Whether a transcript is kept at its newest entry, as a chat is while somebody reads along: pinned, whatever arrives
  * or grows is scrolled into view; scrolled up, it is let go, and what arrives meanwhile is counted (`unseen`) until
  * the bottom is reached again, which pins it once more. Within `slack` pixels of the bottom counts as there.
  *
  * {{{
  *   pin.entries(ids)                        // the entries, each time they change
  *   pin.scrolled(offset, furthest)          // each time the view moves
  *   if (pin.pinned()) scrollToEnd()         // as the content grows
  * }}} */
class BottomPin(slack: Double = 8.0) {
  val pinned: Var[Boolean] = Var(true)
  /** How many entries have arrived below what was the newest when it was let go. */
  val unseen: Var[Int] = Var(0)

  private var ids: Vector[String] = Vector.empty
  // the newest entry when it was let go
  private var seen: Option[String] = None

  /** The view is `offset` pixels down, of the `furthest` it can go. */
  def scrolled(offset: Double, furthest: Double): Unit = {
    val bottom = offset >= furthest - slack
    if (bottom && !pinned()) {
      pinned @= true
      unseen @= 0
    } else if (!bottom && pinned()) {
      seen = ids.lastOption
      pinned @= false
    }
  }

  /** The entries' ids as they are now, oldest first. */
  def entries(now: Vector[String]): Unit = {
    ids = now
    if (!pinned()) {
      val at = seen.map(s => BottomPin.lastIndex(now, s)).getOrElse(-1)
      val count = if (seen.isEmpty) now.length else if (at < 0) 0 else now.length - 1 - at
      if (unseen() != count) unseen @= count
    }
  }

  /** Pinned again, as a jump to the newest does. */
  def pin(): Unit = if (!pinned()) {
    pinned @= true
    unseen @= 0
  }
}

object BottomPin {
  /** Where `id` is last in `ids`, -1 where it is not: looked for from the end, where what was newest is. */
  def lastIndex(ids: Vector[String], id: String): Int = {
    var i = ids.length - 1
    while (i >= 0 && ids(i) != id) i -= 1
    i
  }
}
