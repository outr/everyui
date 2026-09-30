package everyui.event

import everyui.render.Platform

/** When the focus last moved, by `Platform.ticks()`: what a click on a container asks before it takes the focus,
  * since a field inside it takes the focus as the press goes down, before the click that press ends in arrives. */
object FocusMoves {
  /** How long before a click a focus that moved counts as that click's own press having moved it, in milliseconds. */
  val press: Double = 1000.0
  private var at: Double = -1.0e9
  private var watching: Boolean = false

  /** Follow the focus from now on (once): the first click channel made asks for it. */
  def watch(): Unit = if (!watching) {
    watching = true
    everyui.focus.Focus.focused.attach(_ => at = Platform.ticks())
  }

  /** Whether the focus moved just now: within a press's length of this moment. */
  def justMoved: Boolean = Platform.ticks() - at < press
}
