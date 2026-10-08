package everyui.audio

/** Turns at a player only one thing can play through at once (a Roku's, where a second Audio node to play fails with
  * "only one playing instance supported"): the newest to play takes it, and the one it took it from stops where it
  * was and waits underneath; when the newcomer stops or ends, the one under it plays on from there. One that stops
  * while it waits only leaves the stack. Each hand-off is told to `log`.
  *
  * {{{
  *   music plays            [music]
  *   a clip plays           [clip, music]     music stops where it was
  *   the clip ends          [music]           music plays on
  * }}} */
class PlayerStack(log: String => Unit) {
  // the one playing first, then those waiting, the newest waiting first
  private var held: List[SoloPlayer] = Nil

  /** The one playing, if any. */
  def playing(): Option[SoloPlayer] = held.headOption

  /** Those waiting for it, the next first. */
  def waiting(): List[SoloPlayer] = held.drop(1)

  /** Who plays and who waits, for a log: "uiMedia2 clip.wav over uiMedia1 music.wav", or "nothing". */
  def describe(): String =
    if (held.isEmpty) "nothing" else held.map(_.soloName()).mkString(" over ")

  /** `p` is to play: it takes the player, from whoever has it, who stops ([[SoloPlayer.yieldPlayer]]) before `p`
    * starts. Whether it took the player from another. */
  def take(p: SoloPlayer): Boolean = if (held.headOption.exists(_ eq p)) false else {
    val from = held.headOption
    held = p :: held.filterNot(_ eq p)
    if (from.isEmpty) false
    else {
      val other = from.get
      log("audio: " + p.soloName() + " takes the player from " + other.soloName())
      other.yieldPlayer()
      true
    }
  }

  /** `p` has stopped, ended or failed: off the stack, and if it had the player, the one under it plays on. Call it
    * once `p` has stopped. */
  def leave(p: SoloPlayer): Unit = if (held.exists(_ eq p)) {
    val had = held.head eq p
    held = held.filterNot(_ eq p)
    if (had) held.headOption.foreach { next =>
      log("audio: " + next.soloName() + " back from " + p.soloName())
      next.reclaimPlayer()
    }
    else log("audio: " + p.soloName() + " stopped while waiting")
  }
}

object PlayerStack {
  /** The device's one player, where it has only one (a Roku's, which its Audio nodes take turns at); unused
    * elsewhere. */
  val device: PlayerStack = new PlayerStack(line => everyui.render.Platform.log(line))
}
