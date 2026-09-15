package everyui.i18n

import everyui.render.Platform
import reactify.*

/** The reading direction: right to left for a language written that way. `set` changes it: the platform takes it at
  * its root (text shaping, fields, wrapped text), and every layout mirrors. `Strings.use` sets it from the language. */
object Direction {
  val rtl: Var[Boolean] = Var(false)

  def set(rightToLeft: Boolean): Unit = if (rtl() != rightToLeft) {
    rtl @= rightToLeft
    Platform.setDirection(rightToLeft)
    everyui.ui.relayout()
  }
}
