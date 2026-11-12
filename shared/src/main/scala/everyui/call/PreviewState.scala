package everyui.call

/** Where a [[CallPreview]]'s camera or microphone stands (`cameraState`, `micState`): turned off by the person,
  * opening, open and live, or not to be had, and why, for the app to word: refused (`Denied`, by the person or by a
  * policy), none plugged in (`NoDevice`), none on this platform (`Unavailable`), or failed otherwise (another app holds
  * it). */
sealed abstract class PreviewState {
  /** Whether it could not be had: refused, missing, unavailable or failed. A call joined from the preview starts with
    * it off. */
  def refused: Boolean = false
}

object PreviewState {
  /** Turned off by the person (`cameraOn` or `micOn` false): closed, its light off. */
  case object Off extends PreviewState
  /** Opening: the platform's prompt may be up. */
  case object Starting extends PreviewState
  /** Open: the camera's picture drawn, the microphone's level heard. */
  case object Live extends PreviewState
  /** Refused: by the person (`MediaRefusal.ByPerson`, at the prompt or in the site's settings) or by a policy
    * (`MediaRefusal.ByPolicy`: the device's, the browser's, a page not on https). */
  final case class Denied(why: MediaRefusal) extends PreviewState {
    override def refused: Boolean = true
  }
  /** There is none: nothing plugged in, or the one chosen was taken out. */
  case object NoDevice extends PreviewState {
    override def refused: Boolean = true
  }
  /** Nothing on this platform gives one (`CallEngine.preview` has none). */
  case object Unavailable extends PreviewState {
    override def refused: Boolean = true
  }
  /** It is there but could not be opened: another app holds it (`MediaRefusal.InUse`), or the platform failed. */
  final case class Failed(why: MediaRefusal) extends PreviewState {
    override def refused: Boolean = true
  }

  /** The state a refusal leaves a device in. */
  def of(why: MediaRefusal): PreviewState = why match {
    case MediaRefusal.ByPerson | MediaRefusal.ByPolicy => Denied(why)
    case MediaRefusal.NoDevice => NoDevice
    case MediaRefusal.Unavailable => Unavailable
    case other => Failed(other)
  }
}
