package everyui.audio

/** A player that has the device's one player to itself while it plays, and gives it up to a newer one
  * ([[PlayerStack]]): a Roku's Audio node, of which SceneGraph plays one at a time. */
trait SoloPlayer {
  /** Who it is, for the log: the node and the file it plays. */
  def soloName(): String

  /** Another took the player: it stops here, keeping where it had reached. */
  def yieldPlayer(): Unit

  /** The player back: on from where it stopped. */
  def reclaimPlayer(): Unit
}
