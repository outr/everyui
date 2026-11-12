package everyui.call

import everyui.i18n.Words

/** What the call components say, in the language in use: an app gives its own wording with `Words.add` under these
  * keys ("call.muted", ...). */
object CallWords {
  /** Said of someone whose microphone is off. */
  def muted: String = Words("call.muted")
  /** Said of someone speaking now. */
  def speaking: String = Words("call.speaking")
  /** "Ada's screen", what someone shares. */
  def screenOf(name: String): String = Words("call.screenOf").replace("{name}", name)
  /** What a screen reader hears a [[CameraPreview]] as: this person's own camera. */
  def preview: String = Words("call.preview")
  /** Said in a preview whose camera is turned off. */
  def cameraOff: String = Words("call.cameraOff")
  /** Said in a preview whose camera was refused, by the person or a policy. */
  def cameraDenied: String = Words("call.cameraDenied")
  /** Said in a preview with no camera to open. */
  def noCamera: String = Words("call.noCamera")
  /** Said in a preview whose camera could not be opened (another app holds it). */
  def cameraFailed: String = Words("call.cameraFailed")
  /** What a screen reader hears a [[MicLevel]] as. */
  def micLevel: String = Words("call.micLevel")
  /** A [[MediaToggle]]'s name for the microphone. */
  def microphoneSwitch: String = Words("call.microphoneSwitch")
  /** A [[MediaToggle]]'s name for the camera. */
  def cameraSwitch: String = Words("call.cameraSwitch")
  /** A [[MediaToggle]]'s name for the sound. */
  def soundSwitch: String = Words("call.soundSwitch")
  /** "Microphone 2": a device the platform has given no label yet. */
  def device(kind: DeviceKind, n: Int): String = Words(kind match {
    case DeviceKind.Microphone => "call.microphone"
    case DeviceKind.Camera => "call.camera"
    case DeviceKind.Speaker => "call.speaker"
  }).replace("{n}", n.toString)
}
