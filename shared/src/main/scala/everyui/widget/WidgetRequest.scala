package everyui.widget

/** What one placed widget asks for each time it is drawn: its id, its kind's key, its size, the choices it was set to
  * draw from, whether it shows counts alone (no subjects or names), and whether the device is in its dark mode.
  * `width` and `height` are the room the launcher gives it, in points, the least it may be shown at in its size (0
  * where the launcher has not said), less the ground's own margin (`WidgetRequest.margin`): a kind leaves out what
  * would not fit there (a caption under a count) rather than have it cut short. `settings` has the option of each of
  * the kind's settings (`WidgetKind.settings`) by its key: the one chosen in the configure screen where it is still
  * offered, the setting's default otherwise (`WidgetSetup.settled`). */
case class WidgetRequest(id: Int, kind: String, size: WidgetSize, choices: Set[String], countsOnly: Boolean,
                         dark: Boolean, width: Double = 0.0, height: Double = 0.0,
                         settings: Map[String, String] = Map.empty)

object WidgetRequest {
  /** How far in from the widget's edge its content starts, on each side, in points. */
  val margin: Double = 12.0
}
