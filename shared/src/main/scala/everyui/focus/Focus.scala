package everyui.focus

import everyui.component.Component
import everyui.event.{KeyEvent, PointerEvent}
import everyui.render.Platform
import reactify.*

/** Which component has the focus, and how the keys reach it. Every key goes to the focused component; Enter
  * clicks it, and an arrow moves the focus to the nearest focusable component that way — the same spatial
  * navigation for a remote, a keyboard and a TV browser, in one place rather than one per platform. With nothing
  * focused, the first arrow focuses the first focusable component. */
object Focus {
  val focused: Var[Option[Component]] = Var(None)
  private var focusables: Vector[Component] = Vector.empty

  Platform.onKey((name, pressed) => key(name, pressed))

  def register(component: Component): Unit = focusables = focusables :+ component
  def unregister(component: Component): Unit = {
    focusables = focusables.filterNot(_ eq component)
    if (focused().exists(_ eq component)) focused @= None
  }

  /** A key by the web's name, pressed or released — what each backend's key hook reports. Whether the focus
    * layer took it (Enter on, or an arrow moving, the focus), so the platform leaves it be. */
  def key(name: String, pressed: Boolean): Boolean = {
    focused().foreach { c =>
      if (pressed) c.event.key.down @= KeyEvent(name) else c.event.key.up @= KeyEvent(name)
    }
    if (!pressed) false
    else if (name == "Enter") focused().map { c =>
      c.event.click @= PointerEvent("click", c.absoluteLeft + c.size.center(), c.absoluteTop + c.size.middle())
      true
    }.getOrElse(false)
    else if (name.startsWith("Arrow")) move(name)
    else false
  }

  /** Focus the nearest focusable component in `direction` ("ArrowUp" …); the first one when none has the focus.
    * Whether anything was focused. */
  def move(direction: String): Boolean = focused() match {
    case None =>
      focusables.headOption.foreach(_.focus())
      focusables.nonEmpty
    case Some(from) =>
      val fx = from.absoluteLeft + from.size.center()
      val fy = from.absoluteTop + from.size.middle()
      var best: Option[Component] = None
      var bestDistance = 0.0
      focusables.filterNot(_ eq from).foreach { c =>
        val dx = c.absoluteLeft + c.size.center() - fx
        val dy = c.absoluteTop + c.size.middle() - fy
        val ahead =
          if (direction == "ArrowRight") dx > 1.0
          else if (direction == "ArrowLeft") dx < -1.0
          else if (direction == "ArrowDown") dy > 1.0
          else dy < -1.0
        // the distance along the way, plus twice the drift off it: a neighbour in line beats a nearer one aside
        val sideways = direction == "ArrowLeft" || direction == "ArrowRight"
        val distance = (if (sideways) math.abs(dx) else math.abs(dy)) + (if (sideways) math.abs(dy) else math.abs(dx)) * 2.0
        if (ahead && (best.isEmpty || distance < bestDistance)) {
          best = Some(c)
          bestDistance = distance
        }
      }
      best.foreach(_.focus())
      best.nonEmpty
  }
}
