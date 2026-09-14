package everyui.component

import everyui.Color
import everyui.layout.HorizontalLayout
import reactify.*

/** One bar: how tall (0..1 of the chart), its colour, and what a pointer resting on it says. */
case class Bar(fraction: Double, color: Color, tooltip: String = "")

/** A row of bars from the baseline, left to right — a trend, a sparkline. `floor` is the least height in pixels,
  * so a present-but-zero value still shows. */
class Bars(bars: List[Bar], barWidth: Double = 10.0, height: Double = 40.0, spacing: Double = 3.0, floor: Double = 6.0)
  extends Container {
  layout @= Some(HorizontalLayout(spacing))
  size.height @= height
  size.width := contentWidth
  children ++= bars.map { bar =>
    val h = floor + (height - floor) * math.max(0.0, math.min(1.0, bar.fraction))
    new Container {
      size.width @= barWidth
      size.height @= h
      position.top @= height - h
      backgroundColor @= bar.color
      radius @= 2.0
      tooltip @= bar.tooltip
    }
  }
}
