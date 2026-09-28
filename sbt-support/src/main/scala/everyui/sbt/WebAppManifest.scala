package everyui.sbt

/** What a browser needs to offer a web app for install, and to run it in a window of its own: the manifest's fields. */
case class WebAppManifest(name: String,
                          shortName: String = "",
                          description: String = "",
                          icons: Seq[WebAppIcon] = Nil,
                          themeColor: String = "#121212",
                          backgroundColor: String = "#121212",
                          display: String = "standalone",
                          startUrl: String = "./",
                          scope: String = "./",
                          id: String = "./") {
  /** The manifest as JSON. */
  def json: String = {
    def q(s: String): String = "\"" + s.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case c => c.toString
    } + "\""
    val iconList = icons.map(i => s"""    {"src": ${q(i.src)}, "sizes": ${q(i.sizes)}, "type": ${q(i.mime)}, "purpose": ${q(i.purpose)}}""")
    val fields = Seq(
      "id" -> q(id), "name" -> q(name), "short_name" -> q(if (shortName.isEmpty) name else shortName),
      "description" -> q(description), "start_url" -> q(startUrl), "scope" -> q(scope), "display" -> q(display),
      "theme_color" -> q(themeColor), "background_color" -> q(backgroundColor),
      "icons" -> iconList.mkString("[\n", ",\n", "\n  ]"))
    fields.map { case (k, v) => "  " + q(k) + ": " + v }.mkString("{\n", ",\n", "\n}\n")
  }
}
