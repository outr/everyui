package everyui.focus

import everyui.component.{Component, Container}
import everyui.event.{Key, KeyEvent, Keys, PointerEvent, PointerKind}
import everyui.render.Platform
import everyui.ui
import reactify.*

/** Which component has the focus, and how the keys reach it — the same navigation for a remote, a keyboard and a TV
  * browser, in one place rather than one per platform or per screen.
  *
  * Every key goes to the focused component first. Enter clicks it; Tab moves the focus on in tree order; Back goes to
  * the newest back handler (`onBack`: an open dialog, then the screen history). An arrow is answered by the layouts:
  * from the focused component up through its containers, the first whose layout has a neighbour that way (a row's next
  * item, a column's next line, the nearest item in a flow's next row) takes the focus there, onto its focusable part
  * nearest where the focus was. A container without a layout looks among its own children by where they are, preferring
  * what is in line. A container can also state where an arrow leaving it goes (`Container.exitFocus`) or that it stops
  * there (`Container.stopFocus`); an arrow nothing answers leaves the focus where it is. So there is no zone to wire
  * by hand, and no way for a key to be swallowed by a container that runs the other way.
  *
  * The candidates are the focusable components under `ui` (or under an open dialog: `scope`) at the time of the key,
  * found by walking the tree: no registry, so a list rebuilt under the focus needs no bookkeeping. With nothing
  * focused, the first arrow focuses the first candidate; with the focused component gone, the nearest to where it was.
  * Where the focus starts is each screen's `defaultFocus` and each dialog's. */
object Focus {
  val focused: Var[Option[Component]] = Var(None)
  /** Where the focus may go: under `ui`, or under an open dialog alone, so the arrows never reach what it covers. */
  val scope: Var[Option[Component]] = Var(None)
  /** How many keys have been pressed: what an app watches to know it is being used (a screensaver's idle time). */
  val activity: Var[Long] = Var(0L)

  /** Take the platform's keys: `ui` calls it as it starts, since the Roku compiler runs no statement in an object's
    * body. */
  def listen(): Unit = {
    Platform.onKey((name, pressed) => key(name, pressed))
    // only the component the focus leaves and the one it lands on hear of it, rather than every component on the
    // screen working out for itself whether it is the one (a home screen of rails is a hundred and more)
    focused.changes { (was, now) =>
      was.foreach(_.focusedNow @= false)
      now.foreach(_.focusedNow @= true)
    }
  }

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

  /** A key by the web's name, pressed or released — what each backend's key hook reports. Whether the focus layer took
    * it, so the platform leaves it be. A key the focused component keeps (`keepsKey`) goes to it alone. */
  def key(name: String, pressed: Boolean): Boolean = {
    val key = Keys.of(name)
    if (pressed) activity @= activity() + 1L
    // what had the focus when the key came: a component that moves the focus as it handles its own key is still the one
    // that kept it, and the key does not travel on from wherever the focus went
    val target = focused()
    // asked before it hears the key, which may change its mind (a scroll that reaches its end)
    val kept = pressed && target.exists(_.keepsKey(key))
    target.foreach { c =>
      if (pressed) c.event.key.down @= KeyEvent(key, name) else c.event.key.up @= KeyEvent(key, name)
    }
    if (!pressed) false
    else if (kept) false
    else if (key == Key.Enter) target.map { c =>
      c.event.click @= PointerEvent(PointerKind.Click, centerX(c), centerY(c))
      true
    }.getOrElse(false)
    else if (Keys.isArrow(key)) move(key)
    else if (key == Key.Tab) next()
    else if (key == Key.Back || key == Key.Escape) goBack(key)
    else false
  }

  /** Move the focus an arrow's way (see above). Whether it moved. */
  def move(direction: Key): Boolean = {
    val root = scope().getOrElse(ui)
    val all = candidates(root)
    focused() match {
      case None =>
        all.headOption.foreach(_.focus())
        all.nonEmpty
      case Some(was) if !within(was, root) =>
        nearest(all, centerX(was), centerY(was)).foreach(_.focus())
        all.nonEmpty
      case Some(was) =>
        travel(was, root, direction) match {
          case Some(next) =>
            next.focus()
            true
          case None => false   // left to the platform: a page scrolls, say
        }
    }
  }

  /** From `was` up to `root`, the first container with somewhere to go `direction`'s way. */
  private def travel(was: Component, root: Component, direction: Key): Option[Component] = {
    var child: Component = was
    var found: Option[Component] = None
    var stopped = false
    while (found.isEmpty && !stopped && !(child eq root) && child.parent().nonEmpty) {
      val parent = child.parent().get
      parent match {
        case c: Container =>
          found = c.layout() match {
            case Some(layout) =>
              var next = layout.neighbour(c, c.children(), child, direction)
              var entered: Option[Component] = None
              var steps = 0
              // past a neighbour with nothing focusable in it, to the one beyond
              while (entered.isEmpty && next.nonEmpty && steps <= c.children().length) {
                entered = enter(next.get, was, direction)
                if (entered.isEmpty) next = layout.neighbour(c, c.children(), next.get, direction)
                steps += 1
              }
              entered
            case None => spatial(candidates(c).filterNot(x => within(x, child)), was, direction)
          }
          // nothing inside this way: the container's own exit, if it states one
          if (found.isEmpty) c.focusExit(direction) match {
            case Some(Some(target)) => found = enter(target, was, direction)
            case Some(None) => stopped = true
            case None => ()
          }
        case _ => ()
      }
      child = parent
    }
    found
  }

