package everyui.render

/** The alternate renditions of an HLS master playlist (`#EXT-X-MEDIA`), read as plain Scala (so the desktop's specs read
  * them as the player does), for a TV's own player that plays them but does not list them (`WebMedia`): the sound
  * tracks and subtitles of the first group of each kind a variant names, in the playlist's order, which is the order
  * hls.js lists them in, so the same index chooses the same one in either. */
object HlsRenditions {
  /** Every `#EXT-X-MEDIA` of `playlist`, in its order. */
  def all(playlist: String): List[HlsRendition] =
    playlist.split("\n").toList.map(_.trim).filter(_.startsWith("#EXT-X-MEDIA:")).map { line =>
      val a = attributes(line.substring("#EXT-X-MEDIA:".length))
      HlsRendition(a.getOrElse("TYPE", ""), a.getOrElse("GROUP-ID", ""), a.getOrElse("NAME", ""),
        a.getOrElse("LANGUAGE", ""), a.get("DEFAULT").contains("YES"), a.getOrElse("URI", ""))
    }

  /** The renditions of `kind` ("AUDIO", "SUBTITLES") a player chooses among: the group the first variant names for it
    * (its `AUDIO=` or `SUBTITLES=`), else the first group of that kind. */
  def of(playlist: String, kind: String): List[HlsRendition] = {
    val renditions = all(playlist).filter(_.kind == kind)
    val named = playlist.split("\n").toList.map(_.trim).find(_.startsWith("#EXT-X-STREAM-INF:"))
      .flatMap(l => attributes(l.substring("#EXT-X-STREAM-INF:".length)).get(kind))
    val group = named.filter(g => renditions.exists(_.group == g)).orElse(renditions.headOption.map(_.group))
    renditions.filter(r => group.contains(r.group))
  }

  /** Whether `text` is a master playlist (it names variants or renditions), rather than a media playlist. */
  def master(text: String): Boolean = text.contains("#EXT-X-STREAM-INF") || text.contains("#EXT-X-MEDIA:")

  // an attribute list, NAME=value or NAME="quoted, value", by name
  private def attributes(list: String): Map[String, String] = {
    var found = Map.empty[String, String]
    var at = 0
    while (at < list.length) {
      val eq = list.indexOf('=', at)
      if (eq < 0) at = list.length
      else {
        val name = list.substring(at, eq).trim
        var end = eq + 1
        val value =
          if (end < list.length && list.charAt(end) == '"') {
            val close = list.indexOf('"', end + 1)
            val stop = if (close < 0) list.length else close
            end = stop + 1
            list.substring(eq + 2, stop)
          }
          else {
            val comma = list.indexOf(',', end)
            val stop = if (comma < 0) list.length else comma
            end = stop
            list.substring(eq + 1, stop).trim
          }
        found = found + (name -> value)
        val comma = list.indexOf(',', end)
        at = if (comma < 0) list.length else comma + 1
      }
    }
    found
  }
}
