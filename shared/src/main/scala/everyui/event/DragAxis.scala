package everyui.event

/** Which way a drag on a scroll goes, settled once for the whole drag, as a phone's own nested scrolls settle it: the
  * first move further than a tap may (`ScrollPress.slop`) either way decides, along the scroll where it went mostly
  * along it, else across it, and it stays so until the press ends. A rail in a list takes only a drag mostly across
  * the list, and the list the rest, so a finger swiping down over a code block scrolls the page around it, never the
  * code sideways as well; a list's rows take no sideways jitter either. `down` where the press went down, `move` each
  * place it moves to. */
class DragAxis(vertical: Boolean) {
  private var fromX = 0.0
  private var fromY = 0.0
  private var way = 0

  /** A press down at `x`, `y`: the way is open again. */
  def down(x: Double, y: Double): Unit = {
    fromX = x
    fromY = y
    way = 0
  }

  /** The press moved to `x`, `y`: the way settled, where it is not yet and this goes further than a tap may. */
  def move(x: Double, y: Double): Unit = if (way == 0) way = DragAxis.settle(x - fromX, y - fromY, vertical, ScrollPress.slop)

  /** Whether the drag settled along the scroll: it is the scroll's. */
  def along: Boolean = way > 0

  /** Whether the drag settled across the scroll: it is something else's (a scroll the other way around it or in it). */
  def across: Boolean = way < 0
}

object DragAxis {
  /** The way a drag `dx`, `dy` from its press goes for a scroll down (`vertical`) or across: 1 along it, -1 across it,
    * 0 while it has gone no further than `slop` either way. Along only where it went further along than across, so a
    * scroll down and a rail across it never both take a drag. */
  def settle(dx: Double, dy: Double, vertical: Boolean, slop: Double): Int = {
    val along = math.abs(if (vertical) dy else dx)
    val other = math.abs(if (vertical) dx else dy)
    if (along <= slop && other <= slop) 0 else if (along > other) 1 else -1
  }

  /** Whether a scroll `at` along, of `furthest`, can still go `toward` (1 on, -1 back): a scroll in a scroll keeps a
    * drag along it only while it can, and leaves it to the scroll it is in at its end, or where it has nothing to
    * scroll (a table's rows as tall as they are, in a chat's message). */
  def goes(at: Double, furthest: Double, toward: Int): Boolean =
    if (toward > 0) at < furthest - 0.5 else if (toward < 0) at > 0.5 else false
}
