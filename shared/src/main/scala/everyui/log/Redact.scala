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

  def apply(text: String): String = if (text == null) text else userInfo(parameters(text))

  // each secret parameter's value masked
  private def parameters(text: String): String = {
    val lower = text.toLowerCase
    val out = new StringBuilder
    var i = 0
    while (i < text.length) {
      val c = text.charAt(i)
      out.append(c)
      i += 1
      if (c == '?' || c == '&') {
        val eq = lower.indexOf('=', i)
        val name = if (eq > i) lower.substring(i, eq) else ""
        if (name.nonEmpty && !name.exists(ends) && secretNames.contains(name)) {
          out.append(text.substring(i, eq + 1)).append('…')
          i = eq + 1
          while (i < text.length && !ends(text.charAt(i))) i += 1
        }
      }
    }
    out.toString
  }

  // the user and password of an address that carries them masked
  private def userInfo(text: String): String = {
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
    out.append(text.substring(from))
    out.toString
  }
}
