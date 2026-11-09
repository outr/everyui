package everyui.image

/** A `data:` address read, and bytes as words: what Android and the desktop read a picture written into its address
  * with (`SvgImage`). Kept apart from SvgImage, which a Roku and an Apple TV compile, as it reads bytes they have no
  * call for. */
object DataAddress {
  /** A `data:` address's media type and bytes (base64 or percent-encoded); None for any other address, or one that
    * cannot be read. */
  def apply(url: String): Option[(String, Array[Byte])] = {
    val comma = url.indexOf(',')
    if (!url.startsWith("data:") || comma < 0) None
    else {
      val head = url.substring(5, comma).split(';').toList.map(_.trim)
      val body = url.substring(comma + 1)
      try Some((head.headOption.getOrElse(""),
        if (head.contains("base64")) java.util.Base64.getMimeDecoder.decode(body) else percent(body)))
      catch { case _: IllegalArgumentException => None }
    }
  }

  /** Bytes as words, read as UTF-8. */
  def text(bytes: Array[Byte]): String = new String(bytes, "UTF-8")

  // %XX escapes as their bytes, everything else as its UTF-8
  private def percent(s: String): Array[Byte] = {
    val out = new java.io.ByteArrayOutputStream()
    val run = new StringBuilder
    def flush(): Unit = {
      val bytes = run.toString.getBytes("UTF-8")
      out.write(bytes, 0, bytes.length)
      run.clear()
    }
    var i = 0
    while (i < s.length) {
      val hex = if (s.charAt(i) == '%' && i + 2 < s.length) s.substring(i + 1, i + 3) else ""
      if (hex.length == 2 && hex.forall(h => Character.digit(h, 16) >= 0)) {
        flush()
        out.write(Integer.parseInt(hex, 16))
        i += 3
      } else {
        run.append(s.charAt(i))
        i += 1
      }
    }
    flush()
    out.toByteArray
  }
}
