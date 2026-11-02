package everyui.call

/** Someone in a call, as the room last told of them (`Call.participants`, `Call.local`): their `identity` (what the
  * app's server put in their token, the same for them in every call) and `name`; whether they are `speaking` now, and
  * how loud (`level`, 0 to 1); whether their `microphone` is on; their `camera` and `screen` while they send one; and
  * how well their connection carries them. */
case class CallParticipant(identity: String,
                           name: String,
                           local: Boolean = false,
                           speaking: Boolean = false,
                           level: Double = 0.0,
                           microphone: Boolean = false,
                           camera: Option[CallTrack] = None,
                           screen: Option[CallTrack] = None,
                           quality: CallQuality = CallQuality.Unknown) {
  /** Their name, or their identity where the room has none. */
  def shownName: String = if (name.isEmpty) identity else name

  /** Their track from `source`, while they send one. */
  def track(source: TrackSource): Option[CallTrack] = source match {
    case TrackSource.Camera => camera
    case TrackSource.Screen => screen
    case _ => None
  }
}
