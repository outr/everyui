package everyui.render

/** The host the page runs in as an installed app, if any: webOS (`WebOSTv`), told by its runtime's object or its user
  * agent. Asked once. Tizen's runtime and Vega's shell go beside it. */
object AppHosts {
  lazy val current: Option[AppHost] = if (WebOSTv.present) Some(WebOSTv) else None
}
