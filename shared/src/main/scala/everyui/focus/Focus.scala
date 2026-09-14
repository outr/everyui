package everyui.focus

import everyui.component.Component
import everyui.event.{KeyEvent, PointerEvent}
import everyui.render.Platform
import reactify.*

/** Which component has the focus, and how the keys reach it. Every key goes to the focused component; Enter
  * clicks it, and an arrow moves the focus to the nearest focusable component that way — the same spatial
  * navigation for a remote, a keyboard and a TV browser, in one place rather than one per platform. */
object Focus:
  val focused: Var[Option[Component]] = Var(None)
  private var focusables: Vector[Component] = Vector.empty

  Platform.onKey((name, pressed) => key(name, pressed))

  def register(component: Component): Unit = focusables = focusables :+ component
  def unregister(component: Component): Unit =
    focusables = focusables.filterNot(_ eq component)
    if focused().exists(_ eq component) then focused @= None

  /** A key by the web's name, pressed or released — what each backend's key hook reports. */
  def key(name: String, pressed: Boolean): Unit =
    focused().foreach { c =>
      if pressed then c.event.key.down @= KeyEvent(name) else c.event.key.up @= KeyEvent(name)
    }
    if pressed then
      if name == "Enter" then focused().foreach(c => c.event.click @= PointerEvent(c.absoluteLeft + c.size.center(), c.absoluteTop + c.size.middle()))
      else if name.startsWith("Arrow") then move(name)

  /** Focus the nearest focusable component in `direction` ("ArrowUp" …), if any. */
  def move(direction: String): Unit = focused().foreach { from =>
    val fx = from.absoluteLeft + from.size.center()
    val fy = from.absoluteTop + from.size.middle()
    var best: Option[Component] = None
    var bestDistance = 0.0
    focusables.filterNot(_ eq from).foreach { c =>
      val dx = c.absoluteLeft + c.size.center() - fx
      val dy = c.absoluteTop + c.size.middle() - fy
      val ahead =
        if direction == "ArrowRight" then dx > 1.0
        else if direction == "ArrowLeft" then dx < -1.0
        else if direction == "ArrowDown" then dy > 1.0
        else dy < -1.0
      // the distance along the way, plus twice the drift off it: a neighbour in line beats a nearer one aside
      val along = if direction == "ArrowLeft" || direction == "ArrowRight" then math.abs(dx) else math.abs(dy)
      val aside = if direction == "ArrowLeft" || direction == "ArrowRight" then math.abs(dy) else math.abs(dx)
      val distance = along + aside * 2.0
      if ahead && (best.isEmpty || distance < bestDistance) then
        best = Some(c)
        bestDistance = distance
    }
    best.foreach(_.focus())
  }
