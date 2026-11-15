package everyui.component.types

import reactify.reaction.{Reaction, ReactionStatus}

/** A `SizeProperty` heard as it changes: written out once something has set it (`SizeProperty.written`). */
private[types] class SizeWrite(size: SizeProperty) extends Reaction[Double] {
  override def apply(value: Double, previous: Option[Double]): ReactionStatus = {
    size.written(value)
    ReactionStatus.Continue
  }
}
