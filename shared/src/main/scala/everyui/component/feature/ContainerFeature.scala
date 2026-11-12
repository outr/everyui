package everyui.component.feature

import reactify.{Priority, Val, Var}
import everyui.component.Component
import everyui.component.types.Plain
import everyui.layout.LayoutPass

/** youi's `ContainerFeature`: the children, as a reactify `Var`. Changing it adds and removes the children's
  * nodes; measuring the container measures everything under it first.
  *
  * A change costs its own size and the list's, once: what came and what went are found by marking one list and reading
  * the marks off the other, and the container is laid out once for the change, after what was added is measured. */
class ContainerFeature(val component: Component) extends Plain[Vector[Component]](Vector.empty) {
  /** The children whose nodes are placed under the container's now: what a change is worked out from. Not the change's
    * own previous value, which can be one whose nodes were never placed: a change made deep in a chain of changes is
    * heard once the chain unwinds, and is dropped when a later one, made nearer the top, was heard first, so the later
    * one's previous value is the dropped one's, and what was placed before it (a page's loading spinner) stayed under
    * the container, on the screen and moving, while the list no longer held it. */
  private var placed: Vector[Component] = Vector.empty
  /** Whether the nodes are being placed: a change made meanwhile is placed once they are, by the same pass. */
  private var placing = false
  /** Those already here whose nodes the change moved among the others (`ordered`). */
  private var shifted: List[Component] = Nil
  /** The list a `+=` or `++=` is growing, and what it grows it to, while it does: a change from the one to the other
    * that adds only components in no container finds what came at the end, rather than reading the whole list twice
    * over, which for lines added one at a time in a batch read every line again for each. */
  private var grownFrom: Vector[Component] = Vector.empty
  private var grownTo: Vector[Component] = Vector.empty

  attach(_ => place())

  /** Place the nodes as the children are now, and again for any change made while doing so. */
  private def place(): Unit = if (!placing) {
    placing = true
    try {
      while (!(placed eq get)) {
        val old = placed
        val current = get
        placed = current
        change(old, current)
      }
    } finally placing = false
  }

  private def change(old: Vector[Component], current: Vector[Component]): Unit = {
    val end = if ((old eq grownFrom) && (current eq grownTo)) current.drop(old.length) else Vector.empty[Component]
    val grown = end.nonEmpty && end.forall(c => c.parent().isEmpty && !c.takenApart)
    val removed = if (grown) Vector.empty[Component] else ContainerFeature.missing(old, current)
    val added = if (grown) end else ContainerFeature.missing(current, old)
    // what came from another container, and which: once placed here, it is taken out of that one's list, as a node the
    // DOM appends elsewhere leaves where it was (left listed there, it was "there" to whatever asked, and nothing put it
    // back when its tab came up again)
    val movers = added.filter(c => c.parent().exists(p => !(p eq component)))
    val leftBy = movers.map(_.parent().get)
    // how wide what each comes from was: the container it was last taken out of (a kept view put back), or the one it
    // moves from; -1 where it has been in none
    val from = added.map(c => if (c.leftWidth >= 0.0) c.leftWidth
      else c.parent().filter(p => !(p eq component)).map(ContainerFeature.widthOf).getOrElse(-1.0))
    shifted = Nil
    LayoutPass.batch {
      removed.foreach { c =>
        // one put in another container since is there now, its node and the focus with it: it is only off this list
        if (c.parent().exists(p => !(p eq component))) ContainerFeature.forgetMoved(component, c)
        else {
          c.node.remove()
          // where it was, so a focus on it (or inside it) goes to what takes its place
          c.leaves(component, old.indexWhere(_ eq c))
          ContainerFeature.forget(component, c)
          c.parent @= None
          // and how wide it was, for its return: out of every container it is drawn nowhere, so its words are not cut
          // or wrapped again for no box, and a kept view put back where it was finds them as it left them
          c.leftWidth = ContainerFeature.widthOf(component)
        }
      }
      if (grown || (removed.isEmpty && ContainerFeature.startsWith(current, old))) added.foreach { c =>
        // only added to the end (`+=`, `++=`, a list grown by `bind`): each node appended in turn
        component.node.append(c.node)
        c.parent @= Some(component)
        c.containerChanged()
        c.leftParent = None
      }
      else ordered(old, current, removed, added)
      var i = 0
      while (i < movers.length) {
        leftBy(i).asContainer.foreach(_.children.release(movers(i)))
        i += 1
      }
      component.childListChanged(added, removed)
      // what was made before the quality level last changed is drawn as it now has it (`everyui.perf.Quality`)
      if (everyui.perf.Quality.changes > 0)
        added.foreach(c => if (c.drawnAt != everyui.perf.Quality.changes) Component.requality(c))
      // only what was added is measured again, and then the container: those already here have their sizes, and
      // measuring a rail of cards again for each card added to it took a Roku seconds. What comes back from a container
      // as wide (a kept view put back) has its sizes too, but for what was read out of the page meanwhile: putting back
      // a workspace's views read every word in them again
      ContainerFeature.measureAdded(added, from)
      component.measured.refresh()
      component.asContainer.foreach(_.layoutFor("children"))
      if (removed.nonEmpty) everyui.focus.Focus.offScreen()
      // the focus in what was put here or moved along, given the platform's focus again: a field's node taken off the
      // page loses the browser's, though the focus layer's stays on it
      if (added.nonEmpty || shifted.nonEmpty) refocus(added)
      if (added.nonEmpty) everyui.focus.Focus.placed()
      shifted = Nil
      // what is on the screen changed: the frame loop runs, if it was idle, and a focus waiting for this finds it
      everyui.task.Frames.wake()
    }
  }

