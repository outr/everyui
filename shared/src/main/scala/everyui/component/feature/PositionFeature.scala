package everyui.component.feature

import reactify.*
import reactify.reaction.{Reaction, ReactionStatus, Reactions}
import everyui.component.Component
import everyui.component.types.{PositionType, Prop}

/** youi's `PositionFeature`: `left`/`top` are the node's coordinates; `center`/`right` and `middle`/`bottom` are
  * derived from them through the component's effective size, so animating `right` moves `left`. */
class PositionFeature(component: Component) {
  lazy val x: Prop[Double] = new Prop[Double](component.node.getX(), component.node.setX)
  lazy val y: Prop[Double] = new Prop[Double](component.node.getY(), component.node.setY)

  lazy val `type`: Prop[PositionType] =
    new Prop[PositionType](PositionType.Static, pt => component.node.setPositionType(pt.name))

  lazy val left: Var[Double] = x
  lazy val center: DerivedPosition =
    new DerivedPosition(left, l => l + component.effectiveWidth() / 2.0, c => c - component.effectiveWidth() / 2.0)
  lazy val right: DerivedPosition =
    new DerivedPosition(left, l => l + component.effectiveWidth(), r => r - component.effectiveWidth())

  lazy val top: Var[Double] = y
  lazy val middle: DerivedPosition =
    new DerivedPosition(top, t => t + component.effectiveHeight() / 2.0, m => m - component.effectiveHeight() / 2.0)
  lazy val bottom: DerivedPosition =
    new DerivedPosition(top, t => t + component.effectiveHeight(), b => b - component.effectiveHeight())
}

