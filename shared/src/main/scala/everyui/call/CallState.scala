package everyui.call

/** Where a call stands (`Call.state`): not begun, reaching the room, in it, finding it again after the network went,
  * or over, with why. */
sealed abstract class CallState

object CallState {
  /** Not begun: `Call.connect` has not been called. */
  case object Idle extends CallState
  /** Reaching the room. */
  case object Connecting extends CallState
  /** In the room: heard and hearing. */
  case object Connected extends CallState
  /** The network went and the room is being reached again; the call carries on once it is. */
  case object Reconnecting extends CallState
  /** Over: `why` says what ended it. A call that ended is not begun again; a new `Call` joins the room again. */
  final case class Ended(why: CallEnd) extends CallState
}
