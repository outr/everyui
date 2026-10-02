package everyui.motion

import everyui.render.Platform
import reactify.*

/** Whether people have asked the platform for less motion: the web's `prefers-reduced-motion`, Android's animations
  * removed (their duration scale at none), an Apple TV's Reduce Motion. What moves only to say something is going on
  * (`everyui.kit.BusyRing`) says it standing still instead. It follows the setting as the web says it changes, and as
  * the app comes back to the front elsewhere; the desktop and a Roku have none, so it is false there unless the app
  * sets it (`ReduceMotion.on @= true`). */
object ReduceMotion {
  lazy val on: Var[Boolean] = {
    val asked = Var(Platform.reduceMotion())
    Platform.watchReduceMotion(r => asked @= r)
    asked
  }

  def apply(): Boolean = on()
}
