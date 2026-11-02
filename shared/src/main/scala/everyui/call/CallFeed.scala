package everyui.call

/** What an engine tells the `Call` it carries (`CallEngine.room`): where it stands, who is there and what they send,
  * what this person sends, and whether the browser holds the sound back (what the platform refused, `Call` tells
  * `MediaDevices` itself). Told on the thread that draws. */
final class CallFeed private[call] (call: Call) {
  /** It stands at `state` now. */
  def state(state: CallState): Unit = call.stateIs(state)

  /** Who is in the room now: this person (`local`, once in) and everyone else, in the order to show them. */
  def people(local: Option[CallParticipant], remote: Vector[CallParticipant]): Unit = call.peopleAre(local, remote)

  /** This person sends `source` now, or no longer (stopped by the platform too: the browser's own "Stop sharing"). */
  def sending(source: TrackSource, on: Boolean): Unit = call.sendingIs(source, on)

  /** The browser holds the others' sound back until the page is used (`Call.startAudio` from a click), or no longer. */
  def audioBlocked(blocked: Boolean): Unit = call.blockedIs(blocked)
}
