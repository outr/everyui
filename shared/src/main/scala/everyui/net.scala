package everyui

/** The piece of spice's `net` youi's screens use: a path, its `path"..."` interpolator, and a query lookup. */
object net {
  case class URLPath(value: String) {
    override def toString: String = value
  }

  /** The value of `name` in a path's query (`/runs?state=x`), if there. */
  def query(path: String, name: String): Option[String] = {
    val at = path.indexOf('?')
    if (at < 0) None
    else path.substring(at + 1).split('&').toList.find(_.startsWith(name + "=")).map(_.substring(name.length + 1))
  }

  implicit class URLPathInterpolator(sc: StringContext) {
    def path(args: Any*): URLPath = URLPath(sc.s(args*))
  }
}
