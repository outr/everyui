package everyui.log

/** An address as a log line about a picture shows it: `scheme://host/path` alone, with "?…" after it where it had a
  * query or a fragment, and without a user or password; a `data:` address as its media type and its length, never
  * what it holds. A signed address (a stored file's, a portrait's) carries its signature, expiry and key in its query
  * under names of the server's choosing, which `Redact` cannot know, so none of the query is said.
  * {{{
  *   LoggedAddress("https://cdn.example.com/a/b.svg?sig=abc&exp=1#top")  // https://cdn.example.com/a/b.svg?…
  *   LoggedAddress("data:image/svg+xml;base64,PHN2Zz4=")                 // data:image/svg+xml (34 characters)
  * }}} */
object LoggedAddress {
  def apply(url: String): String =
    if (url == null) ""
    else if (url.startsWith("data:")) data(url)
    else {
      val cut = url.indexWhere(c => c == '?' || c == '#')
      val bare = if (cut < 0) url else url.substring(0, cut)
      val scheme = bare.indexOf("://")
      val start = scheme + 3
      val slash = if (scheme < 0) -1 else bare.indexOf('/', start)
      val authority = if (scheme < 0) "" else if (slash < 0) bare.substring(start) else bare.substring(start, slash)
      val at = authority.lastIndexOf('@')
      val shown = if (at < 0) bare
        else bare.substring(0, start) + authority.substring(at + 1) + (if (slash < 0) "" else bare.substring(slash))
      if (cut < 0) shown else shown + "?…"
    }

  // a data: address's media type, where it reads as one, and its length
  private def data(url: String): String = {
    val end = url.indexWhere(c => c == ';' || c == ',')
    val kind = if (end < 0) "" else url.substring(5, end)
    val typed = kind.length <= 64 &&
      kind.forall(c => c.isLetter || (c >= '0' && c <= '9') || c == '/' || c == '+' || c == '-' || c == '.')
    "data:" + (if (typed) kind else "") + " (" + url.length.toString + " characters)"
  }
}
