package everyui.render

/** What a player last said on the main thread (`ExoMedia`): its place, its length, whether it waits, its tracks and
  * what it decoded. A read made off the main thread is answered with it, a player being the main thread's alone. */
final class MediaSaid {
  @volatile var position: Double = 0.0
  @volatile var duration: Double = 0.0
  @volatile var buffering: Boolean = false
  @volatile var tracks: List[everyui.component.MediaTrack] = Nil
  @volatile var diagnostics: everyui.component.MediaDiagnostics = everyui.component.MediaDiagnostics()
}
