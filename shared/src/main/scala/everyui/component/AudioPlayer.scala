package everyui.component

import everyui.render.{Nodes, RenderNode}

/** Sound from `source` (a file or a stream), heard while `playing` is on and while it is on the screen (it takes no
  * room there): music under a game, a clip to name. The same controls as a [[VideoView]], with nothing to draw. */
class AudioPlayer(node: RenderNode = Nodes.audio()) extends Component(node) with MediaSupport {
  /** Its player let go for good along with it (MediaSupport.release). */
  override def dispose(): Unit = {
    release()
    super.dispose()
  }

  override def kind: String = "AudioPlayer(" + source() + ")"
}
