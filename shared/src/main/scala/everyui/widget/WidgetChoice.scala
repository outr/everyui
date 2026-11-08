package everyui.widget

/** One of the app's choices a widget may draw from (a mail account, a calendar), offered in its configure screen
  * under its `group` ("Mail accounts", "Google: matt@…"). */
case class WidgetChoice(key: String, label: String, group: String = "")
