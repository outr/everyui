package everyui.render

/** What a TV's browser engine says of how much it can do, read from its user agent as plain Scala (so the desktop's
  * specs read it as the TV does). */
object TvEngine {
  /** The newest Chromium of a TV taken to be a slow one: webOS 4 (53) and 3 (38), Tizen 4 (56) and 3 (47), and the
    * 1 to 1.5 GB of memory they came with. webOS 5's 68 is not. */
  val slowChromium: Int = 67

  /** The Chromium in `agent` ("Chrome/53.0.2785.34" is 53), or 0 where it names none. */
  def chromium(agent: String): Int =
    "Chrome/(\\d+)".r.findFirstMatchIn(agent).map(_.group(1).toInt).getOrElse(0)

  /** Whether a page `remote` driven (a TV's: `Platform.remoteNavigation`) with user agent `agent` runs on a slow TV:
    * a Chromium of `slowChromium` or older, or an engine with no Chromium at all (webOS 1 and 2's WebKit). */
  def slow(remote: Boolean, agent: String): Boolean = remote && chromium(agent) <= slowChromium
}
