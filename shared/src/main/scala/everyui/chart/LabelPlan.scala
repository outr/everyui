package everyui.chart

/** How a category axis writes its labels so that none runs into the next: every one, or only every `every`th
  * (thinned, the first always among them), and level or `rotated` (turned 45 degrees, reading up to the tick). */
case class LabelPlan(every: Int, rotated: Boolean) {
  /** Whether label `i` is written. */
  def shows(i: Int): Boolean = i % every == 0
}

object LabelPlan {
  /** The gap kept between two labels side by side. */
  val gap: Double = 6.0

  /** The plan for labels `widths` wide and `height` tall, `step` apart, with `room` below the axis for labels turned:
    * level where every one fits; turned where they fit so and their slant fits in `room`; else level, as many
    * skipped between two as keeps them apart. */
  def of(widths: Vector[Double], height: Double, step: Double, room: Double): LabelPlan = {
    val widest = if (widths.isEmpty) 0.0 else widths.max
    val slant = math.sqrt(0.5)
    if (widest + gap <= step) LabelPlan(1, rotated = false)
    else if (height / slant + 2.0 <= step && (widest + height) * slant <= room) LabelPlan(1, rotated = true)
    else LabelPlan(math.max(1, math.ceil((widest + gap) / math.max(step, 1e-9)).toInt), rotated = false)
  }

  /** How far below the axis turned labels `widest` wide and `height` tall reach. */
  def depth(widest: Double, height: Double): Double = (widest + height) * math.sqrt(0.5)
}
