package everyui.component

/** A row of bars from the baseline, left to right — a trend, a sparkline. `floor` is the least height in pixels,
  * so a present-but-zero value still shows. */
class Bars(bars: List[Bar], barWidth: Double = 10.0, height: Double = 40.0, spacing: Double = 3.0, floor: Double = 6.0)
  extends Container {
  size.width @= math.max(0.0, bars.length * (barWidth + spacing) - spacing)
  size.height @= height
  children ++= bars.zipWithIndex.map { case (bar, i) =>
    val h = floor + (height - floor) * math.max(0.0, math.min(1.0, bar.fraction))
    new Container {
      size.width @= barWidth
      size.height @= h
      position.left @= i * (barWidth + spacing)
      position.top @= height - h
      backgroundColor @= bar.color
      radius @= 2.0
      tooltip @= bar.tooltip
    }
  }
}
