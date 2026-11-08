package everyui.push

/** What a tap on an ongoing notice, or on one of its actions, does: open the app at a path (a deep link, answered as
  * a link is), or run the app's `BackgroundApp.onNoticeAction` with a name and args, leaving the app closed. */
enum NoticeTap {
  case OpenApp(path: String)
  case RunAction(name: String, args: Map[String, String] = Map.empty)
}
