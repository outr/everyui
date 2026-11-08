package everyui.widget

/** What a placed widget was set to in its configure screen, kept for it by its id until it is removed: its kind, the
  * choices it draws from, and whether it shows counts alone. */
case class WidgetSetup(kind: String, choices: Set[String], countsOnly: Boolean)
