package everyui.component.types

import reactify.reaction.{Reaction, ReactionStatus}

/** A `Prop` heard as it changes: its value written out and its callbacks told (`Prop.written`). */
private[types] class PropWrite[T](prop: Prop[T]) extends Reaction[T] {
  override def apply(value: T, previous: Option[T]): ReactionStatus = {
    prop.written(value)
    ReactionStatus.Continue
  }
}
