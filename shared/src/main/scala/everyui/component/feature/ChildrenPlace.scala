package everyui.component.feature

import everyui.component.Component
import reactify.reaction.{Reaction, ReactionStatus}

/** A container's children heard as they change (`ContainerFeature`): their nodes placed as the list now has them. */
private[feature] class ChildrenPlace(children: ContainerFeature) extends Reaction[Vector[Component]] {
  override def apply(value: Vector[Component], previous: Option[Vector[Component]]): ReactionStatus = {
    children.place()
    ReactionStatus.Continue
  }
}
