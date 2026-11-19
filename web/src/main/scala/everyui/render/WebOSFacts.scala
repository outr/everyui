package everyui.render

/** What an LG TV's web runtime says of itself, read as plain Scala (so the desktop's specs read it as the TV does):
  * whether the page is on one, which webOS it is, what its remote's keys are, and how the app leaves. `WebOSTv` asks
  * the page and hands the answers here. */
object WebOSFacts {
  /** On webOS: the runtime's own object (`webOSSystem` from webOS 5, `PalmSystem` before it and still beside it), or
    * else the user agent's "Web0S" (with a zero), which every LG TV's runtime and browser send. */
  def detect(webOSSystem: Boolean, palmSystem: Boolean, agent: String): Boolean =
    webOSSystem || palmSystem || agent.contains("Web0S") || agent.contains("webOS.TV")

  /** The remote's keys by their key code, named as the web names them (and `everyui.event.Keys` reads them): Back is
    * 461, the coloured keys 403 to 406, the transport keys of the standard remote (Play 415, Pause 19, Stop 413,
    * Rewind 412, Fast-forward 417), and channel up and down (33, 34, Page Up and Down as on every TV browser). The
    * engine's own `key` for these is not the same across webOS versions ("GoBack", "Unidentified"), so they are named
    * by their code here. */
  val keys: Map[Int, String] = Map(
    461 -> "GoBack",
    403 -> "ColorF0Red", 404 -> "ColorF1Green", 405 -> "ColorF2Yellow", 406 -> "ColorF3Blue",
    415 -> "MediaPlay", 19 -> "MediaPause", 413 -> "MediaStop", 412 -> "MediaRewind", 417 -> "MediaFastForward",
    33 -> "PageUp", 34 -> "PageDown"
  )

  /** The Magic Remote's pointer shown and hidden, as webOS 1 says it with key codes rather than `cursorStateChange`. */
  val CursorShown: Int = 1536
  val CursorHidden: Int = 1537

  /** LG's webOS TV versions by the Chromium each ships (LG's "Web API and Web Engine" table): 3 to 6, then the years. */
  val byChrome: List[(Int, String)] =
    List(38 -> "3", 53 -> "4", 68 -> "5", 79 -> "6", 87 -> "22", 94 -> "23", 108 -> "24", 120 -> "25")

  /** The webOS TV version as LG names it ("4", "6", "22", "25"), "" where it cannot be told. The user agent's Chrome is
    * LG's own table for it; a release the table does not have yet is read from the runtime's `deviceInfo` (a JSON
    * object with `platformVersionMajor`: 7 is webOS 22, and each year one more), else said as the nearest release before
    * it with a "+" ("25+"). webOS 1 and 2 have no Chrome in theirs ("Large Screen", WebKit 537 and 538). */
  def version(agent: String, deviceInfo: String): String = {
    val chrome = "Chrome/(\\d+)".r.findFirstMatchIn(agent).map(_.group(1).toInt)
    val major = LaunchParams.parse(deviceInfo).get("platformVersionMajor").flatMap(_.toIntOption)
    chrome.flatMap(c => byChrome.find(_._1 == c).map(_._2)).orElse(major.map(m => if (m >= 7) (m + 15).toString else m.toString))
      .orElse(chrome.flatMap(c => byChrome.filter(_._1 < c).lastOption.map(_._2 + "+")))
      .orElse(if (agent.contains("AppleWebKit/538")) Some("2") else if (agent.contains("AppleWebKit/537.41")) Some("1") else None)
      .getOrElse("")
  }

  /** How the app leaves at the root, for the first of the ways the page has: webOSTV.js's `webOS.platformBack()` (an
    * exit prompt on webOS 6 and later, the launcher before), the runtime's own `webOSSystem.platformBack()` or
    * `PalmSystem.platformBack()` (what webOSTV.js calls), else `window.close()`. Which was used. */
  def exit(webOS: Option[() => Unit], webOSSystem: Option[() => Unit], palmSystem: Option[() => Unit],
           close: () => Unit): String =
    webOS.map { f => f(); "webOS" }
      .orElse(webOSSystem.map { f => f(); "webOSSystem" })
      .orElse(palmSystem.map { f => f(); "PalmSystem" })
      .getOrElse { close(); "close" }
}
