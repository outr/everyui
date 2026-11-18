package everyui.chart

/** Where a mark of a chart is heard (a bar, a point, a cell), in the chart's pixels: the box a pointer is on it in,
  * larger than what is drawn (24 pixels across at least, so a small dot is easy to land on), and which value it is. */
case class ChartMark(category: Int, series: Int, value: Double, x: Double, y: Double, width: Double, height: Double) {
  def contains(px: Double, py: Double): Boolean = px >= x && px <= x + width && py >= y && py <= y + height

  def centerX: Double = x + width / 2.0
  def centerY: Double = y + height / 2.0
}

object ChartMark {
  /** The least a mark's box is across and down. */
  val least: Double = 24.0

  /** The mark at (`px`, `py`): of those whose box holds it, the one whose middle is nearest; None where none does. */
  def at(marks: Vector[ChartMark], px: Double, py: Double): Option[ChartMark] = {
    var best: Option[ChartMark] = None
    var far = 1.0e18
    marks.foreach { m =>
      if (m.contains(px, py)) {
        val dx = m.centerX - px
        val dy = m.centerY - py
        val d = dx * dx + dy * dy
        if (d < far) {
          far = d
          best = Some(m)
        }
      }
    }
    best
  }

  /** A box of at least `least` round (`x`, `y`, `w`, `h`), grown evenly about its middle. */
  def around(category: Int, series: Int, value: Double, x: Double, y: Double, w: Double, h: Double): ChartMark = {
    val gw = math.max(w, least)
    val gh = math.max(h, least)
    ChartMark(category, series, value, x - (gw - w) / 2.0, y - (gh - h) / 2.0, gw, gh)
  }
}