  /** The part of `target` the focus lands on, arriving `direction`'s way from `from`: itself if it takes the focus,
    * else, of its candidates, those on the line nearest that way (the bottom row, arriving from below), and of those
    * the one most nearly in line with `from`. A container that names its entry (`Container.entryFocus`) is entered
    * there. */
  private def enter(target: Component, from: Component, direction: Key): Option[Component] =
    if (target.focusable()) Some(target)
    else if (entered(target).nonEmpty) entered(target).flatMap(e => enter(e, from, direction))
    else {
      val all = candidates(target)
      val sideways = direction == Key.ArrowLeft || direction == Key.ArrowRight
      var nearestLine = 0.0
      all.zipWithIndex.foreach { case (c, i) => if (i == 0 || depth(c, direction) < nearestLine) nearestLine = depth(c, direction) }
      var best: Option[Component] = None
      var bestDrift = 0.0
      all.foreach { c =>
        if (depth(c, direction) < nearestLine + 1.0) {
          val drift = if (sideways) math.abs(centerY(c) - centerY(from)) else math.abs(centerX(c) - centerX(from))
          if (best.isEmpty || drift < bestDrift) {
            best = Some(c)
            bestDrift = drift
          }
        }
      }
      best
    }

  // the part a container says an arrival lands on, when it has one to take the focus
  private def entered(target: Component): Option[Component] = target match {
    case c: Container => c.focusEntry.filter(e => candidates(e).nonEmpty)
    case _ => None
  }

  /** How far `direction`'s way a component's near edge is: the line arrived at first has the least. */
  private def depth(c: Component, direction: Key): Double =
    if (direction == Key.ArrowDown) c.absoluteTop
    else if (direction == Key.ArrowUp) -(c.absoluteTop + c.size.height())
    else if (direction == Key.ArrowRight) c.absoluteLeft
    else -(c.absoluteLeft + c.size.width())

  /** Of `all`, the nearest ahead of `from` `direction`'s way by the gap between their edges: anything in line with it
    * (overlapping it across the way) before anything aside, and less drift before more. */
  private def spatial(all: Vector[Component], from: Component, direction: Key): Option[Component] = {
    val sideways = direction == Key.ArrowLeft || direction == Key.ArrowRight
    var best: Option[Component] = None
    var bestScore = 0.0
    all.foreach { c =>
      val dx = centerX(c) - centerX(from)
      val dy = centerY(c) - centerY(from)
      val ahead =
        if (direction == Key.ArrowRight) dx > 1.0
        else if (direction == Key.ArrowLeft) dx < -1.0
        else if (direction == Key.ArrowDown) dy > 1.0
        else dy < -1.0
      if (ahead) {
        val gap =
          if (direction == Key.ArrowRight) c.absoluteLeft - (from.absoluteLeft + from.size.width())
          else if (direction == Key.ArrowLeft) from.absoluteLeft - (c.absoluteLeft + c.size.width())
          else if (direction == Key.ArrowDown) c.absoluteTop - (from.absoluteTop + from.size.height())
          else from.absoluteTop - (c.absoluteTop + c.size.height())
        val inLine =
          if (sideways) c.absoluteTop < from.absoluteTop + from.size.height() && from.absoluteTop < c.absoluteTop + c.size.height()
          else c.absoluteLeft < from.absoluteLeft + from.size.width() && from.absoluteLeft < c.absoluteLeft + c.size.width()
        val drift = if (sideways) math.abs(dy) else math.abs(dx)
        val score = (if (inLine) 0.0 else 1000000.0) + math.max(0.0, gap) + drift * 2.0
        if (best.isEmpty || score < bestScore) {
          best = Some(c)
          bestScore = score
        }
      }
    }
    best
  }

  /** Tab: the next candidate in tree order, round to the first after the last. Whether anything was focused. */
  def next(): Boolean = {
    val all = candidates(scope().getOrElse(ui))
    if (all.isEmpty) false
    else {
      var at = -1
      all.zipWithIndex.foreach { case (c, i) => if (focused().exists(_ eq c)) at = i }
      all((at + 1) % all.length).focus()
      true
    }
  }

  /** Whether `c` is `ancestor` or somewhere under it. */
  def within(c: Component, ancestor: Component): Boolean = {
    var at: Option[Component] = Some(c)
    var found = false
    while (!found && at.nonEmpty) {
      if (at.exists(_ eq ancestor)) found = true
      at = at.flatMap(_.parent())
    }
    found
  }

  /** Whether `c` is on screen: under `ui`, focusable or not. */
  def showing(c: Component): Boolean = within(c, ui)

  /** The focusable components under `root`, in tree order. Walked with a list rather than by recursion: a Roku's
    * stack is small, and every level of a deep tree (a dialog on its sheet) costs it several frames. */
  def candidates(root: Component): Vector[Component] = {
    var found: Vector[Component] = Vector.empty
    var pending: List[Component] = List(root)
    while (pending.nonEmpty) {
      val c = pending.head
      pending = c.childComponents.toList ++ pending.tail
      if (c.focusable()) found = found :+ c
    }
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
