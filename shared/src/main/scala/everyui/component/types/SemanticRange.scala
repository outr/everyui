package everyui.component.types

/** Where a control that sets a number stands, for a screen reader: `now` between `min` and `max`, and `text`, the
  * value as it is shown ("40%", "3 km"). */
case class SemanticRange(now: Double, min: Double, max: Double, text: String)
