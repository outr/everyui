package everyui.date

/** Six hours of the device's clock (`LocalClock`), the `index`th since the epoch: its offset from UTC is `before` until
  * the moment `change` and `after` from it (`change` past the span's end where the clock does not change in it). An
  * `exact` span is one the platform is asked about at each moment: its clocks change in a way these cannot say. */
case class LocalSpan(index: Long, before: Long, change: Long, after: Long, exact: Boolean) {
  def offset(millis: Long): Long = if (millis < change) before else after
}
