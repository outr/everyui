package everyui.call

import everyui.render.CallAccess

/** What carries a call: a media server's client (LiveKit's), behind what `Call` asks of any. An app can give a
  * `Call` an engine of its own, for another server; the platform's (`default`) is LiveKit's in a browser, and none
  * elsewhere for now. */
trait CallEngine {
  /** Its name, for a log ("livekit"). */
  def name: String

  /** Whether it can run a call here. */
  def supported: Boolean

  /** A room for one call, which tells `feed` all that happens in it. */
  def room(feed: CallFeed): CallRoom

  /** The camera and the microphone opened before a call (a `CallPreview`), as tracks of this engine's that its room can
    * send (`CallRoom.take`), telling `feed` the microphone's level. None here (`PreviewMedia.unavailable`) unless the
    * engine gives them. */
  def preview(feed: PreviewFeed): PreviewMedia = PreviewMedia.unavailable
}

object CallEngine {
  /** The platform's engine: LiveKit's client in a browser (everyui.render.LiveKitEngine), and `Unavailable`
    * elsewhere. */
  def default: CallEngine = CallAccess.engine

  /** No calls: one ends as it begins, unavailable, and nothing can be sent. */
  object Unavailable extends CallEngine {
    def name: String = "none"

    def supported: Boolean = false

    def room(feed: CallFeed): CallRoom = new CallRoom {
      def connect(url: String, token: String, devices: Map[DeviceKind, String]): Unit =
        feed.state(CallState.Ended(CallEnd.Unavailable))
      def leave(): Unit = ()
      def send(source: TrackSource, on: Boolean, device: Option[String], done: Option[MediaRefusal] => Unit): Unit =
        done(if (on) Some(MediaRefusal.Unavailable) else None)
      def device(kind: DeviceKind, id: Option[String]): Unit = ()
      def audible(on: Boolean): Unit = ()
      def startAudio(): Unit = ()
      def show(track: CallTrack, into: CallVideo): Unit = ()
      def hide(track: CallTrack, into: CallVideo): Unit = ()
    }
  }
}
