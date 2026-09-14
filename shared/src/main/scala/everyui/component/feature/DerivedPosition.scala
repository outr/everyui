package everyui.component.feature

import reactify.*
import reactify.reaction.{Reaction, ReactionStatus, Reactions}

/** What youi's position `Dep` does — reading derives from the owner, writing sets the owner through the inverse
  * (`right := x` is `left := x - width`) — without keeping a derived `Val` up to date on every owner change. Reads
  * are still dependency-tracked (they read the owner and the size), and the value is only watched once something
  * attaches a reaction to it. */
class DerivedPosition(owner: Var[Double], toDerived: Double => Double, toOwner: Double => Double)
  extends Reactive[Double] with Stateful[Double] with Mutable[Double] {

  override def get: Double = toDerived(owner())

  override def set(f: => Double): Unit = owner := toOwner(f)

  override lazy val reactions: Reactions[Double] = {
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
  }

  private def fireDerived(value: Double, previous: Option[Double], to: List[Reaction[Double]]): Unit =
    fire(value, previous, to)
}
