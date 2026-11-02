package everyui.call

/** What a [[MediaDevice]] is: a microphone, a camera, or a speaker (headphones, a headset) sound can be sent to. */
sealed abstract class DeviceKind

object DeviceKind {
  case object Microphone extends DeviceKind
  case object Camera extends DeviceKind
  case object Speaker extends DeviceKind
}
