package everyui.widget

/** What a tap on part of a widget does: open the app at a path (a deep link, which the app's screens answer as a link
  * is answered), or run the kind's `action` by name in the background (refresh, mark read), and draw it again. Either
  * works on any part of a list's row, as it does outside a list. */
enum WidgetTap {
  case Open(path: String)
  case Action(name: String, args: Map[String, String] = Map.empty)
}

object WidgetTap {
  /** Which a tap is, among its extras: "open" or "action". */
  val tapKey: String = "everyui.widget.tap"
  /** An open tap's path. */
  val pathKey: String = "path"
  /** An action's name. */
  val nameKey: String = "everyui.widget.tap.name"
  /** Before each of an action's args' names. */
  val argPrefix: String = "everyui.widget.tap.arg."

  /** `tap` as the words a list's row hands with its tap (Android's fill-in intent, whose extras are merged into the
    * list's one template): which it is, and its path, or its name and each of its args under a name of its own. */
  def extras(tap: WidgetTap): Map[String, String] = tap match {
    case Open(path) => Map(tapKey -> "open", pathKey -> path)
    case Action(name, args) => Map(tapKey -> "action", nameKey -> name) ++ args.map((k, v) => (argPrefix + k) -> v)
  }

  /** The tap `extras` hold, among whatever else is with them (the template's own); none where they hold no tap, or
    * one without its path or its name. */
  def of(extras: Map[String, String]): Option[WidgetTap] = extras.get(tapKey) match {
    case Some("open") => extras.get(pathKey).map(Open(_))
    case Some("action") => extras.get(nameKey).filter(_.nonEmpty).map { name =>
      Action(name, extras.collect { case (k, v) if k.startsWith(argPrefix) => k.substring(argPrefix.length) -> v })
    }
    case _ => None
  }
}
