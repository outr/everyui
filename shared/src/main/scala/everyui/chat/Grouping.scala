package everyui.chat

/** How a transcript's entries run together: consecutive messages by the same author are one group, with the avatar and
  * name on the first alone, and a separator goes in wherever the day changes between one dated entry and the next. */
object Grouping {
  /** Whether `entry`, coming after `previous`, starts a group of its own: anything but a message by the same author
    * (by id: two people can share a name) straight after one of theirs. */
  def leads(previous: Option[ChatEntry], entry: ChatEntry): Boolean = (previous, entry) match {
    case (Some(p: ChatEntry.Message), m: ChatEntry.Message) => p.author.id != m.author.id
    case _ => true
  }

  /** `entries` with a separator before the first entry of each day: `dayOf` says which day an entry falls on (none for
    * one with no time, which takes no separator and changes no day), and `separator` makes the one for a day, which is
    * asked for again each time (keep them, so a day's separator stays the same entry). */
  def withDays(entries: Vector[ChatEntry], dayOf: ChatEntry => Option[String],
               separator: String => ChatEntry): Vector[ChatEntry] = {
    var out = Vector.empty[ChatEntry]
    var last: Option[String] = None
    entries.foreach { e =>
      val day = dayOf(e)
      if (day.nonEmpty && last != day) out = out :+ separator(day.get)
      if (day.nonEmpty) last = day
      out = out :+ e
    }
    out
  }

  /** The key a day's separator is known by: never an entry's own id, which the app makes. */
  def dayKey(day: String): String = "\u0000day:" + day

  /** Whether `entry` is a day's separator (`withDays`) rather than one of the app's. */
  def isDay(entry: ChatEntry): Boolean = entry.id.startsWith("\u0000day:")

  /** The key the transcript's row for somebody at work (`Transcript.working`) is known by: never an entry's own id. */
  def workKey(id: String): String = "\u0000work:" + id

  /** Whether `entry` is the transcript's row for somebody at work (`workKey`) rather than one of the app's. */
  def isWork(entry: ChatEntry): Boolean = entry.id.startsWith("\u0000work:")
}
