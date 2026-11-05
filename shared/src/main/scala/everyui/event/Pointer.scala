package everyui.event

import everyui.render.Platform

/** The last pointer event a handler consumed; a component under it does not fire it again. Where the platform tells
  * its events apart (`Platform.pointerEvent`: the web, the desktop), it is that one event, however long its handlers
  * take as it climbs (a button whose work draws a list again, which a container under it must still not hear); where
  * it does not, the hold lasts only `hold` milliseconds, so a second click at the very same pixel, even a quick double
  * click, is a new event and fires. */
object Pointer {
  var consumed: Option[PointerEvent] = None
  /** When `consumed` was consumed, by `Platform.ticks()`. */
  var consumedAt: Double = 0.0
  /** Which of the platform's events `consumed` was (`Platform.pointerEvent`), -1 where it cannot tell. */
  var consumedEvent: Long = -1L
  /** How long a consumed event keeps its equals from firing: far longer than one event's climb, far shorter than any
    * two clicks a person makes. */
  val hold: Double = 30.0
  /** When a pointer last went down, moved or clicked, by `Platform.ticks()`. */
  var usedAt: Double = -1.0e9
  /** How soon after a pointer was used a focus change counts as its doing, in milliseconds. */
  val lately: Double = 250.0

  /** Whether a pointer was used just now: a focus that moves now was moved by it, not by a key. */
  def recent: Boolean = Platform.ticks() - usedAt < lately

  private var focusing: Option[PointerEvent] = None
  private var focusingAt: Double = 0.0
  private var focusingEvent: Long = -1L

  // whether the platform's event now is `told` (or, where it cannot tell, whether `at` was a moment ago)
  private[everyui] def same(told: Long, at: Double): Boolean =
    if (told >= 0L) Platform.pointerEvent() == told else Platform.ticks() - at < hold

  /** Record that `event`, climbing, focused a component: the ones it climbs through after, its containers, do not take the
    * focus from it. */
  def focused(event: PointerEvent): Unit = {
    focusing = Some(event)
    focusingAt = Platform.ticks()
    focusingEvent = Platform.pointerEvent()
  }

  /** Whether `event` has already focused a component inside the one it is climbing through now. */
  def focusedBy(event: PointerEvent): Boolean = focusing.contains(event) && same(focusingEvent, focusingAt)

  /** Whether `event` is the one just consumed, still climbing, which the components under it must not fire. */
  def blocks(event: PointerEvent): Boolean =
    consumed.contains(event) && same(consumedEvent, consumedAt)

  /** Whether the context menu just asked for was the app's to show: a handler consumed it, or put a menu (or any
    * dialog) up for it (PointerEvents). Where it was not, the platform's own shows, as a browser's Copy and Look Up. */
  def menuAnswered: Boolean = consumed.exists(_.kind == PointerKind.Menu) && Platform.ticks() - consumedAt < hold

  private var textAt: Double = -1.0e9

  /** Told by the platform: a press went down on words that can be selected (`Component.selectable`). */
  def pressedText(): Unit = textAt = Platform.ticks()

  /** Whether the press going down now is on words that can be selected: a drag from there selects them, and is not
    * a list's row dragged. */
  def onText: Boolean = Platform.ticks() - textAt < hold
}
