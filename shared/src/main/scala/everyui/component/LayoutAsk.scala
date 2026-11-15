package everyui.component

import reactify.reaction.{Reaction, ReactionStatus}

/** A container laid out again, for `why`, as what its layout places by changes (`Container.follow`): its padding or
  * its size. One reaction for each, not a function attached to each, which cost a closure and its scope. */
private[component] class LayoutAsk(container: Container, why: String) extends Reaction[Double] {
  override def apply(value: Double, previous: Option[Double]): ReactionStatus = {
    container.layoutFor(why)
    ReactionStatus.Continue
  }
}
