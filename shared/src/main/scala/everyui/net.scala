package everyui

/** The piece of spice's `net` youi's screens use: a path and its `path"..."` interpolator. */
object net:
  case class URLPath(value: String):
    override def toString: String = value

  implicit class URLPathInterpolator(sc: StringContext):
    def path(args: Any*): URLPath = URLPath(sc.s(args*))
