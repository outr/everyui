package everyui.background

/** Work as `Work.once` and `Work.every` hand it to the platform: its key (unique: work scheduled with a key replaces
  * or keeps the one already scheduled with it), every how many minutes (0 for once), what must hold, whether it is
  * expedited, its backoff, its delay in seconds (once only), its data, and whether it replaces one already
  * scheduled. */
case class WorkRequest(key: String,
                       every: Int,
                       constraints: WorkConstraints,
                       expedited: Boolean,
                       backoff: WorkBackoff,
                       delay: Double,
                       data: Map[String, String],
                       replace: Boolean) {
  /** Whether it runs again and again, every `every` minutes. */
  def periodic: Boolean = every > 0

  /** What is wrong with it, each said; none where it can be scheduled. Expedited work runs as soon as it can or not
    * at all, so it cannot wait for charging, a battery not low, the device idle or a delay. */
  def problems: List[String] = {
    val named =
      if (key.isEmpty) List("work needs a key")
      else if (key.length > WorkRequest.longestKey)
        List("work \"" + key.take(20) + "…\": a key is " + WorkRequest.longestKey.toString + " characters at most")
      else Nil
    val waits = constraints.charging || constraints.batteryNotLow || constraints.idle || delay > 0.0
    val hurried =
      if (expedited && periodic) List("work \"" + key + "\": periodic work cannot be expedited")
      else if (expedited && waits)
        List("work \"" + key + "\": expedited work cannot wait for charging, the battery, the device idle or a delay")
      else Nil
    val late = if (delay < 0.0) List("work \"" + key + "\": a delay is not negative") else Nil
    val size = data.foldLeft(0)((n, kv) => n + kv._1.length + kv._2.length)
    val heavy =
      if (size > WorkRequest.mostData)
        List("work \"" + key + "\": its data is " + size.toString + " characters, more than " +
          WorkRequest.mostData.toString)
      else Nil
    named ++ hurried ++ late ++ heavy
  }
}

object WorkRequest {
  /** The fewest minutes between runs of periodic work: the platform's floor. */
  val leastMinutes: Int = 15
  /** The longest key. */
  val longestKey: Int = 100
  /** The most characters of data, keys and values together: the platform keeps it with the work. */
  val mostData: Int = 8192
}
