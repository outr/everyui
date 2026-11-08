package everyui.sbt

/** One kind of home-screen widget as Android's launcher knows it (`everyuiWidgetKinds`): its key, the name and
  * description its widget picker shows, the size classes it is drawn at ("compact", "medium", "large"), the picture
  * the picker shows (a resource, "@drawable/inbox_preview"; the app's icon where there is none), how often it is drawn
  * again with nothing asking (minutes, 30 at least, 0 for never), and whether it has a configure screen.
  *
  * The key, name, description and sizes are the app's `WidgetKind`'s as well: the build reads the kinds of the object
  * `everyuiWidgets` names and fails where the two differ (AndroidWidgets.mismatches). The sizes give the launcher's
  * least and most sizes and the cells it is placed at (AndroidWidgets.provider). */
case class AndroidWidget(key: String,
                         label: String,
                         sizes: Seq[String],
                         description: String = "",
                         preview: String = "",
                         updateMinutes: Int = 30,
                         configurable: Boolean = true)
