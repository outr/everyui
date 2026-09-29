package everyui.app

/** One row of the rows demo, as a dashboard hears of a pipeline's run: its number, its pipeline, how it stands, how
  * many records it has, when it started, and when it finished (None while it runs, when its duration ticks). */
case class RowRun(index: Int, pipeline: String, status: String, records: Int, started: Long, finished: Option[Long])

object RowRun {
  /** The `i`th row, as the demo starts it: with `ticking`, every seventh still running. */
  def apply(i: Int, now: Long, ticking: Boolean): RowRun = {
    val running = ticking && i % 7 == 0
    RowRun(i, "pipeline-" + (i % 17), if (running) "running" else if (i % 3 == 0) "failed" else "done", i * 37 % 1000,
      now - (i % 50) * 60000L, if (running) None else Some(now - (i % 50) * 30000L))
  }
}
