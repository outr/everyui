package everyui.component

import reactify.reaction.{Reaction, ReactionStatus}

/** A child's place heard for its container (`ChildWatch`): its left or its top changed. */
private[component] class ChildPlace(watch: ChildWatch) extends Reaction[Double] {
  override def apply(value: Double, previous: Option[Double]): ReactionStatus = {
    watch.container.childWatched(watch, false)
    ReactionStatus.Continue
  }
}
