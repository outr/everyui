package everyui.call

/** What a track carries (`CallTrack.source`): a person's microphone, their camera, the screen they share, or the
  * sound of what they share. */
sealed abstract class TrackSource

object TrackSource {
  case object Microphone extends TrackSource
  case object Camera extends TrackSource
  case object Screen extends TrackSource
  case object ScreenAudio extends TrackSource
}
