package everyui

/** A media server that answered a search of the local network ([[everyui.render.Platform.findMediaServers]]).
  * `kind` is "jellyfin", "emby" or "plex"; `id` is the server's own, the same wherever it is reached from; `address`
  * is where it answered, as a base URL (`http://192.168.1.50:8096`). */
case class MediaServer(kind: String, name: String, id: String, address: String)
