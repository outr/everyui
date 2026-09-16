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
    current.filterNot(c => old.exists(_ eq c)).foreach { c =>
      component.node.append(c.node)
      c.parent @= Some(component)
    }
    component.measure.trigger()
  }

  def +=(child: Component): Unit = static(get :+ child)
  def -=(child: Component): Unit = static(get.filterNot(_ eq child))
  def ++=(seq: Seq[Component]): Unit = static(get ++ seq)
  def clear(): Unit = static(Vector.empty)

  /** The children follow `values`: whenever what it reads changes, each value is rendered again. `values` is data —
    * construct components only in `render`, since a component built inside the tracked expression makes its own
    * measurements dependencies, and the list rebuilds itself forever. */
  def bind[T](values: => List[T])(render: T => Component): Unit = {
    val source: Val[List[T]] = Val(values)
    var previous: List[(T, Component)] = Nil
    source.attachAndFire { all =>
      // a value still in the list keeps the component it already had: a list that grows an entry at a time would
      // otherwise build every entry again, and lose everything each had loaded (a rail of artwork) with it
      // (a find and a filter rather than `span`, which the Roku's runtime has no form of)
      var spare = previous
      val rendered = all.map { value =>
        spare.find(_._1 == value) match {
          case Some(kept) =>
            val component = kept._2
            spare = spare.filterNot(_._2 eq component)
            value -> component
          case None => value -> render(value)
        }
      }
      previous = rendered
      static(rendered.map(_._2).toVector)
    }
  }

  def isEmpty: Boolean = get.isEmpty
  def nonEmpty: Boolean = get.nonEmpty
  def length: Int = get.length

  // Measuring the container measures everything under it first, deepest first, in one walk rather than a trigger per
  // level: a Roku runs out of stack a few levels into a chain of triggers (a dialog on its sheet is enough).
  component.measure.on({
    var order: List[Component] = Nil
    var pending: List[Component] = get.toList
    while (pending.nonEmpty) {
      val c = pending.head
      order = c :: order
      pending = c.childComponents.toList ++ pending.tail
    }
    order.foreach(_.measured.refresh())
  }, Priority.High)
}