  /** The nodes in the list's order, which is the order they are drawn in: one added anywhere but the end goes in at its
    * place (a clip parked under the one playing was appended on top of it, and showed its first frame, stopped, through
    * the rest of the clip it was parked under), and one moved in the list is moved among the nodes. */
  private def ordered(old: Vector[Component], current: Vector[Component], removed: Vector[Component],
                      added: Vector[Component]): Unit = {
    removed.foreach(_.listMark = true)
    var placed = old.filterNot(_.listMark)
    removed.foreach(_.listMark = false)
    added.foreach(_.listMark = true)
    current.zipWithIndex.foreach { case (c, i) =>
      if (c.listMark) {
        if (i >= placed.size) component.node.append(c.node) else component.node.insert(c.node, i)
        c.parent @= Some(component)
        c.containerChanged()
        c.leftParent = None
        placed = placed.patch(i, Vector(c), 0)
      }
      // (only where the list and the nodes can be matched place for place: a list holding one component twice has more
      // places than nodes, and indexing past them took the app down on returning to the games lobby)
      else if (i < placed.size && !(placed(i) eq c)) {
        component.node.insert(c.node, i)
        shifted = c :: shifted
        placed = placed.filterNot(_ eq c).patch(i, Vector(c), 0)
      }
    }
    added.foreach(_.listMark = false)
  }

  /** Where the focus is in one of `added` or of those moved along (`shifted`), and on the screen, its platform focus
    * put back (Component.refocus): a field's node moved, or taken off the page and put back, loses the browser's
    * focus and its caret. */
  private def refocus(added: Vector[Component]): Unit = everyui.focus.Focus.focused().foreach { f =>
    // the one of this container's children the focus is in, if it is in one
    var at: Component = f
    while (at.parent().exists(p => !(p eq component))) at = at.parent().get
    val top = at
    if (at.parent().nonEmpty && (added.exists(_ eq top) || shifted.exists(_ eq top)) && everyui.focus.Focus.showing(f))
      f.refocus()
  }

  /** `child` taken off the list but not off the screen: another container has put it in itself (its change calls
    * this), and its node with it. A list bound to what it reads (`children := …`) is left to say what it holds. */
  private[everyui] def release(child: Component): Unit =
    if (references.isEmpty && get.exists(_ eq child)) static(get.filterNot(_ eq child))

