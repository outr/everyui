package everyui.call

/** What this person chose for their calls on this device (`CallSettings.current`): the `microphone`, `camera` and
  * `speaker` by id (None, the system's own), whether the microphone's sound is cleaned up as it is recorded
  * (`noiseSuppression`, `echoCancellation`, `autoGain`: the browser's getUserMedia constraints of those names), and the
  * camera's `quality`. A call and a preview send with them (`CallRoom.choose`, `PreviewMedia.choose`). */
case class CallChoices(microphone: Option[String] = None, camera: Option[String] = None,
                       speaker: Option[String] = None, noiseSuppression: Boolean = true,
                       echoCancellation: Boolean = true, autoGain: Boolean = true,
                       quality: VideoQuality = VideoQuality.Auto) {
  /** How the camera is sent; None, not at all. */
  def video: Option[VideoSend] = quality.send

  /** Whether the microphone is recorded the same way in `other`: the same processing on or off. */
  def sameProcessing(other: CallChoices): Boolean = noiseSuppression == other.noiseSuppression &&
    echoCancellation == other.echoCancellation && autoGain == other.autoGain
}
