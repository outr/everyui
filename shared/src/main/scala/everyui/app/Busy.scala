package everyui.app

/** What the app is busy with (`AppBusy.state`): how far along it is, from 0 to 1, where it knows (`progress`), and
  * what the work is called, where it has a name (`label`). Neither is an indeterminate busy: working, with no end in
  * sight.
  *
  * {{{
  *   AppBusy.state @= Some(Busy())                              // working
  *   AppBusy.state @= Some(Busy(Some(0.42), Some("deliver")))   // 42% through "deliver"
  *   AppBusy.state @= None                                      // idle
  * }}} */
case class Busy(progress: Option[Double] = None, label: Option[String] = None) {
  /** The progress held to 0..1, or None where there is none (or it is not a number). */
  def fraction: Option[Double] = progress.filter(p => p == p).map(p => if (p < 0.0) 0.0 else if (p > 1.0) 1.0 else p)

  /** The progress in whole percent, rounded down so that 100 means done. */
  def percent: Option[Int] = fraction.map(f => (f * 100.0).toInt)

  /** The label, where it says anything. */
  def name: Option[String] = label.filter(_.trim.nonEmpty)

  /** A few words for a title: "42% deliver", "42%", "deliver", or "" for an indeterminate busy with no name. */
  def summary: String = (percent.map(p => p.toString + "%").toList ++ name.toList).mkString(" ")
}

object Busy {
  /** Working, with no progress and no name. */
  val indeterminate: Busy = Busy()

  /** What a title starts with while `busy` (`AppBusy.inTitle`): "(42% deliver) ", "(…) " without a summary, and
    * nothing while idle. */
  def titlePrefix(busy: Option[Busy]): String = busy match {
    case Some(b) => if (b.summary.isEmpty) "(…) " else "(" + b.summary + ") "
    case None => ""
  }

  /** `title` without the `prefix` shown before it, where it still starts with it: the app's own title, which it may
    * have changed since. */
  def untitled(title: String, prefix: String): String =
    if (prefix.nonEmpty && title.startsWith(prefix)) title.substring(prefix.length) else title
}
