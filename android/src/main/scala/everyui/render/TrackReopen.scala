package everyui.render

/** When choosing a track reopens the file rather than switching in place (ExoMedia).
  *
  * A file played whole over the network (an MKV direct-played from Plex) holds one long response open while it plays.
  * A track switched in place may make the player load the file again from where it is (Media3 reloads it when the
  * track's samples are not in what it holds, or when it recovers an audio output that could not follow the switch, as
  * a passthrough device's can fail to): a new ranged request, sent the moment the old response is let go. A server
  * still holding the old one can leave the new one unanswered, and the picture stops. A file opened afresh at the
  * same place, with the track chosen before it starts, plays: so that is what a choice does here. Every connection is
  * closed first ([[MediaSockets]]), then, a moment later ([[settleSeconds]]), the file is opened again on the same
  * player, which keeps its place, its play or pause and its other choices.
  *
  * Not reopened: an adaptive stream (HLS, DASH), which fetches segments and switches on its own; a file on the device;
  * text turned off, which only stops reading a track; and a sidecar subtitle, a file of its own read whole. Plain
  * Scala, for the desktop's specs. */
object TrackReopen {
  /** How long after the old connections are closed the file is asked for again: time for the server to see them go. */
  val settleSeconds: Double = 0.5

  /** Whether a track choice reopens the source at `url`: one played whole (not `adaptive`) over the network, where the
    * choice turns on a track of the file itself, by the id of its group (`enabling`; None where it turns one off). */
  def needed(url: String, adaptive: Boolean, enabling: Option[String]): Boolean =
    !adaptive && network(url) && enabling.exists(fromFile)

  def network(url: String): Boolean = {
    val lower = url.trim.toLowerCase
    lower.startsWith("http://") || lower.startsWith("https://")
  }

  /** Whether a track group is the file's own: Media3 numbers a file's groups ("2"), and with sidecars merges them,
    * the file's first ("0:2") and each sidecar's after ("1:..."). An empty id (the stream's own choice of sound) is
    * the file's. */
  def fromFile(groupId: String): Boolean = {
    val colon = groupId.indexOf(':')
    colon < 0 || groupId.substring(0, colon) == "0"
  }
}
