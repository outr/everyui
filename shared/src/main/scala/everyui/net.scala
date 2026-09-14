package everyui

/** The piece of spice's `net` youi's screens use: a path, its `path"..."` interpolator, and a query lookup. */
object net:
  case class URLPath(value: String):
    override def toString: String = value

  /** The value of `name` in a path's query (`/runs?state=x`), if there. */
  def query(path: String, name: String): Option[String] =
    val at = path.indexOf('?')
    if at < 0 then None
    else path.drop(at + 1).split('&').toList.map(_.split("=", 2)).collectFirst { case Array(k, v) if k == name => v }

  implicit class URLPathInterpolator(sc: StringContext):
    def path(args: Any*): URLPath = URLPath(sc.s(args*))
