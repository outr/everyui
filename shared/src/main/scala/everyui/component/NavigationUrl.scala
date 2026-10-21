package everyui.component

/** URLs that may leave text and enter a platform navigation API. Custom handlers can opt into other schemes. */
object NavigationUrl {
  val schemes: Set[String] = Set("http", "https", "mailto", "tel", "sms", "geo", "google.navigation", "market")

  def safe(raw: String): Boolean = {
    if (raw.isEmpty || raw.exists(c => c <= ' ' && c != ' ' || c == '\u007f')) false
    else {
      val url = raw.trim
      val colon = url.indexOf(':')
      val boundary = List(url.indexOf('/'), url.indexOf('?'), url.indexOf('#')).filter(_ >= 0).minOption.getOrElse(Int.MaxValue)
      url.nonEmpty && (colon < 0 || colon > boundary || schemes(url.substring(0, colon).toLowerCase))
    }
  }
}
