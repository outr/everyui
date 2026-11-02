package everyui.call

/** Why a microphone, a camera or a screen could not be had (`MediaPermission.refusal`, and what turning one on
  * answers). */
sealed abstract class MediaRefusal

object MediaRefusal {
  /** The person said no (to the browser's prompt, or by cancelling a screen's choice). They can be asked again, or
    * change it in the site's settings once the browser no longer asks. */
  case object ByPerson extends MediaRefusal
  /** Not the person's choice: the device or the browser's policy forbids it, or the page is not on https. */
  case object ByPolicy extends MediaRefusal
  /** There is no such device (no camera plugged in), or not the one chosen. */
  case object NoDevice extends MediaRefusal
  /** The device is there but another app holds it, or the system will not give it. */
  case object InUse extends MediaRefusal
  /** Nothing on this platform gives one (`Call.supported` false). */
  case object Unavailable extends MediaRefusal
  /** Anything else, as the platform says it. */
  final case class Failed(message: String) extends MediaRefusal
}
