package everyui.app

/** One picture of a drawn busy indicator (the web's favicon): a ring filled `filled` steps of `BusyFrame.steps`
  * where the progress is known, or otherwise a spinner's arc turned `turn` steps of `BusyFrame.turns`. A progress that
  * has stood still for a while (`AppBusy.stillAfter`) also has a small arc going round the ring, turned `orbit` steps.
  * Equal frames are the same picture, so a backend draws only when the frame changes.
  *
  * Angles are in turns, clockwise from twelve o'clock, for the backend to scale to its own units. */
case class BusyFrame(filled: Option[Int], turn: Int, orbit: Option[Int] = None) {
  /** Where the arc starts. */
  def start: Double = filled match {
    case Some(_) => 0.0
    case None => turn.toDouble / BusyFrame.turns
  }

  /** How far the arc runs. */
  def sweep: Double = filled match {
    case Some(steps) => steps.toDouble / BusyFrame.steps
    case None => BusyFrame.spinnerSweep
  }

  /** The small arc going round a progress that stands still, as the pieces to draw: each piece's start and sweep,
    * and whether it lies over the filled part of the ring (to be drawn so it shows against the fill) or over the rest.
    * Nothing where there is no such arc. */
  def orbitPieces: List[(Double, Double, Boolean)] = orbit.toList.flatMap { o =>
    val from = o.toDouble / BusyFrame.turns
    val to = from + BusyFrame.orbitSweep
    // the arc past twelve o'clock is a second span from 0
    val spans = if (to > 1.0) List((from, 1.0), (0.0, to - 1.0)) else List((from, to))
    val fill = sweep
    spans.flatMap { span =>
      List((span._1, math.min(span._2, fill), true), (math.max(span._1, fill), span._2, false))
        .filter(p => p._2 > p._1).map(p => (p._1, p._2 - p._1, p._3))
    }
  }
}

object BusyFrame {
  /** How finely a progress is drawn: 60 steps, 6 degrees each, under a pixel of a 32-pixel ring's edge. */
  val steps: Int = 60
  /** How many pictures make one turn of the spinner. */
  val turns: Int = 12
  /** How many pictures a second the spinner shows while it is watched (a turn in 1.5s). */
  val perSecond: Double = 8.0
  /** How much of the ring the spinner's arc covers. */
  val spinnerSweep: Double = 0.3
  /** How much of the ring the arc going round a progress that stands still covers. */
  val orbitSweep: Double = 0.12

  /** The steps of the ring `busy` fills, or None where it has no progress (or is idle): what a backend watches to
    * know when the progress last moved. */
  def fill(busy: Option[Busy]): Option[Int] = busy.flatMap(_.fraction).map(f => (f * steps).toInt)

  /** The picture `busy` is, `elapsed` seconds after the spinner started: None while idle. The spinner's frame comes
    * from the time alone, so a page that is woken once a second (a tab in the background) shows the frame due, and
    * never catches up on the ones it missed. A progress whose fill has stood still for `still` seconds, `stillAfter`
    * or more, has the small arc going round it too, turned by the time since it began. */
  def at(busy: Option[Busy], elapsed: Double, still: Double = 0.0, stillAfter: Option[Double] = None): Option[BusyFrame] =
    busy.map { b =>
      fill(busy) match {
        case Some(f) => BusyFrame(Some(f), 0, stillAfter.filter(a => still >= a).map(a => turned(still - a)))
        case None => BusyFrame(None, turned(elapsed))
      }
    }

  /** Whether `busy` moves by itself, and so needs a timer: an indeterminate busy, or a progress that may come to
    * stand still (`stillAfter`). Otherwise a progress only changes when the app changes it. */
  def moving(busy: Option[Busy], stillAfter: Option[Double] = None): Boolean =
    busy.exists(b => b.fraction.isEmpty || stillAfter.nonEmpty)

  /** The step of a turn `seconds` in, at `perSecond`. */
  private def turned(seconds: Double): Int = ((if (seconds > 0.0) (seconds * perSecond).toLong else 0L) % turns).toInt
}
