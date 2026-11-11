package everyui.tvos

import everyui.compiler.api.native

/** What plays (everyui.component.VideoView, AudioPlayer): VLCKit's player behind a view
  * (`swift/runtime/MediaRuntime.swift`), one per node. In an app built without VLCKit the node is an empty view and
  * every call after it does nothing (`uiMediaPlays` is false there). */
@native object uiMediaPlays {
  def apply(): Boolean = native.value
}
/** A player: drawing its picture in the view (`video`), or a view with nothing to draw (a sound). */
@native object uiMedia {
  def apply(video: Boolean): SGNode = native.value
}
/** Opened afresh (an empty address stops it and lets the player go), from where `uiMediaStart` last said. */
@native object uiMediaSource {
  def apply(node: SGNode, url: String): Unit = native.value
}
@native object uiMediaStart {
  def apply(node: SGNode, seconds: Double): Unit = native.value
}
/** `Name: value`, a line each, for the openings after this. */
@native object uiMediaHeaders {
  def apply(node: SGNode, lines: String): Unit = native.value
}
/** Each sidecar's address, label and language, a line each in the same order, for the openings after this. */
@native object uiMediaSidecars {
  def apply(node: SGNode, urls: String, labels: String, languages: String): Unit = native.value
}
@native object uiMediaPlaying {
  def apply(node: SGNode, on: Boolean): Unit = native.value
}
@native object uiMediaLoop {
  def apply(node: SGNode, on: Boolean): Unit = native.value
}
@native object uiMediaMuted {
  def apply(node: SGNode, on: Boolean): Unit = native.value
}
@native object uiMediaVolume {
  def apply(node: SGNode, volume: Double): Unit = native.value
}
@native object uiMediaRate {
  def apply(node: SGNode, rate: Double): Unit = native.value
}
@native object uiMediaCover {
  def apply(node: SGNode, on: Boolean): Unit = native.value
}
/** How much it holds from the network: `responsive`, `balanced` or `slow`. */
@native object uiMediaCaching {
  def apply(node: SGNode, named: String): Unit = native.value
}
/** The language to hear the openings after this in, or "" for the file's own. */
@native object uiMediaHear {
  def apply(node: SGNode, language: String): Unit = native.value
}
/** A track heard (`audio`) or read (`text`, "" for none), by its id. */
@native object uiMediaSelect {
  def apply(node: SGNode, kind: String, id: String): Unit = native.value
}
@native object uiMediaAids {
  def apply(node: SGNode, clarity: Double, night: Double): Unit = native.value
}
@native object uiMediaAidsHeard {
  def apply(node: SGNode): Boolean = native.value
}
/** What is playing, for the system's now-playing; an empty title is none. */
@native object uiMediaNowPlaying {
  def apply(node: SGNode, title: String, artist: String, album: String, art: String, seconds: Double): Unit =
    native.value
}
@native object uiMediaPosition {
  def apply(node: SGNode): Double = native.value
}
@native object uiMediaDuration {
  def apply(node: SGNode): Double = native.value
}
@native object uiMediaSeek {
  def apply(node: SGNode, seconds: Double): Unit = native.value
}
@native object uiMediaWaiting {
  def apply(node: SGNode): Boolean = native.value
}
@native object uiMediaBandwidth {
  def apply(node: SGNode): Long = native.value
}
/** The picture's width, or its height (`tall`), as decoded; 0 until it is. */
@native object uiMediaSize {
  def apply(node: SGNode, tall: Boolean): Double = native.value
}
@native object uiMediaTrackCount {
  def apply(node: SGNode): Int = native.value
}
/** Track `index`'s id, kind, language, label or format (`field` 0 to 4). */
@native object uiMediaTrack {
  def apply(node: SGNode, index: Int, field: Int): String = native.value
}
/** MediaDiagnostics' `audio`, `video`, `audioTracks` or `failure`. */
@native object uiMediaDiagnostics {
  def apply(node: SGNode, field: String): String = native.value
}
/** Hear `ready`, `ended`, `error`, `mixing`, and `paused` and `resumed` where the system stopped and restarted it. */
@native object uiMediaOnEvent {
  def apply(node: SGNode, f: String => Unit): Unit = native.value
}
@native object uiMediaRelease {
  def apply(node: SGNode): Unit = native.value
}
