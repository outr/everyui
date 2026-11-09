package everyui.widget

/** One of a `WidgetSetting`'s options: its key, which the kind is handed (`WidgetRequest.settings`) and which is kept
  * for the widget, and its label in the configure screen ("Large", "Next 7 days"). */
case class WidgetSettingOption(key: String, label: String)
