package everyui.render

import everyui.Permission
import everyui.call.{CallEngine, DeviceKind, MediaDevice, MediaRefusal}

/** Calls (`everyui.call`) and the devices they use. A Roku has no camera, no microphone of its own and no WebRTC: no
  * devices are listed, the access is refused as unavailable, and a call ends as it connects
  * (`CallEngine.Unavailable`). */
object CallAccess {
  def engine: CallEngine = CallEngine.Unavailable

  def supported(): Boolean = false

  def speakersChosen(): Boolean = false

  def devices(found: Vector[MediaDevice] => Unit): Unit = found(Vector.empty)

  def watch(changed: () => Unit, permission: (DeviceKind, Permission) => Unit): Unit = ()

  def ask(microphone: Boolean, camera: Boolean, mic: Option[String], cam: Option[String],
          done: Option[MediaRefusal] => Unit): Unit = done(Some(MediaRefusal.Unavailable))

  def meter(device: Option[String], opened: () => Unit, level: Double => Unit,
            refused: MediaRefusal => Unit): () => Unit = {
    refused(MediaRefusal.Unavailable)
    () => ()
  }
}
