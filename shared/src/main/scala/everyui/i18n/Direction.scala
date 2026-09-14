package everyui.i18n

import everyui.render.Platform
import reactify.*

/** The reading direction: right to left for a language written that way. The platform takes it at its root (text
  * shaping, fields, wrapped text), and every layout mirrors when it changes. `Strings.use` sets it from the language. */
object Direction {
  val rtl: Var[Boolean] = Var(false)

  rtl.attach { r =>
    Platform.setDirection(r)
    everyui.ui.relayout()
  }
}
