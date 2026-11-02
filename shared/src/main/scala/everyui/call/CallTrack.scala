package everyui.call

/** A picture or a sound someone sends into the call: what a [[CallVideo]] shows, by its `id` (the engine's own),
  * `source` saying which of theirs it is and `participant` whose (their identity). A track the person has turned off
  * for now is not given at all, so a camera off shows their face rather than a still frame. */
case class CallTrack(id: String, source: TrackSource, participant: String)
