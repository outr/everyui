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
  /** "Microphone 2": a device the platform has given no label yet. */
  def device(kind: DeviceKind, n: Int): String = Words(kind match {
    case DeviceKind.Microphone => "call.microphone"
    case DeviceKind.Camera => "call.camera"
    case DeviceKind.Speaker => "call.speaker"
  }).replace("{n}", n.toString)
}
