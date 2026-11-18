package everyui.chart

/** A mark along an axis: where it is, in the axis's own values (a value, or a moment in epoch milliseconds), and the
  * words written at it. */
case class Tick(value: Double, label: String)
