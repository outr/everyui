package everyui.render

/** A thread's `yyyy-MM-dd HH:mm:ss` (`Platform.localDateTime`): java.text rather than java.time, which is Android 8's,
  * and a format is not safe on two threads at once. Its zone is set again only as the zone changes. */
private[render] final class LocalFormat {
  private val format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
  private var zone: java.util.TimeZone = null

  def apply(millis: Long, now: java.util.TimeZone): String = {
    if (zone ne now) {
      zone = now
      format.setTimeZone(now)
    }
    format.format(new java.util.Date(millis))
  }
}
