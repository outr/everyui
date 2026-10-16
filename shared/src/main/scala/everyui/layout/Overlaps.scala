package everyui.layout

import everyui.component.{Component, Container}
import everyui.render.Platform
import reactify.*

/** A check for while an app is being made: something drawn over its neighbour by mistake, as a list placed under a
  * header at a guessed height is once the header grows taller than the guess. With `warn` on, the screen is looked
  * over after each layout pass and every second, and each pair of overlapping siblings is told once
  * (`scribe.warn`), with their kinds, their boxes (left, top, width by height, in what holds them) and where they are,
  * then again only if their boxes change:
  * {{{
  *   Overlaps.warn @= true
  *   // WARN overlap: TextArea (16,16 388x58) and VirtualList (16,62 388x622) in ProvidersPanel < Container < ui$
  * }}}
  *
  * What it reports: in a row, a column, a flow or a grid, any two neighbours that overlap at all; among children an app
  * places by hand (in a plain `Container`, or a kind of its own), two that overlap in part, neither wholly inside the
  * other (a picture behind words or a plate under a label is layered on purpose, and holds what lies over it).
  *
  * What it leaves out: what is hidden (`visible`) or has no size; what is marked as layered on purpose (`allow`: a
  * dialog's sheet, a canvas's pins and a graph's edges are); and the parts EveryUI's own components place by hand (a
  * list's scrollbar over its rows), theirs to layer. On a Roku, whose class names do not say whose a component is,
  * only rows, columns, flows and grids are looked at. Off, as it is by default, it costs nothing: nothing is looked at
  * and nothing is kept. */
object Overlaps {
  /** Whether overlapping siblings are reported. */
  val warn: Var[Boolean] = Var(false)

  /** `c`, marked as layered on purpose: never reported against what is beside it, nor are the children it places by
    * hand reported against one another (a canvas of pins, the nodes and edges of a graph). */
  def allow[C <: Component](c: C): C = {
    c.overlays = true
    c
  }

  // read where layout passes end, without the cost of a Var's read
  private var on: Boolean = false
  private var ticking: Boolean = false
  private var soon: Boolean = false
  // the pairs told of, with their boxes as told
  private var told: List[OverlapPair] = Nil

  warn.attach { w =>
    on = w
    if (!w) told = Nil
    else {
      laidOut()
      if (!ticking) {
        ticking = true
        Platform.after(1.0, () => tick())
      }
    }
  }

  private def tick(): Unit = if (on) {
    check()
    Platform.after(1.0, () => tick())
  } else ticking = false

  /** A layout pass ended (`LayoutPass.flush`): the screen is looked over on a turn of its own, once however many end. */
  private[everyui] def laidOut(): Unit = if (on && !soon) {
    soon = true
    Platform.later(() => {
      soon = false
      if (on) check()
    })
  }

  /** Look over everything under `root` now, telling each overlap not told of yet (or whose boxes changed); the pairs
    * found, whether told or not. Walked with a list rather than by recursion (a Roku's stack is small). */
  def check(root: Component = everyui.ui): List[OverlapPair] = {
    var found: List[OverlapPair] = Nil
    var pending: List[Component] = List(root)
    while (pending.nonEmpty) {
      val at = pending.head
      pending = pending.tail
      if (at.notHidden) {
        val kids = at.childComponents
        pending = kids.toList ::: pending
        at.asContainer.foreach(c => found = pairs(c, kids.filter(seen)) ::: found)
      }
    }
    found.foreach { p =>
      if (!told.exists(t => (t.first eq p.first) && (t.second eq p.second) && t.boxes == p.boxes)) everyui.scribe.warn(
        "overlap: " + p.boxes + " in " + Component.lineage(p.container))
    }
    told = found
    found
  }

  // Whether children placed by hand in `container` are looked at: those an app places (in a plain container, or one of
  // its own kinds), not EveryUI's own parts, layered as each component means them (a list's scrollbar over its rows).
  // Told apart by the package; a Roku's class names have none, so there only rows, columns, flows and grids are.
  private def placedByApp(container: Container): Boolean = {
    val name = container.getClass.getName
    name == "everyui.component.Container" || (name.contains(".") && !name.startsWith("everyui."))
  }

  private def seen(c: Component): Boolean = c.notHidden && !c.overlays && c.size.width() > 0.0 && c.size.height() > 0.0

  // a row, a column, a flow or a grid places its children apart: neighbours in its order overlapping at all are wrong.
  // Placed by hand, any two may be layered; only those overlapping in part are reported (every pair, up to 200).
  private def pairs(container: Container, kids: Vector[Component]): List[OverlapPair] = {
    var found: List[OverlapPair] = Nil
    if (laysApart(container)) {
      var i = 1
      while (i < kids.length) {
        if (overlap(kids(i - 1), kids(i))) found = pair(container, kids(i - 1), kids(i)) :: found
        i += 1
      }
    }
    else if (kids.length <= 200 && !container.overlays && placedByApp(container)) {
      var i = 0
      while (i < kids.length) {
        var j = i + 1
        while (j < kids.length) {
          val a = kids(i)
          val b = kids(j)
          if (overlap(a, b) && !inside(a, b) && !inside(b, a)) found = pair(container, a, b) :: found
          j += 1
        }
        i += 1
      }
    }
    found
  }

  private def laysApart(container: Container): Boolean = container.layout() match {
    case Some(_: PlacedLayout) => false
    case Some(_) => true
    case None => false
  }

  // half a pixel either way is rounding, not an overlap
  private def overlap(a: Component, b: Component): Boolean =
    a.position.left() + a.size.width() > b.position.left() + 0.5 && b.position.left() + b.size.width() > a.position.left() + 0.5 &&
      a.position.top() + a.size.height() > b.position.top() + 0.5 && b.position.top() + b.size.height() > a.position.top() + 0.5

  // whether `a` lies wholly inside `b`
  private def inside(a: Component, b: Component): Boolean =
    a.position.left() >= b.position.left() - 0.5 && a.position.top() >= b.position.top() - 0.5 &&
      a.position.left() + a.size.width() <= b.position.left() + b.size.width() + 0.5 &&
      a.position.top() + a.size.height() <= b.position.top() + b.size.height() + 0.5

  private def pair(container: Container, a: Component, b: Component): OverlapPair =
    OverlapPair(container, a, b, box(a) + " and " + box(b))

  private def box(c: Component): String = c.kind + " (" + whole(c.position.left()) + "," + whole(c.position.top()) + " " +
    whole(c.size.width()) + "x" + whole(c.size.height()) + ")"

  private def whole(d: Double): String = math.round(d).toString
}
