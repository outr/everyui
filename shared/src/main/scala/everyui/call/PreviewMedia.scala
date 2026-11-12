package everyui.call

/** The camera and the microphone a [[CallPreview]] opens before a call, as an engine opens them
  * (`CallEngine.preview`): its own kind of track, so that the engine's room, given them as a call is joined
  * (`CallRoom.take`), sends those very tracks rather than opening the devices again. It tells its `PreviewFeed` the
  * microphone's level and a device lost. Everything is asked and told on the thread that draws. */
trait PreviewMedia {
  /** Open the devices `wanted` (each kind with its id; None is the system's own) together, under one prompt where the
    * platform asks, the one of a kind already open closed first (another device chosen); `done` hears, for each kind,
    * why it could not be had (None: open). A kind closed again before it opened is not answered. */
  def open(wanted: Map[DeviceKind, Option[String]], done: (DeviceKind, Option[MediaRefusal]) => Unit): Unit

  /** Close the device of `kind`, one still opening too: its track stopped and its light off. */
  def close(kind: DeviceKind): Unit

  /** Draw the camera in `into`'s picture (as `into.cover` and `into.mirror` say) while it is open, and a camera opened
    * after once it is, until `hide`. */
  def show(into: CameraPreview): Unit

  /** Stop drawing the camera in `into`. */
  def hide(into: CameraPreview): Unit

  /** Close everything, for good. */
  def stop(): Unit
}

object PreviewMedia {
  /** No devices: each is refused as unavailable, and nothing is drawn. */
  def unavailable: PreviewMedia = new PreviewMedia {
    def open(wanted: Map[DeviceKind, Option[String]], done: (DeviceKind, Option[MediaRefusal]) => Unit): Unit =
      wanted.keys.foreach(k => done(k, Some(MediaRefusal.Unavailable)))
    def close(kind: DeviceKind): Unit = ()
    def show(into: CameraPreview): Unit = ()
    def hide(into: CameraPreview): Unit = ()
    def stop(): Unit = ()
  }
}
