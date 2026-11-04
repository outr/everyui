package everyui.rich

/** A stretch of a rich document, from where a selection was started (`anchor`) to where it is now (`focus`): either
  * way round. `start` and `end` put them in order; nothing is chosen where they are the same, the caret there. */
case class RichRange(anchor: RichPos, focus: RichPos) {
  def start: RichPos = if (anchor <= focus) anchor else focus
  def end: RichPos = if (anchor <= focus) focus else anchor
  def collapsed: Boolean = anchor == focus
}

object RichRange {
  /** The caret alone, at `at`. */
  def at(at: RichPos): RichRange = RichRange(at, at)
}