  def +=(child: Component): Unit = grow(get :+ child)
  def -=(child: Component): Unit = static(get.filterNot(_ eq child))
  def ++=(seq: Seq[Component]): Unit = grow(get ++ seq)
  def clear(): Unit = static(Vector.empty)

  private def grow(to: Vector[Component]): Unit = {
    grownFrom = get
    grownTo = to
    try static(to)
    finally {
      grownFrom = Vector.empty
      grownTo = Vector.empty
    }
  }

  /** The children become `parts`, and each that is not among them is taken apart (Component.dispose, a slice a turn:
    * `Component.disposeGradually`), never to be shown again: a board drawn afresh for each question, a page's rows
    * made again, where replacing them with `@=` left each old one bound to the theme, and so held, for good.
    * {{{
    *   stage.children.replaceMaking(Vector(board.build()))
    * }}} */
  def replaceMaking(parts: Vector[Component]): Unit = {
    val gone = ContainerFeature.missing(get, parts)
    if (everyui.perf.PerfCounters.on) {
      val was = get
      val newly = parts.count(p => !was.exists(_ eq p))
      everyui.perf.PerfCounters.replaced(newly, parts.size - newly)
    }
    static(parts)
    if (gone.nonEmpty) Component.disposeGradually(gone.toList, () => everyui.render.Platform.collect())
  }

  /** The children follow `values`: whenever what it reads changes, each value is rendered again. `values` is data —
    * construct components only in `render`, since a component built inside the tracked expression makes its own
    * measurements dependencies, and the list rebuilds itself forever.
    *
    * A component `render` made is taken apart (Component.dispose) at the list's next change after its value left, or
    * with the container: left whole, it stayed bound to whatever lives on (the theme, the host's size), and a list
    * rendered again every two seconds held every one it had ever made until the page ran out of memory. One `render`
    * hands back from elsewhere (`parts(key)`, made before) is only removed. A `render` that hands back again one it made
    * (a `lazy val` first read inside it) keeps what it makes: this list takes none of them apart from then on. */
  def bind[T](values: => List[T])(render: T => Component): Unit = bindAll(values, render, false)

  /** `bind`, taking apart every component whose value has left the list, whether `render` made it or not. Not for a
    * list that hands back components it keeps (`c => c`). */
  def bindMaking[T](values: => List[T])(render: T => Component): Unit = bindAll(values, render, true)

  private def bindAll[T](values: => List[T], render: T => Component, making: Boolean): Unit = {
    val source: Val[List[T]] = Val(values)
    // each value with its component, and whether `render` made it (and so this takes it apart once it has left)
    var previous: List[(T, Component, Boolean)] = Nil
    // made by `render` and gone at the last change: taken apart at the next unless `render` hands one back
    var leaving: List[Component] = Nil
    // `render` handed back one it had made: it keeps them, and none is taken apart
    var keeps = false
    component.onDispose(() => leaving.foreach(c => if (ContainerFeature.unplaced(c, component)) c.dispose()))
    source.attachAndFire { all =>
      // a value still in the list keeps the component it already had: a list that grows an entry at a time would
      // otherwise build every entry again, and lose everything each had loaded (a rail of artwork) with it
      // (a find and a filter rather than `span`, which the Roku's runtime has no form of). The one next in line is
      // tried first: a list kept in its order finds each value there, not by a pass over those left
      var spare = previous
      val rendered = all.map { value =>
        if (spare.nonEmpty && ContainerFeature.same(spare.head._1, value)) {
          val kept = spare.head
          spare = spare.tail
          kept
        }
        else spare.find(kept => ContainerFeature.same(kept._1, value)) match {
          case Some(kept) =>
            spare = spare.filterNot(_._2 eq kept._2)
            kept
          case None =>
            val made = renderMaking(value, render)
            if (leaving.exists(_ eq made._2)) {
              keeps = true
              (value, made._2, false)
            }
            else made
        }
      }
      if (everyui.perf.PerfCounters.on) {
        val newly = rendered.count(r => !previous.exists(_._2 eq r._2))
        everyui.perf.PerfCounters.listed(newly, rendered.size - newly)
      }
      previous = rendered
      static(rendered.map(_._2).toVector)
      // (not one put in another container since, which lives on there)
      val gone = (if (making) spare.map(_._2) else if (keeps) Nil else leaving)
        .filter(c => ContainerFeature.unplaced(c, component))
      leaving = if (making || keeps) Nil else spare.filter(_._3).map(_._2)
      if (gone.nonEmpty) {
        gone.foreach(_.dispose())
        everyui.render.Platform.collect()
      }
    }
  }

