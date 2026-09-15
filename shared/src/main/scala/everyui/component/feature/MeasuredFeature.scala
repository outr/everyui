package everyui.component.feature

import reactify.{Val, Var}
import everyui.component.Component

/** youi's `MeasuredFeature`: the node's laid-out size, refreshed whenever the component's `measure` fires (and, for a
  * component inside a container, whenever the container's does: see `ContainerFeature`). */
class MeasuredFeature(component: Component) {
  private val w: Var[Double] = Var(0.0)
  private val h: Var[Double] = Var(0.0)

  val width: Val[Double] = w
  val height: Val[Double] = h

  /** Read the node's size now. */
  def refresh(): Unit = {
    w @= component.node.getWidth()
    h @= component.node.getHeight()
  }

  component.measure.on(refresh())
}
