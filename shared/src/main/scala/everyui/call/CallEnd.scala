package everyui.call

/** Why a call ended (`CallState.Ended`). */
sealed abstract class CallEnd

object CallEnd {
  /** The app left it (`Call.leave`). */
  case object Left extends CallEnd
  /** Someone with the right to took this person out of the room. */
  case object Removed extends CallEnd
  /** The same person joined the room from somewhere else, which took their place. */
  case object Replaced extends CallEnd
  /** The room closed, or was taken away. */
  case object Closed extends CallEnd
  /** The connection was lost and could not be had again. */
  case object Lost extends CallEnd
  /** The room would not have this person: the token was refused (out of date, for another room), or it was full. */
  final case class Refused(message: String) extends CallEnd
  /** Nothing on this platform runs calls (`Call.supported` false), or the engine's script could not be loaded. */
  case object Unavailable extends CallEnd
  /** Anything else, as the engine says it. */
  final case class Failed(message: String) extends CallEnd
}