  /** `value` rendered, with whether `render` made the component it returned: each component made meanwhile tells
    * Component.rendered. One rendering inside another (a list in a component another list renders) leaves the outer
    * one seeing what it made too. */
  private def renderMaking[T](value: T, render: T => Component): (T, Component, Boolean) = {
    val wasRendering = Component.rendering(0)
    val outer = Component.rendered(0)
    Component.rendering(0) = true
    Component.rendered(0) = Nil
    var fresh: List[Component] = Nil
    val out = try render(value) finally {
      fresh = Component.rendered(0)
      Component.rendering(0) = wasRendering
      Component.rendered(0) = if (wasRendering) fresh ::: outer else Nil
    }
    (value, out, fresh.exists(_ eq out))
  }

  def isEmpty: Boolean = get.isEmpty
  def nonEmpty: Boolean = get.nonEmpty
  def length: Int = get.length

  // Measuring the container measures everything under it first, deepest first, in one walk rather than a trigger per
  // level: a Roku runs out of stack a few levels into a chain of triggers (a dialog on its sheet is enough).
  component.measure.on(ContainerFeature.measureUnder(get.toList), Priority.High)
}

object ContainerFeature {
  /** What the focus was last on in `from` and in each container it is in (Container.remembered), forgotten where it is
    * `gone` or in it: a long-lived container (a screen kept, the root) remembered a row of a board drawn afresh, and
    * held it, and through what follows it the whole board, long after the board was taken away. One not in `gone` is
    * kept. */
  def forget(from: Component, gone: Component): Unit = {
    var at: Option[Component] = Some(from)
    while (at.nonEmpty) {
      at.get match {
        case box: everyui.component.Container =>
          if (box.remembered.exists(r => everyui.focus.Focus.within(r, gone))) box.remembered = None
        case _ => ()
      }
      at = at.get.parent()
    }
  }

  /** `moved`, taken from `from` into another container: forgotten by `from` and each container it is in, up to the
    * first that holds it still (where it went is in that one too), which goes on remembering it. */
  def forgetMoved(from: Component, moved: Component): Unit = {
    var at: Option[Component] = Some(from)
    while (at.nonEmpty && !everyui.focus.Focus.within(moved, at.get)) {
      at.get match {
        case box: everyui.component.Container =>
          if (box.remembered.exists(r => everyui.focus.Focus.within(r, moved))) box.remembered = None
        case _ => ()
      }
      at = at.get.parent()
    }
  }

  /** Whether `c` is to be taken apart with the list it left: it is in no other container (one moved into another
    * lives on there). */
  def unplaced(c: Component, list: Component): Boolean = c.parent().forall(_ eq list)

  /** The same value: the very one first, which is all a list kept from one change to the next needs, before `==`, which
    * on a Roku compares a value of pages of titles field by field. */
  def same[T](a: T, b: T): Boolean = (a.asInstanceOf[AnyRef] eq b.asInstanceOf[AnyRef]) || a == b

  /** What of `from` is not in `in`, by identity: `in` marked, then `from` read. */
  def missing(from: Vector[Component], in: Vector[Component]): Vector[Component] = {
    in.foreach(_.listMark = true)
    val out = from.filterNot(_.listMark)
    in.foreach(_.listMark = false)
    out
  }

  /** Whether `list` begins with every one of `start`, in its order. */
  def startsWith(list: Vector[Component], start: Vector[Component]): Boolean = list.length >= start.length && {
    var i = 0
    while (i < start.length && (list(i) eq start(i))) i += 1
    i == start.length
  }

  /** How wide `c` is now, as last laid out (not read as a value being worked out would read it). */
  private[everyui] def widthOf(c: Component): Double = c.size.width.option.getOrElse(0.0)

