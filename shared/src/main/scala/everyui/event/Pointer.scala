package everyui.event

import everyui.render.Platform

/** The last pointer event a handler consumed; a component under it does not fire it again. The event climbs through
  * the components under the pointer in the same moment on every backend, so the hold lasts only `hold` milliseconds:
  * a second click at the very same pixel, even a quick double click, is a new event and fires. */
object Pointer {
  var consumed: Option[PointerEvent] = None
  /** When `consumed` was consumed, by `Platform.ticks()`. */
  var consumedAt: Double = 0.0
  /** How long a consumed event keeps its equals from firing: far longer than one event's climb, far shorter than any
    * two clicks a person makes. */
  val hold: Double = 30.0

  /** Whether `event` is the one just consumed, still climbing, which the components under it must not fire. */
  def blocks(event: PointerEvent): Boolean =
    consumed.contains(event) && Platform.ticks() - consumedAt < hold
}
