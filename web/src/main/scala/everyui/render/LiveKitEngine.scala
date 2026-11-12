package everyui.render

import everyui.call.{CallEngine, CallFeed, CallRoom, PreviewFeed, PreviewMedia}

/** Calls through a LiveKit server (`everyui.call.Call`), with LiveKit's own browser client (LiveKitScript), the web's
  * engine: wherever the page may use WebRTC and the microphone (a secure context). */
object LiveKitEngine extends CallEngine {
  def name: String = "livekit"

  def supported: Boolean = CallAccess.supported()

  def room(feed: CallFeed): CallRoom = new LiveKitRoom(feed)

  /** A preview's camera and microphone as LiveKit's own local tracks, which its room publishes as the call is
    * joined (LiveKitPreview). */
  override def preview(feed: PreviewFeed): PreviewMedia =
    if (supported) new LiveKitPreview(feed) else PreviewMedia.unavailable
}
