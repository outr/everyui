package everyui.list

import everyui.event.PointerEvent
import everyui.ui

/** A pointer followed wherever it goes until it is let go: a scrollbar's thumb, a drag. Heard at the root, since a page
  * sends a move to whatever is under the pointer and the desktop to what it went down on. */
object Pointers {
  private var moving: Option[PointerEvent => Unit] = None
  private var ending: Option[PointerEvent => Unit] = None
  private var listening = false

  /** `move` on each move until the pointer is let go, then `up`. A newer one replaces it. */
  def track(move: PointerEvent => Unit, up: PointerEvent => Unit): Unit = {
    listen()
    // the press is the drag's: as it ends, it lets no focus go (Focus.pointerUp)
    everyui.focus.Focus.dragging()
    moving = Some(move)
    ending = Some(up)
  }

  /** Whether something is being followed. */
  def tracking: Boolean = moving.nonEmpty

  private def listen(): Unit = if (!listening) {
    listening = true
    ui.event.pointer.move.attach(e => moving.foreach(f => f(e)))
    ui.event.pointer.up.attach { e =>
      val end = ending
      moving = None
      ending = None
      end.foreach(f => f(e))
    }
  }
}
