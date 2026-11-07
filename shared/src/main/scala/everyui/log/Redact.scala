package everyui.log

/** Text with the secrets in its addresses masked, for a log: the value of each query parameter named as a secret
  * (`X-Plex-Token`, `token`, `access_token`, `api_key`, `signature`, `password` and the like) and the user and password
  * of an address that carries them ("https://user:pass@host"), each as "…". A media error said its stream's whole
  * address, a server's token with it, to every log the device keeps.
  * {{{
  *   Platform.log("media: " + Redact(url) + " failed")
  * }}} */
object Redact {
  /** The parameter names whose values are secrets, as their lower-case. */
  val secretNames: Set[String] = Set("x-plex-token", "token", "access_token", "refresh_token", "id_token", "api_key",
    "apikey", "key", "auth", "authorization", "signature", "sig", "password", "passwd", "secret", "client_secret")

  // what ends a parameter's value in a line of text
  private def ends(c: Char): Boolean = c == '&' || c == '#' || c == ' ' || c == '"' || c == '\'' || c == '\n' ||
    c == ')' || c == ']' || c == '>' || c == ','

  // the longest name among them: no further is looked for a parameter's "="
  private val longest = secretNames.map(_.length).max

  /** `text` with its secrets masked, in one pass over it: a line with none comes back as it was, nothing copied. (Each
    * line the app logs passes through here, on whatever thread logs it: one that looked to the end of the line for
    * each parameter's "=" took time and memory growing with the square of a long line's length.) */
  def apply(text: String): String =
    if (text == null) text
    else userInfo(if (text.indexOf('=') < 0) text else parameters(text))

  // each secret parameter's value masked
  private def parameters(text: String): String = {
    var out: StringBuilder = null
    var copied = 0
    var i = 0
    while (i < text.length) {
      val c = text.charAt(i)
      i += 1
      if (c == '?' || c == '&') {
        // the name runs to its "=", no further than the longest secret's, and holds nothing that ends a value
        var eq = i
        while (eq < text.length && eq - i <= longest && text.charAt(eq) != '=' && !ends(text.charAt(eq)) &&
          text.charAt(eq) != '?') eq += 1
        if (eq < text.length && eq > i && text.charAt(eq) == '=' &&
          secretNames.contains(text.substring(i, eq).toLowerCase)) {
          var end = eq + 1
          while (end < text.length && !ends(text.charAt(end))) end += 1
          if (out == null) out = new StringBuilder(text.length)
          out.append(text.substring(copied, eq + 1)).append('…')
          copied = end
          i = end
        }
      }
    }
    if (out == null) text
    else {
      out.append(text.substring(copied))
      out.toString
    }
  }

  // the user and password of an address that carries them masked
  private def userInfo(text: String): String = if (text.indexOf("://") < 0) text else {
    val out = new StringBuilder
    var from = 0
    var at = text.indexOf("://")
    while (at >= 0) {
      val start = at + 3
      var end = start
      while (end < text.length && text.charAt(end) != '/' && text.charAt(end) != '@' && !ends(text.charAt(end)))
        end += 1
      if (end < text.length && text.charAt(end) == '@' && end > start) {
        out.append(text.substring(from, start)).append('…')
        from = end
      }
      at = text.indexOf("://", start)
    }
    if (from == 0) text
    else {
      out.append(text.substring(from))
      out.toString
    }
  }
}
