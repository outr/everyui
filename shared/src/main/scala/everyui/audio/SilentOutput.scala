package everyui.audio

/** The output where a platform plays no PCM (a Roku, an Apple TV): each chunk is played out at once, in silence. */
final class SilentOutput(drained: Long => Unit) extends PcmOutput {
  def write(samples: Array[Short], number: Long): Unit = everyui.render.Platform.later(() => drained(number))

  def flush(): Unit = ()

  def volume(level: Double): Unit = ()

  def close(): Unit = ()
}
