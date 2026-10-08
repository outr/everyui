package everyui.component

import everyui.PlatformKind
import everyui.audio.EffectSource
import everyui.component.types.Prop
import everyui.render.{Nodes, Platform, RenderNode}
import reactify.Var

/** Sound from `source` (a file or a stream), heard while `playing` is on and while it is on the screen (it takes no
  * room there): music under a game, a clip to name. The same controls as a [[VideoView]], with nothing to draw.
  *
  * A Roku plays one at a time: the newest to play takes its player, and the one it took it from stops where it was
  * and plays on from there once the newcomer stops or ends (`everyui.audio.PlayerStack`). A short sound over the
  * others (a cheer, a click) is an [[effect]], which never takes the player. */
class AudioPlayer(node: RenderNode = Nodes.audio()) extends Component(node) with MediaSupport {
  /** A short sound over whatever else plays, never taking the player from it: set it BEFORE [[source]]. Each time the
    * source is set (or `playing` turned on) it plays once, from its start; it says nothing of its end.
    *
    * On a Roku it is SceneGraph's SoundEffect, which mixes with the Audio node playing, and plays only a WAV on the
    * device (a file in the channel's package, or under `tmp:/` or `cachefs:/`): anything else is reported
    * (`[error] audio: …`) and played through the player, stopping what plays there until it is done. Its `volume` is
    * a share of the device's own menu volume, and none at all where that is off. Elsewhere it plays as any sound
    * does, mixed with the rest, and one a Roku could not play as an effect is said once in the log. */
  lazy val effect: Prop[Boolean] = own(new Prop[Boolean](false, e => node.setMediaEffect(e)))

  source.attach(s => if (effect() && s.nonEmpty && !EffectSource.mixes(s)) AudioPlayer.unfit(s))

  /** Its player let go for good along with it (MediaSupport.release). */
  override def dispose(): Unit = {
    release()
    super.dispose()
  }

  override def kind: String = "AudioPlayer(" + source() + ")"
}

object AudioPlayer {
  // the effects said once each not to suit a Roku
  private val said: Var[Set[String]] = Var(Set.empty)

  // on a Roku the node reports it as an error (once a source); elsewhere it is said once, for whoever builds for one
  private def unfit(source: String): Unit = if (Platform.kind != PlatformKind.Roku && !said().contains(source)) {
    said @= said() + source
    Platform.log("audio: effect " + source + " is " + EffectSource.unfit(source) +
      ": a Roku plays it through its one player, stopping what plays there, where a WAV in the package mixes")
  }
}
