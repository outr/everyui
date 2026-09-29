package everyui.audio

/** Why a capture ended: stopped by the app, refused, unavailable here, lost, or failed. It ends once, and nothing
  * comes after it. */
sealed trait CaptureEnd

object CaptureEnd {
  /** The app stopped it ([[Capture.stop]]). */
  case object Stopped extends CaptureEnd
  /** The person (or the platform's policy) refused the microphone. */
  case object Denied extends CaptureEnd
  /** No microphone here, or none this platform lets an app record from. */
  case object Unavailable extends CaptureEnd
  /** The microphone went away while it was recording: unplugged, or taken by the system. */
  case object Lost extends CaptureEnd
  /** Anything else, as the platform put it. */
  case class Failed(message: String) extends CaptureEnd
}
