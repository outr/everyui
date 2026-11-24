package everyui.sbt

/** What an app's build says of its use of the location (`everyuiLocation`), for what sbt-everyui writes from it: on
  * Android the manifest's permissions, precise and approximate (`everyui.location.Location`), and with `background`
  * the one with the app closed (`Location.permitBackground`, `Visits`), with the location hardware not required, so
  * Google Play offers the app to a device without it; on an iPhone the Info.plist's reasons, `purpose` in the words
  * iOS shows in its prompts (none written where it is empty, and iOS then refuses the location without asking). */
case class LocationUse(background: Boolean = false, purpose: String = "")

object LocationUse {
  val fine: String = "android.permission.ACCESS_FINE_LOCATION"
  val coarse: String = "android.permission.ACCESS_COARSE_LOCATION"
  val background: String = "android.permission.ACCESS_BACKGROUND_LOCATION"
  /** Declared not required: a permission for a precise location otherwise makes Google Play take GPS as required. */
  val features: Seq[String] = Seq("android.hardware.location", "android.hardware.location.gps")

  /** The Android permissions `use` needs. */
  def permissions(use: LocationUse): Seq[String] = Seq(coarse, fine) ++ (if (use.background) Seq(background) else Nil)

  /** `manifest` with the permissions and features it does not declare already put before its application, each on a
    * line of its own at the application's indent. It must have one. */
  def manifest(manifest: String, use: LocationUse): Either[String, String] = {
    val start = manifest.indexOf("<application")
    if (start < 0) Left("the app's AndroidManifest.xml has no application to put the location's permissions before")
    else {
      val line = manifest.lastIndexOf('\n', start) + 1
      val at = if (manifest.substring(line, start).trim.isEmpty) line else start
      val indent = manifest.substring(at, start).takeWhile(_ == ' ')
      val declared = (name: String) => manifest.contains("\"" + name + "\"")
      val uses = permissions(use).filterNot(declared).map(p => s"""$indent<uses-permission android:name="$p" />\n""") ++
        features.filterNot(declared).map(f =>
          s"""$indent<uses-feature android:name="$f" android:required="false" />\n""")
      Right(manifest.substring(0, at) + uses.mkString + manifest.substring(at))
    }
  }

  /** The iOS Info.plist's reasons for the location, `use.purpose` for each the app does not give already: while in
    * use, and with `background` always. */
  def infoPlist(plist: String, use: LocationUse): String =
    if (use.purpose.isEmpty) plist
    else {
      val keys = Seq("NSLocationWhenInUseUsageDescription") ++
        (if (use.background) Seq("NSLocationAlwaysAndWhenInUseUsageDescription") else Nil)
      val text = use.purpose.flatMap {
        case '&' => "&amp;"
        case '<' => "&lt;"
        case '>' => "&gt;"
        case c => c.toString
      }
      val missing = keys.filterNot(k => plist.contains("<key>" + k + "</key>"))
      val end = plist.lastIndexOf("</dict>")
      if (missing.isEmpty || end < 0) plist
      else plist.substring(0, end) + missing.map(k => s"  <key>$k</key><string>$text</string>\n").mkString +
        plist.substring(end)
    }
}
