package everyui.widget

/** A choice of one among a few that a kind offers in its configure screen, beside what it draws from: its text size,
  * how far ahead a calendar looks. `key` names it in `WidgetRequest.settings`; `label` heads its options in the
  * configure screen, with `note` under them where it has one; `default` is the option's key a widget has where none
  * was chosen (placed with its configure screen passed by, kept before the kind offered the setting, or set to an
  * option no longer offered). */
case class WidgetSetting(key: String,
                         label: String,
                         options: Vector[WidgetSettingOption],
                         default: String,
                         note: String = "") {
  /** The option keyed `chosen` where it is still offered, and the default otherwise. */
  def resolve(chosen: Option[String]): String = chosen.filter(c => options.exists(_.key == c)).getOrElse(default)
}
