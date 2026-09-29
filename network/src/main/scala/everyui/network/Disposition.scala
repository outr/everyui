package everyui.network

/** A Content-Disposition header read (RFC 6266): `attachment; filename="report.csv"; filename*=UTF-8''r%C3%A9sum%C3%A9.csv`. */
object Disposition {
  /** The file name it gives: `filename*` (RFC 5987's charset'language'percent-encoding, UTF-8 or ISO-8859-1) where it
    * has one, else `filename`, quoted or not; without any path before it ("../x/a.csv" is "a.csv"). */
  def fileName(disposition: String): Option[String] = {
    val params = parameters(disposition)
    params.get("filename*").flatMap(extended).orElse(params.get("filename")).map(base).filter(_.nonEmpty)
  }

  /** Its parameters after the type, by their names in lower case (the first of each), quoted values unquoted. */
  def parameters(disposition: String): Map[String, String] = {
    var out = Map.empty[String, String]
    var at = disposition.indexOf(";")
    while (at >= 0) {
      val equals = disposition.indexOf("=", at + 1)
      if (equals < 0) at = -1
      else {
        val name = disposition.substring(at + 1, equals).trim.toLowerCase
        var i = equals + 1
        while (i < disposition.length && disposition.substring(i, i + 1) == " ") i += 1
        var value = ""
        if (i < disposition.length && disposition.substring(i, i + 1) == "\"") {
          i += 1
          var closed = false
          while (i < disposition.length && !closed) {
            val c = disposition.substring(i, i + 1)
            if (c == "\\" && i + 1 < disposition.length) {
              value = value + disposition.substring(i + 1, i + 2)
              i += 2
            }
            else if (c == "\"") {
              closed = true
              i += 1
            }
            else {
              value = value + c
              i += 1
            }
          }
          at = disposition.indexOf(";", i)
        }
        else {
          val semi = disposition.indexOf(";", i)
          value = (if (semi < 0) disposition.substring(i) else disposition.substring(i, semi)).trim
          at = semi
        }
        if (name.nonEmpty && !out.contains(name)) out = out + (name -> value)
      }
    }
    out
  }

  private val hex = "0123456789abcdef"

  /** `charset'language'value` decoded: its `%XX` escapes the bytes of the charset (UTF-8 or ISO-8859-1). */
  private def extended(value: String): Option[String] = {
    val first = value.indexOf("'")
    val second = if (first < 0) -1 else value.indexOf("'", first + 1)
    if (second < 0) None
    else {
      val charset = value.substring(0, first).trim.toLowerCase
      val encoded = value.substring(second + 1)
      var bytes: List[Int] = Nil
      var i = 0
      while (i < encoded.length) {
        val c = encoded.substring(i, i + 1)
        val high = if (c == "%" && i + 2 < encoded.length) hex.indexOf(encoded.substring(i + 1, i + 2).toLowerCase) else -1
        val low = if (high >= 0) hex.indexOf(encoded.substring(i + 2, i + 3).toLowerCase) else -1
        if (high >= 0 && low >= 0) {
          bytes = (high * 16 + low) :: bytes
          i += 3
        }
        else {
          c.getBytes("UTF-8").foreach(b => bytes = (b & 0xff) :: bytes)
          i += 1
        }
      }
      if (charset == "utf-8" || charset == "iso-8859-1") Some(new String(bytes.reverse.map(_.toByte).toArray, charset)) else None
    }
  }

  /** A name without the path a server may have put before it. */
  private def base(name: String): String = {
    val cut = math.max(name.lastIndexOf("/"), name.lastIndexOf("\\"))
    (if (cut >= 0) name.substring(cut + 1) else name).trim
  }
}
