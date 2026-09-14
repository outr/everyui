package everyui.focus

import everyui.component.Component
import everyui.event.{Key, KeyEvent, Keys, PointerEvent, PointerKind}
import everyui.render.Platform
import everyui.ui
import reactify.*

/** Which component has the focus, and how the keys reach it. Every key goes to the focused component; Enter
  * clicks it, Tab moves it on in tree order, an arrow moves the focus to the nearest focusable component that way, and
  * Back goes to the newest back handler (`onBack`) — the same navigation for a remote, a keyboard and a TV browser, in
  * one place rather than one per platform.
  *
  * The candidates are the focusable components under `ui` at the time of the key, found by walking the tree: no
  * registry to keep, so a list rebuilt under the focus or a screen swapped out needs no bookkeeping. With
  * nothing focused, the first arrow focuses the first candidate; with the focused component gone from the tree,
  * the arrow starts from the candidate nearest where it was. */
object Focus {
  val focused: Var[Option[Component]] = Var(None)

  Platform.onKey((name, pressed) => key(name, pressed))

  private var backs: List[Key => Boolean] = Nil

  /** Handle Back (or Escape) before anything registered earlier: the newest handler that takes it wins. A dialog
    * registers while it is open, the screen manager for as long as it runs. The returned function unregisters it. */
  def onBack(handler: Key => Boolean): () => Unit = {
    backs = handler :: backs
    () => backs = backs.filterNot(_ eq handler)
  }

  private def goBack(key: Key): Boolean = {
    var taken = false
    var left = backs
    while (!taken && left.nonEmpty) {
      taken = left.head(key)
      left = left.tail
    }
    taken
  }

  /** A key by the web's name, pressed or released — what each backend's key hook reports. Whether the focus
    * layer took it (Enter on, or an arrow or Tab moving, the focus), so the platform leaves it be. A key the focused
    * component keeps (`keepsKey`) goes to it alone. */
  def key(name: String, pressed: Boolean): Boolean = {
    val key = Keys.of(name)
    focused().foreach { c =>
      if (pressed) c.event.key.down @= KeyEvent(key, name) else c.event.key.up @= KeyEvent(key, name)
    }
    if (!pressed) false
    else if (focused().exists(_.keepsKey(key))) false
    else if (key == Key.Enter) focused().map { c =>
      c.event.click @= PointerEvent(PointerKind.Click, centerX(c), centerY(c))
      true
    }.getOrElse(false)
    else if (Keys.isArrow(key)) move(key)
    else if (key == Key.Tab) next()
    else if (key == Key.Back || key == Key.Escape) goBack(key)
    else false
  }

  /** Focus the nearest candidate in `direction` (an arrow). Whether anything was focused. */
  def move(direction: Key): Boolean = {
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
              if (direction == Key.ArrowRight) dx > 1.0
              else if (direction == Key.ArrowLeft) dx < -1.0
              else if (direction == Key.ArrowDown) dy > 1.0
              else dy < -1.0
            // the distance along the way, plus twice the drift off it: a neighbour in line beats a nearer one aside
            val sideways = direction == Key.ArrowLeft || direction == Key.ArrowRight
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

  /** Tab: the next candidate in tree order, round to the first after the last. Whether anything was focused. */
  def next(): Boolean = {
    val all = candidates(ui)
    if (all.isEmpty) false
    else {
      var at = -1
      all.zipWithIndex.foreach { case (c, i) => if (focused().exists(_ eq c)) at = i }
      all((at + 1) % all.length).focus()
      true
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
