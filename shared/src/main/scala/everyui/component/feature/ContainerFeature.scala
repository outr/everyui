package everyui.component.feature

import reactify.{Priority, Val, Var}
import everyui.component.Component
import everyui.layout.LayoutPass

/** youi's `ContainerFeature`: the children, as a reactify `Var`. Changing it adds and removes the children's
  * nodes; measuring the container measures everything under it first.
  *
  * A change costs its own size and the list's, once: what came and what went are found by marking one list and reading
  * the marks off the other, and the container is laid out once for the change, after what was added is measured. */
class ContainerFeature(val component: Component) extends Var[Vector[Component]](Vector.empty) {
  /** The children whose nodes are placed under the container's now: what a change is worked out from. Not the change's
    * own previous value, which can be one whose nodes were never placed: a change made deep in a chain of changes is
    * heard once the chain unwinds, and is dropped when a later one, made nearer the top, was heard first, so the later
    * one's previous value is the dropped one's, and what was placed before it (a page's loading spinner) stayed under
    * the container, on the screen and moving, while the list no longer held it. */
  private var placed: Vector[Component] = Vector.empty
  /** Whether the nodes are being placed: a change made meanwhile is placed once they are, by the same pass. */
  private var placing = false

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
    val removed = ContainerFeature.missing(old, current)
    val added = ContainerFeature.missing(current, old)
    LayoutPass.batch {
      removed.foreach { c =>
        c.node.remove()
        // where it was, so a focus on it (or inside it) goes to what takes its place
        c.leaves(component, old.indexWhere(_ eq c))
        ContainerFeature.forget(component, c)
        c.parent @= None
      }
      if (removed.isEmpty && ContainerFeature.startsWith(current, old)) added.foreach { c =>
        // only added to the end (`+=`, `++=`, a list grown by `bind`): each node appended in turn
        component.node.append(c.node)
        c.parent @= Some(component)
        c.leftParent = None
      }
      else ordered(old, current, removed, added)
      component.childListChanged(added, removed)
      // what was made before the quality level last changed is drawn as it now has it (`everyui.perf.Quality`)
      if (everyui.perf.Quality.changes > 0)
        added.foreach(c => if (c.drawnAt != everyui.perf.Quality.changes) Component.requality(c))
      // only what was added is measured again, and then the container: those already here have their sizes, and
      // measuring a rail of cards again for each card added to it took a Roku seconds
      ContainerFeature.measureUnder(added.toList)
      component.measured.refresh()
      component.requestLayout()
      if (removed.nonEmpty) everyui.focus.Focus.offScreen()
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
        c.leftParent = None
        placed = placed.patch(i, Vector(c), 0)
      }
      // (only where the list and the nodes can be matched place for place: a list holding one component twice has more
      // places than nodes, and indexing past them took the app down on returning to the games lobby)
      else if (i < placed.size && !(placed(i) eq c)) {
        component.node.insert(c.node, i)
        placed = placed.filterNot(_ eq c).patch(i, Vector(c), 0)
      }
    }
    added.foreach(_.listMark = false)
  }

  def +=(child: Component): Unit = static(get :+ child)
  def -=(child: Component): Unit = static(get.filterNot(_ eq child))
  def ++=(seq: Seq[Component]): Unit = static(get ++ seq)
  def clear(): Unit = static(Vector.empty)

  /** The children become `parts`, and each that is not among them is taken apart (Component.dispose, a slice a turn:
    * `Component.disposeGradually`), never to be shown again: a board drawn afresh for each question, a page's rows
    * made again, where replacing them with `@=` left each old one bound to the theme, and so held, for good.
    * {{{
    *   stage.children.replaceMaking(Vector(board.build()))
    * }}} */
  def replaceMaking(parts: Vector[Component]): Unit = {
    val gone = ContainerFeature.missing(get, parts)
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
    component.onDispose(() => leaving.foreach(_.dispose()))
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
      previous = rendered
      static(rendered.map(_._2).toVector)
      val gone = if (making) spare.map(_._2) else if (keeps) Nil else leaving
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

  /** Measure `roots` and everything under them, deepest first. */
  def measureUnder(roots: List[Component]): Unit = {
    var order: List[Component] = Nil
    var pending: List[Component] = roots
    while (pending.nonEmpty) {
      val c = pending.head
      order = c :: order
      pending = c.childComponents.toList ++ pending.tail
    }
    order.foreach(_.measured.refresh())
  }
}

