package everyui.tooltip

import everyui.component.Component
import everyui.component.types.Plain
import reactify.*

/** What a tip's view is made with (`Component.tipView`): what it belongs to, the width it may take (`Tooltip.maxWidth`),
  * the side it opened on, and `close`, for a tip that acts and is done (a workspace's tip with an "Open" link). */
final class TipContext private[tooltip] (val target: Component, val maxWidth: Double, closing: TipContext => Unit) {
  /** The side of `target` it opened on, once it is placed: an arrow drawn on its edge follows it. */
  val side: Var[TipSide] = Plain[TipSide](TipSide.Below)

  /** The tip closed, where it is still the one showing. */
  def close(): Unit = closing(this)
}
