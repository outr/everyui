package everyui.audio

/** What a sound effect ([[everyui.component.AudioPlayer.effect]]) can be played from on a Roku, whose mixer for short
  * sounds (SceneGraph's SoundEffect node) plays a WAV kept on the device (in the package, `tmp:/`, `cachefs:/`) and
  * nothing else. Elsewhere any file the platform plays will do. */
object EffectSource {
  /** Whether `source` is a WAV, by its path (any query left off). */
  def wav(source: String): Boolean = {
    val q = source.indexOf('?')
    val path = (if (q >= 0) source.substring(0, q) else source).toLowerCase
    path.endsWith(".wav")
  }

  /** Whether it is on the device, rather than an address to fetch. */
  def local(source: String): Boolean = {
    val lower = source.toLowerCase
    !(lower.startsWith("http:") || lower.startsWith("https:"))
  }

  /** Whether a Roku plays it as an effect, over whatever its player plays. */
  def mixes(source: String): Boolean = source.nonEmpty && wav(source) && local(source)

  /** Why a Roku cannot play `source` as an effect, or "" where it can. */
  def unfit(source: String): String =
    if (!wav(source)) "not a WAV"
    else if (!local(source)) "an address, not a file on the device"
    else ""
}
