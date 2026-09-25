package everyui.component

/** What the player actually decoded, as it says, for an app's report of what played: the server that named the stream
  * knows only what it told the device to fetch. `audio` is the sound's format, its channels, rate and bitrate
  * ("audio/ac3 ch=6 hz=48000 br=384000"), `video` the picture's format and size ("video/avc 1920x1080"), each "none"
  * where the stream has none; `audioTracks` the languages it can be heard in, the one chosen marked `*`, a label in
  * brackets and one the device cannot play marked `!unsupported` ("en*,fr(Commentary)"); and `failure` the last error,
  * its code and every cause under it, with no address (an address carries a token). Each "" where the platform does
  * not say. */
case class MediaDiagnostics(audio: String = "", video: String = "", audioTracks: String = "", failure: String = "")
