package everyui.focus

import everyui.component.Component
import everyui.event.{KeyEvent, PointerEvent}
import everyui.render.Platform
import everyui.ui
import reactify.*

/** Which component has the focus, and how the keys reach it. Every key goes to the focused component; Enter
  * clicks it, and an arrow moves the focus to the nearest focusable component that way — the same spatial
  * navigation for a remote, a keyboard and a TV browser, in one place rather than one per platform.
  *
  * The candidates are the focusable components under `ui` at the time of the key, found by walking the tree: no
  * registry to keep, so a list rebuilt under the focus or a screen swapped out needs no bookkeeping. With
  * nothing focused, the first arrow focuses the first candidate; with the focused component gone from the tree,
  * the arrow starts from the candidate nearest where it was. */
object Focus {
  val focused: Var[Option[Component]] = Var(None)

  Platform.onKey((name, pressed) => key(name, pressed))

  /** A key by the web's name, pressed or released — what each backend's key hook reports. Whether the focus
    * layer took it (Enter on, or an arrow moving, the focus), so the platform leaves it be. */
  def key(name: String, pressed: Boolean): Boolean = {
    focused().foreach { c =>
      if (pressed) c.event.key.down @= KeyEvent(name) else c.event.key.up @= KeyEvent(name)
    }
    if (!pressed) false
    else if (name == "Enter") focused().map { c =>
      c.event.click @= PointerEvent("click", centerX(c), centerY(c))
      true
    }.getOrElse(false)
    else if (name.startsWith("Arrow")) move(name)
    else false
  }

  /** Focus the nearest candidate in `direction` ("ArrowUp" …). Whether anything was focused. */
  def move(direction: String): Boolean = {
    val all = candidates(ui)
    focused() match {
      case None =>
        all.headOption.foreach(_.focus())
        all.nonEmpty
      case Some(was) =>
        // still in the tree, or else the candidate nearest where it was
        val from = if (all.exists(_ eq was)) Some(was) else nearest(all, centerX(was), centerY(was))
        from.flatMap { f =>
          val fx = centerX(f)
          val fy = centerY(f)
          var best: Option[Component] = None
          var bestDistance = 0.0
          all.filterNot(_ eq f).foreach { c =>
            val dx = centerX(c) - fx
            val dy = centerY(c) - fy
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
          best.orElse(if (all.exists(_ eq was)) None else from)
        } match {
          case Some(next) =>
            next.focus()
            true
          case None => false
        }
    }
  }

  /** The focusable components under `root`, in tree order. */
  def candidates(root: Component): Vector[Component] = {
    var found: Vector[Component] = Vector.empty
    if (root.focusable()) found = found :+ root
    root.childComponents.foreach(child => found = found ++ candidates(child))
    found
  }

  private def nearest(all: Vector[Component], x: Double, y: Double): Option[Component] = {
    var best: Option[Component] = None
    var bestDistance = 0.0
    all.foreach { c =>
      val distance = math.abs(centerX(c) - x) + math.abs(centerY(c) - y)
      if (best.isEmpty || distance < bestDistance) {
        best = Some(c)
        bestDistance = distance
      }
    }
    best
  }

  private def centerX(c: Component): Double = c.absoluteLeft + c.size.center()
  private def centerY(c: Component): Double = c.absoluteTop + c.size.middle()
}
