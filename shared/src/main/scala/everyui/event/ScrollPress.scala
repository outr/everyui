package everyui.event

import everyui.render.Platform

/** Whether the press ending now scrolled something: a finger, or a mouse, that dragged a list's rows or a scroll's
  * content along further than a tap may is a scroll, and its release clicks nothing under it, as a phone's own lists
  * and a browser's pages have it. A row of a picker, which chooses on a click, was chosen as a finger lifted from
  * scrolling past it, and the picker closed. What scrolls by a drag says so (`scrolled`); `PointerEvents` hears each
  * press go down and come up, and holds back the click that comes of one that scrolled, for every component the click
  * climbs through. A tap whose finger wobbled a little, scrolling a pixel or two, is still a tap (`slop`). */
object ScrollPress {
  /** How far a press may move, in the app's units, and still be a tap: a fingertip rolls as it presses. Android's own
    * is 8 dp. */
  val slop: Double = 8.0
  /** How long after a press that scrolled comes up its click may still come, in milliseconds: every platform sends it
    * with the release, and nobody lifts and taps again as quickly. */
  val window: Double = 250.0

  private var pressScrolled = false
  private var endedAt = -1.0e9
  // the click being held back, as it climbs: the platform's event (or, where it cannot tell, when it came)
  private var heldEvent = -1L
  private var heldAt = -1.0e9

  /** Told by what scrolls by a drag: the press going on now has moved the content further than a tap may. */
  def scrolled(): Unit = pressScrolled = true

  /** Whether the press going on now has scrolled something. */
  def scrolling: Boolean = pressScrolled

  /** Whether a drag from `from` to `to` (along it and across it) has gone further than a tap may. */
  def beyond(fromX: Double, fromY: Double, toX: Double, toY: Double): Boolean =
    math.abs(toX - fromX) > slop || math.abs(toY - fromY) > slop

  // a press going down starts afresh: whatever the last one scrolled is not this one's
  private[event] def down(): Unit = {
    pressScrolled = false
    endedAt = -1.0e9
  }

  // the press coming up: its click, which follows at once, is held back where it scrolled
  private[event] def up(): Unit = if (pressScrolled) {
    pressScrolled = false
    endedAt = Platform.ticks()
  }

  // whether the click heard now comes of a press that scrolled: the first to hear it settles it for the rest it climbs
  // through, and the next click is a new press's
  private[event] def holdsClick(): Boolean =
    if (Pointer.same(heldEvent, heldAt)) true
    else if (pressScrolled || Platform.ticks() - endedAt < window) {
      pressScrolled = false
      endedAt = -1.0e9
      heldEvent = Platform.pointerEvent()
      heldAt = Platform.ticks()
      true
    }
    else false
}
