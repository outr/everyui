package everyui.sbt

import java.io.File

/** An EveryUI web app packaged for LG's webOS TVs (`everyuiWebOS`, packaged by `webosPackage`): its appinfo.json and
  * its pictures. The app's web bundle runs inside the package from its own files, served by no one.
  *
  * {{{
  *   everyuiWebOS := Some(WebOSApp(id = "com.example.app", version = "1.2.0", vendor = "Example",
  *     title = "Example", icon = baseDirectory.value / "webos" / "icon.png"))
  * }}}
  *
  * @param id             lowercase letters, digits, "-" and ".", reverse DNS, at least two characters, never under
  *                       com.palm, com.webos, com.lge or com.palmdts; it cannot change once published.
  * @param version        three numbers ("2.1.0"), none with a leading zero; each upload to LG's store a higher one.
  * @param icon           the icon, a PNG: written 80 by 80 (`icon.png`), as LG asks, whatever its size.
  * @param largeIcon      the large icon, 130 by 130 (`largeIcon.png`); the icon scaled where there is none.
  * @param splash         the picture shown while the app loads (`splashBackground`), written at `resolution`.
  * @param resolution     "1920x1080" (the default) or "1280x720": what the page is laid out at (webOS has no 4K for
  *                       a web app).
  * @param iconColor      the colour of the app's tile in the launcher (LG's default is white).
  * @param requiredMemory the least memory the app starts in, in megabytes.
  * @param description    LG's `appDescription`, a tagline of up to 60 characters.
  * @param requiredACG    the Luna service groups the app calls (none for an app that calls none, which LG's newer
  *                       store checks want declared, as an empty list; `WebOSApp.appsACG` for `everyui.apps.Apps`).
  * @param extra          any other appinfo.json field, as its JSON text (`"transparent" -> "true"`). */
case class WebOSApp(id: String, version: String, vendor: String, title: String, icon: File,
                    largeIcon: Option[File] = None, splash: Option[File] = None, resolution: String = "1920x1080",
                    iconColor: Option[String] = None, requiredMemory: Option[Int] = None,
                    description: Option[String] = None, requiredACG: Seq[String] = Nil,
                    extra: Seq[(String, String)] = Nil) {
  /** What LG's packager and store would refuse in this, each said; none where it is right. */
  def problems: List[String] = List(
    if (WebOSApp.idPattern.matches(id) && !WebOSApp.reserved.exists(id.startsWith)) None
    else Some(s"id \"$id\": lowercase letters, digits, - and ., starting with a letter or a digit, at least two, " +
      "not under com.palm, com.webos, com.lge or com.palmdts"),
    if (WebOSApp.versionPattern.matches(version)) None
    else Some(s"version \"$version\": three numbers, none with a leading zero, of up to nine digits (\"1.2.0\")"),
    if (title.trim.nonEmpty) None else Some("title is empty"),
    if (resolution == "1920x1080" || resolution == "1280x720") None
    else Some(s"resolution \"$resolution\": \"1920x1080\" or \"1280x720\""),
    if (description.forall(_.length <= 60)) None else Some("description: over 60 characters"),
    if (icon.isFile) None else Some(s"icon $icon is missing"),
    largeIcon.filterNot(_.isFile).map(f => s"largeIcon $f is missing"),
    splash.filterNot(_.isFile).map(f => s"splash $f is missing")
  ).flatten

  /** The appinfo.json the package carries. Back is the app's (`disableBackHistoryAPI`), which EveryUI answers and
    * leaves the app by at its root. */
  def appinfo: String = {
    val fields = List(
      "id" -> WebOSApp.quote(id),
      "version" -> WebOSApp.quote(version),
      "vendor" -> WebOSApp.quote(vendor),
      "type" -> WebOSApp.quote("web"),
      "main" -> WebOSApp.quote("index.html"),
      "title" -> WebOSApp.quote(title),
      "icon" -> WebOSApp.quote("icon.png"),
      "largeIcon" -> WebOSApp.quote("largeIcon.png"),
      "resolution" -> WebOSApp.quote(resolution),
      "disableBackHistoryAPI" -> "true",
      "requiredACG" -> requiredACG.map(WebOSApp.quote).mkString("[", ", ", "]")
    ) ++ splash.map(_ => "splashBackground" -> WebOSApp.quote("splash.png")) ++
      iconColor.map(c => "iconColor" -> WebOSApp.quote(c)) ++
      requiredMemory.map(m => "requiredMemory" -> m.toString) ++
      description.map(d => "appDescription" -> WebOSApp.quote(d)) ++
      extra.filterNot(e => WebOSApp.written(e._1))
    fields.map { case (k, v) => "  " + WebOSApp.quote(k) + ": " + v }.mkString("{\n", ",\n", "\n}\n")
  }

  /** The width and the height of `resolution`. */
  def size: (Int, Int) = {
    val parts = resolution.split('x')
    (parts(0).toInt, parts(1).toInt)
  }
}

object WebOSApp {
  val idPattern: scala.util.matching.Regex = "[a-z0-9][a-z0-9.-]+".r
  val versionPattern: scala.util.matching.Regex = "(0|[1-9]\\d{0,8})\\.(0|[1-9]\\d{0,8})\\.(0|[1-9]\\d{0,8})".r
  val reserved: List[String] = List("com.palm", "com.webos", "com.lge", "com.palmdts")

  /** The groups `everyui.apps.Apps` calls in: the Application Manager's `launch` (`application.launcher`) and
    * `getAppLoadStatus` (`application.query`), for `requiredACG = WebOSApp.appsACG`. */
  val appsACG: Seq[String] = Seq("application.launcher", "application.query")

  // the fields written from the parameters, which `extra` does not write again
  private val written: Set[String] = Set("id", "version", "vendor", "type", "main", "title", "icon", "largeIcon",
    "resolution", "disableBackHistoryAPI", "requiredACG", "splashBackground", "iconColor", "requiredMemory",
    "appDescription")

  /** `s` as a JSON string. */
  def quote(s: String): String = {
    val out = new StringBuilder("\"")
    s.foreach {
      case '"' => out.append("\\\"")
      case '\\' => out.append("\\\\")
      case '\n' => out.append("\\n")
      case '\r' => out.append("\\r")
      case '\t' => out.append("\\t")
      case c if c < ' ' => out.append(f"\\u${c.toInt}%04x")
      case c => out.append(c)
    }
    out.append('"').toString
  }
}
