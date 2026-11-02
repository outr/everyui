package everyui.call

/** How well someone's connection to the room carries them (`CallParticipant.quality`), as the room measures it. */
sealed abstract class CallQuality

object CallQuality {
  case object Excellent extends CallQuality
  case object Good extends CallQuality
  /** Heard, but breaking up. */
  case object Poor extends CallQuality
  /** Not reaching the room for now: they leave it if it lasts. */
  case object Lost extends CallQuality
  /** Not measured yet. */
  case object Unknown extends CallQuality
}
