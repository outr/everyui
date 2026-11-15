package everyui.component

import everyui.focus.Focus
import everyui.render.Platform
import reactify.*

/** A part of `host` made only while it is wanted: the pointer over `host`, the focus in the part, or `also` (a card's
  * focus drawn, say), and always on a screen with no pointer to hover with (a phone's). It is made and put in `host`
  * as it is first wanted, and taken out and disposed of as it stops being wanted: a list's hover tools cost nothing on
  * the rows the pointer is not over. In any container, a list's row as much as a card in a column.
  *
  * {{{
  *   val tools = WhileHovered(card)(() => toolbar(message))
  *   tools.made.attach(t => t.foreach(_.position.left @= card.size.width() - 120.0))
  * }}}
  *
  * Disposed of with `host`. `stop()` ends it sooner, disposing of the part if it is made. */
class WhileHovered(host: Container, also: => Boolean = false)(make: () => Component) {
  /** The part, while it is made. */
  val made: Var[Option[Component]] = Var(None)
  // a screen with nothing to hover with: the part always there
  private val always = !Platform.pointerSupport()
  private val wanted: Val[Boolean] = Val(always || host.event.pointer.over() || also ||
    made().exists(p => Focus.focused().exists(f => Focus.within(f, p))))
  private val reaction = wanted.attach(on => turn(on))
  private var stopped = false
  host.onDispose(() => stop())
  turn(wanted())

  /** Listening no more: the part, if made, taken out and disposed of. */
  def stop(): Unit = if (!stopped) {
    stopped = true
    wanted.reactions -= reaction
    Component.unbind(wanted)
    turn(false)
  }

  private def turn(on: Boolean): Unit = made() match {
    case None if on && !stopped && !host.takenApart =>
      val part = make()
      host.children += part
      made @= Some(part)
    case Some(part) if !on || stopped =>
      made @= None
      host.children -= part
      part.dispose()
    case _ => ()
  }
}

object WhileHovered {
  def apply(host: Container, also: => Boolean = false)(make: () => Component): WhileHovered =
    new WhileHovered(host, also)(make)
}
