package everyui

import everyui.render.Platform

/** The piece of spice's `net` youi's screens use: a path, its `path"..."` interpolator, and a query lookup. */
object net {
  case class URLPath(value: String) {
    override def toString: String = value
  }

  /** The value of `name` in a path's query (`/runs?state=x`), decoded, if there. */
  def query(path: String, name: String): Option[String] = {
    val at = path.indexOf('?')
    if (at < 0) None
    else path.substring(at + 1).split('&').toList.find(_.startsWith(name + "=")).map(p => decode(p.substring(name.length + 1)))
  }

  private val hex = "0123456789abcdef"

  /** A query value as it was before it was encoded: `+` a space, and `%XX` the bytes of UTF-8. */
  def decode(value: String): String = {
    var out = ""
    var bytes: List[Int] = Nil
    var i = 0
    while (i < value.length) {
      val c = value.substring(i, i + 1)
      val high = if (c == "%" && i + 2 < value.length) hex.indexOf(value.substring(i + 1, i + 2).toLowerCase) else -1
      val low = if (high >= 0) hex.indexOf(value.substring(i + 2, i + 3).toLowerCase) else -1
      if (high >= 0 && low >= 0) {
        bytes = bytes :+ (high * 16 + low)
        i += 3
      }
      else {
        if (bytes.nonEmpty) {
          out = out + utf8(bytes)
          bytes = Nil
        }
        out = out + (if (c == "+") " " else c)
        i += 1
      }
    }
    if (bytes.nonEmpty) out = out + utf8(bytes)
    out
  }

  /** UTF-8 bytes as the characters they are; a broken sequence is dropped. */
  private def utf8(bytes: List[Int]): String = {
    var out = ""
    var rest = bytes
    while (rest.nonEmpty) {
      val b = rest.head
      val extra = if (b < 0x80) 0 else if (b >= 0xF0) 3 else if (b >= 0xE0) 2 else if (b >= 0xC0) 1 else -1
      if (extra < 0 || rest.size <= extra) rest = rest.tail
      else {
        var point = if (extra == 0) b else if (extra == 1) b - 0xC0 else if (extra == 2) b - 0xE0 else b - 0xF0
        var k = 1
        while (k <= extra) {
          point = point * 64 + (rest(k) - 0x80)
          k += 1
        }
        out = out + Platform.fromCodePoint(point)
        rest = rest.drop(extra + 1)
      }
    }
    out
  }

  implicit class URLPathInterpolator(sc: StringContext) {
    def path(args: Any*): URLPath = URLPath(sc.s(args*))
  }
}
