package everyui.call

/** What an engine's [[PreviewMedia]] tells the [[CallPreview]] it opens devices for: how loud the microphone hears the
  * room, and a device lost by itself (taken out, or stopped by the system). Told on the thread that draws. */
final class PreviewFeed private[call] (preview: CallPreview) {
  /** The open microphone hears this loud now, from 0 (silence) to 1. */
  def level(level: Double): Unit = preview.heard(level)

  /** The open device of `kind` is gone, for `why` (unplugged: `MediaRefusal.NoDevice`). */
  def lost(kind: DeviceKind, why: MediaRefusal): Unit = preview.lost(kind, why)
}
