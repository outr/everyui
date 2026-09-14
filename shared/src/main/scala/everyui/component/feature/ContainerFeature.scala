package everyui.component.feature

import reactify.{Priority, Val, Var}
import everyui.component.Component

/** youi's `ContainerFeature`: the children, as a reactify `Var`. Changing it adds and removes the children's
  * nodes; measuring the container measures its children first. */
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
    source.attachAndFire(all => static(all.map(render).toVector))
  }

  def isEmpty: Boolean = get.isEmpty
  def nonEmpty: Boolean = get.nonEmpty
  def length: Int = get.length

  component.measure.on({
    get.foreach(_.measure.trigger())
  }, Priority.High)
}
