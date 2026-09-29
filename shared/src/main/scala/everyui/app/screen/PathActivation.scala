package everyui.app.screen

import everyui.net.{URLPath, decode, query}
import everyui.render.Platform

/** youi's `PathActivation`: the path a screen is reached at, and shown at. A screen with parameters declares a
  * `pattern` (`/run/:id`), claims every path shaped like it, and reads `parameter("id")` when activated — a
  * `?name=value` in the query answers too. A path is matched without its query or its `#fragment`. */
trait PathActivation {
  def path: URLPath
  def pattern: Option[String] = None

  def matches(path: URLPath): Boolean = pattern match {
    case Some(p) =>
      val want = segments(p)
      val have = segments(path.value)
      var same = want.length == have.length
      var i = 0
      while (same && i < want.length) {
        same = want(i).startsWith(":") || want(i) == have(i)
        i += 1
      }
      same
    case None => bare(path.value) == bare(this.path.value)
  }

  /** `name` from the page's path by the pattern, or from its query. */
  def parameter(name: String): Option[String] = {
    val at = Platform.path()
    pattern.flatMap { p =>
      val want = segments(p)
      val have = segments(at)
      val i = want.indexOf(":" + name)
      // a + in a path is itself, not the space it stands for in a query
      if (i >= 0 && i < have.length) Some(decode(have(i).replace("+", "%2B"))) else None
    }.orElse(query(at, name))
  }

  /** The path's segments, without its query or fragment. */
  private def segments(path: String): List[String] = bare(path).split('/').toList

  /** `path` up to its query or its fragment, whichever comes first. */
  private def bare(path: String): String = {
    val q = path.indexOf('?')
    val h = path.indexOf('#')
    val end = if (q < 0) h else if (h < 0) q else math.min(q, h)
    if (end < 0) path else path.substring(0, end)
  }
}
