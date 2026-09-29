package everyui.app

/** One picture of a drawn busy indicator (the web's favicon): a ring filled `filled` steps of `BusyFrame.steps`
  * where the progress is known, or otherwise a spinner's arc turned `turn` steps of `BusyFrame.turns`. Equal frames
  * are the same picture, so a backend draws only when the frame changes.
  *
  * Angles are in turns, clockwise from twelve o'clock, for the backend to scale to its own units. */
case class BusyFrame(filled: Option[Int], turn: Int) {
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

  /** The picture `busy` is, `elapsed` seconds after the spinner started: None while idle. The spinner's frame comes
    * from the time alone, so a page that is woken once a second (a tab in the background) shows the frame due, and
    * never catches up on the ones it missed. */
  def at(busy: Option[Busy], elapsed: Double): Option[BusyFrame] = busy.map { b =>
    b.fraction match {
      case Some(f) => BusyFrame(Some((f * steps).toInt), 0)
      case None =>
        val shown = if (elapsed > 0.0) (elapsed * perSecond).toLong else 0L
        BusyFrame(None, (shown % turns).toInt)
    }
  }

  /** Whether `busy` moves by itself, and so needs a timer: an indeterminate busy. A progress only changes when the
    * app changes it. */
  def moving(busy: Option[Busy]): Boolean = busy.exists(_.fraction.isEmpty)
}
