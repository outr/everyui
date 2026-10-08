package everyui.audio

/** What a platform does with sound as [[SoundTurns]] routes it: a player that takes its turn at the device's one
  * player, and effects that mix over it (a Roku's Audio node and its SoundEffect nodes). */
trait SoundOutput {
  /** The node's id, for the log ("uiMedia5"). */
  def id(): String

  /** The player's source ("" stops it), and whether it plays. */
  def source(url: String): Unit
  def playing(on: Boolean): Unit

  /** The player handed to another, stopping where it was; back, on from there; and taken from another just now, which
    * may not have let it go yet. */
  def yieldPlayer(): Unit
  def reclaimPlayer(): Unit
  def handedOff(): Unit

  /** An effect loaded to play later, played once at a volume of 0 to 1, and stopped. */
  def loadEffect(url: String): Unit
  def playEffect(url: String, volume: Double): Unit
  def stopEffect(url: String): Unit

  /** An error, reported as one nothing caught is, the first time `key` is. */
  def report(key: String, message: String): Unit
}
