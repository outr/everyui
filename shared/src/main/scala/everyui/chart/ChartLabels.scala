package everyui.chart

import everyui.component.{Container, TextView}
import everyui.component.types.TextOverflow
import everyui.theme.Theme
import reactify.*

/** The words along one of a chart's axes, in `into`: one view for each of `texts`, kept as the words change and made
  * or taken apart only as their number does, each placed by `place` (bound there to what the place follows). The
  * views' widths are what a chart lays its plot out round (`widest`). */
final class ChartLabels(into: Container, points: Double, place: (Int, TextView) => Unit) {
  private var made: Vector[TextView] = Vector.empty
  private val count: Var[Int] = into.own(Var(0))

  /** The views, in order. */
  def views: Vector[TextView] = made

  /** The words become `texts`. */
  def set(texts: Vector[String]): Unit = {
    while (made.size > texts.size) {
      val last = made.last
      made = made.dropRight(1)
      into.children -= last
      last.dispose()
    }
    texts.indices.foreach { i =>
      if (i < made.size) made(i).content @= texts(i)
      else {
        val view = Chart.words(TextView(texts(i), points, Theme.muted()))
        // a label is drawn whole, past its own box where it is turned, never cut short by it
        view.textOverflow @= TextOverflow.Visible
        made = made :+ view
        place(i, view)
        into.children += view
      }
    }
    count @= made.size
  }

  /** How wide the widest of them is, followed as they change. */
  def widest: Double = {
    count()
    made.foldLeft(0.0)((w, v) => math.max(w, v.size.width()))
  }

  /** Their widths, in order, followed as they change. */
  def widths: Vector[Double] = {
    count()
    made.map(_.size.width())
  }

  /** How tall a line of them is. */
  def lineHeight: Double = {
    count()
    made.headOption.map(_.size.height()).getOrElse(points * 1.4)
  }
}
