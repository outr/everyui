package universal.component.feature

import reactify.{Val, Var}
import universal.component.Component

/** youi's `MeasuredFeature`: the node's laid-out size, refreshed whenever the component's `measure` fires. */
class MeasuredFeature(component: Component):
  private val w: Var[Double] = Var(0.0)
  private val h: Var[Double] = Var(0.0)

  val width: Val[Double] = w
  val height: Val[Double] = h

  component.measure.on {
    w @= component.node.getWidth()
    h @= component.node.getHeight()
  }
