package everyui.component.feature

import reactify.{Priority, Val, Var}
import everyui.component.Component

/** youi's `ContainerFeature`: the children, as a reactify `Var`. Changing it adds and removes the children's
  * nodes; measuring the container measures everything under it first. */
class ContainerFeature(val component: Component) extends Var[Vector[Component]](Vector.empty) {
  changes { (old, current) =>
    old.filterNot(c => current.exists(_ eq c)).foreach { c =>
      c.node.remove()
      c.parent @= None
    }
    val added = current.filterNot(c => old.exists(_ eq c))
    // the nodes in the list's order, which is the order they are drawn in: one added anywhere but the end goes in at its
    // place (a clip parked under the one playing was appended on top of it, and showed its first frame, stopped,
    // through the rest of the clip it was parked under), and one moved in the list is moved among the nodes
    var placed = old.filter(c => current.exists(_ eq c))
    current.zipWithIndex.foreach { case (c, i) =>
      if (added.exists(_ eq c)) {
        if (i >= placed.size) component.node.append(c.node) else component.node.insert(c.node, i)
        c.parent @= Some(component)
        placed = placed.patch(i, Vector(c), 0)
      }
      // (only where the list and the nodes can be matched place for place: a list holding one component twice has more
      // places than nodes, and indexing past them took the app down on returning to the games lobby)
      else if (i < placed.size && !(placed(i) eq c)) {
        component.node.insert(c.node, i)
        placed = placed.filterNot(_ eq c).patch(i, Vector(c), 0)
      }
    }
    // only what was added is measured again, and then the container: those already here have their sizes, and
    // measuring a rail of cards again for each card added to it took a Roku seconds
    ContainerFeature.measureUnder(added.toList)
    component.measured.refresh()
  }

  def +=(child: Component): Unit = static(get :+ child)
  def -=(child: Component): Unit = static(get.filterNot(_ eq child))
  def ++=(seq: Seq[Component]): Unit = static(get ++ seq)
  def clear(): Unit = static(Vector.empty)

  /** The children follow `values`: whenever what it reads changes, each value is rendered again. `values` is data —
    * construct components only in `render`, since a component built inside the tracked expression makes its own
    * measurements dependencies, and the list rebuilds itself forever. */
  def bind[T](values: => List[T])(render: T => Component): Unit = bindAll(values, render, false)

  /** `bind`, where `render` makes each component afresh: one whose value has left the list is taken apart
    * (Component.dispose), never to be shown again. Not for a list that hands back components it keeps (`c => c`). */
  def bindMaking[T](values: => List[T])(render: T => Component): Unit = bindAll(values, render, true)

  private def bindAll[T](values: => List[T], render: T => Component, making: Boolean): Unit = {
    val source: Val[List[T]] = Val(values)
    var previous: List[(T, Component)] = Nil
    source.attachAndFire { all =>
      // a value still in the list keeps the component it already had: a list that grows an entry at a time would
      // otherwise build every entry again, and lose everything each had loaded (a rail of artwork) with it
      // (a find and a filter rather than `span`, which the Roku's runtime has no form of)
      var spare = previous
      val rendered = all.map { value =>
        spare.find(kept => ContainerFeature.same(kept._1, value)) match {
          case Some(kept) =>
            val component = kept._2
            spare = spare.filterNot(_._2 eq component)
            value -> component
          case None => value -> render(value)
        }
      }
      previous = rendered
      static(rendered.map(_._2).toVector)
      if (making && spare.nonEmpty) {
        spare.foreach(gone => gone._2.dispose())
        everyui.render.Platform.collect()
      }
    }
  }

  def isEmpty: Boolean = get.isEmpty
  def nonEmpty: Boolean = get.nonEmpty
  def length: Int = get.length

  // Measuring the container measures everything under it first, deepest first, in one walk rather than a trigger per
  // level: a Roku runs out of stack a few levels into a chain of triggers (a dialog on its sheet is enough).
  component.measure.on(ContainerFeature.measureUnder(get.toList), Priority.High)
}

object ContainerFeature {
  /** The same value: the very one first, which is all a list kept from one change to the next needs, before `==`, which
    * on a Roku compares a value of pages of titles field by field. */
  def same[T](a: T, b: T): Boolean = (a.asInstanceOf[AnyRef] eq b.asInstanceOf[AnyRef]) || a == b

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

