package everyui.call

/** A microphone, camera or speaker this device has (`MediaDevices.microphones`, ...): its `id`, to choose it by, and
  * its `label` to show. A browser gives the labels only once the person has let the page use one of them
  * (`MediaDevices.ask`); until then `label` is empty, and `shown` names it by its kind and place. */
case class MediaDevice(id: String, label: String, kind: DeviceKind, index: Int = 0) {
  /** Its label, or its kind and number where the platform gives none yet. */
  def shown: String = if (label.nonEmpty) label else CallWords.device(kind, index + 1)
}
