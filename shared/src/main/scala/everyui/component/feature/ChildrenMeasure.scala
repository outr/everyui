package everyui.component.feature

import reactify.Priority
import reactify.reaction.{Reaction, ReactionStatus}

/** A container measured (`ContainerFeature`): everything under it measured first, deepest first, in one walk, before the
  * container itself (a high priority). */
private[feature] class ChildrenMeasure(children: ContainerFeature) extends Reaction[Unit] {
  override def priority: Double = Priority.High

  override def apply(value: Unit, previous: Option[Unit]): ReactionStatus = {
    ContainerFeature.measureUnder(children.get.toList)
    ReactionStatus.Continue
  }
}
