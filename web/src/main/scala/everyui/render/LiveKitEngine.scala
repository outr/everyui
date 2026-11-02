package everyui.render

import everyui.call.{CallEngine, CallFeed, CallRoom}

/** Calls through a LiveKit server (`everyui.call.Call`), with LiveKit's own browser client (LiveKitScript), the web's
  * engine: wherever the page may use WebRTC and the microphone (a secure context). */
object LiveKitEngine extends CallEngine {
  def name: String = "livekit"

  def supported: Boolean = CallAccess.supported()

  def room(feed: CallFeed): CallRoom = new LiveKitRoom(feed)
}
