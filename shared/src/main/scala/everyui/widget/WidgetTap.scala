package everyui.widget

/** What a tap on part of a widget does: open the app at a path (a deep link, which the app's screens answer as a link
  * is answered), or run the kind's `action` by name in the background (refresh, mark read), and draw it again. */
enum WidgetTap {
  case Open(path: String)
  case Action(name: String, args: Map[String, String] = Map.empty)
}