  /** `added` measured, and everything under each, but for one that comes from a container (`from`, its width) and has
    * been measured: a kept view put back, or one moved. That is looked at once the turn is over, when what it is in has
    * been laid out (`settleReturns`): back in as wide a room, its sizes are as they were, and only what was read out of
    * the page since is read again (`readAgain`); in a room of another width it is measured whole, since words with no
    * width of their own wrap at their box. */
  private[feature] def measureAdded(added: Vector[Component], from: Vector[Double]): Unit = {
    var fresh: List[Component] = Nil
    var i = added.length - 1
    while (i >= 0) {
      val c = added(i)
      if (from(i) >= 0.0 && c.measured.readOrQueued) {
        c.leftWidth = from(i)
        val first = returning(0).isEmpty
        returning(0) = c :: returning(0)
        if (first) everyui.render.Platform.afterTurn(() => settleReturns())
      }
      else {
        c.leftWidth = -1.0
        fresh = c :: fresh
      }
      i -= 1
    }
    measureUnder(fresh)
  }

  // what came back this turn (`measureAdded`), the newest first, each with the width it came from in `leftWidth`: an
  // array of one rather than a var, which a Roku may not be able to set
  private val returning: Array[List[Component]] = Array(Nil)

  // each that came back and is still where it came to, measured as its room says (`measureAdded`); one taken out again
  // meanwhile has the width it left then, and one put back twice is looked at once
  private def settleReturns(): Unit = {
    val back = returning(0).reverse
    returning(0) = Nil
    back.foreach { c =>
      val was = c.leftWidth
      if (was >= 0.0 && !c.takenApart) c.parent.option match {
        case Some(Some(p)) =>
          c.leftWidth = -1.0
          if (widthOf(p) == was) readAgain(c) else measureUnder(List(c))
        case _ => ()
      }
    }
  }

  /** What under `root` (and `root`) was read out of the page since it was last in it (MeasuredFeature.wasRead), read
    * again, the deepest first, and the marks let go: a kept view put back reads nothing else. Only the marked are
    * walked. */
  def readAgain(root: Component): Unit = if (root.readAwayBelow) {
    var order: List[Component] = Nil
    var pending: List[Component] = List(root)
    while (pending.nonEmpty) {
      val c = pending.head
      pending = pending.tail
      c.readAwayBelow = false
      if (c.measured.readAway) order = c :: order
      c.childComponents.foreach(k => if (k.readAwayBelow) pending = k :: pending)
    }
    order.foreach(_.measured.refresh())
  }

  /** Measure `roots` and everything under them, deepest first: each after all that come after it in tree order, so what
    * is under a component before it. Walked with arrays grown as they fill, rather than a list of what is still to
    * walk joined to each one's children, which on a Roku is a copy of the list for every component (a title's page put
    * together a rail at a time walked each rail's cards again for every level it was put in). */
  def measureUnder(roots: List[Component]): Unit = {
    var stack = new Array[Component](64)
    var depth = 0
    roots.reverse.foreach { r =>
      if (depth == stack.length) stack = grown(stack, depth)
      stack(depth) = r
      depth += 1
    }
    var order = new Array[Component](64)
    var count = 0
    while (depth > 0) {
      depth -= 1
      val c = stack(depth)
      c.readAwayBelow = false
      if (count == order.length) order = grown(order, count)
      order(count) = c
      count += 1
      val kids = c.childComponents
      var i = kids.length - 1
      while (i >= 0) {
        if (depth == stack.length) stack = grown(stack, depth)
        stack(depth) = kids(i)
        depth += 1
        i -= 1
      }
    }
    while (count > 0) {
      count -= 1
      order(count).measured.refresh()
    }
  }

  /** `items`, `count` of them kept, in an array twice as long. */
  private def grown(items: Array[Component], count: Int): Array[Component] = {
    val bigger = new Array[Component](items.length * 2)
    var i = 0
    while (i < count) {
      bigger(i) = items(i)
      i += 1
    }
    bigger
  }
}

