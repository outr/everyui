package everyui.component.feature

import reactify.*
import reactify.reaction.{Reaction, ReactionStatus, Reactions}
import everyui.component.Component
import everyui.component.types.{PositionType, Prop}

/** youi's `PositionFeature`: `left`/`top` are the node's coordinates; `center`/`right` and `middle`/`bottom` are
  * derived from them through the component's effective size, so animating `right` moves `left`. */
class PositionFeature(component: Component):
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

/** What youi's position `Dep` does — reading derives from the owner, writing sets the owner through the inverse
  * (`right := x` is `left := x - width`) — without keeping a derived `Val` up to date on every owner change. Reads
  * are still dependency-tracked (they read the owner and the size), and the value is only watched once something
  * attaches a reaction to it. */
class DerivedPosition(owner: Var[Double], toDerived: Double => Double, toOwner: Double => Double)
  extends Reactive[Double] with Stateful[Double] with Mutable[Double]:

  override def get: Double = toDerived(owner())

  override def set(f: => Double): Unit = owner := toOwner(f)

  override lazy val reactions: Reactions[Double] =
    val attached = new Reactions[Double]
    val watched = Val(toDerived(owner()))
    val self = this
    watched.reactions += new Reaction[Double] {
      override def apply(value: Double, previous: Option[Double]): ReactionStatus = {
        self.fireDerived(value, previous, attached())
        ReactionStatus.Continue
      }
    }
    attached

  private def fireDerived(value: Double, previous: Option[Double], to: List[Reaction[Double]]): Unit =
    fire(value, previous, to)
