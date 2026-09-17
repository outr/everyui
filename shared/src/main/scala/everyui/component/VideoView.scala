package everyui.component

import everyui.component.types.Prop
import everyui.render.{Nodes, RenderNode}

/** A video in its box: a file (`.mp4`) or a stream (`.m3u8`) from `source`, filling the box (`cover`, cut at the
  * edges) or shown whole inside it. It plays while `playing` is on, from the start again at the end with `loop`, and
  * stops for good once it has been taken off the screen. `events` hears `ready` (a first frame is up), `ended` and
  * `error`. The platform plays it: an Android MediaPlayer, a JavaFX MediaPlayer, the browser's `video`, a Roku `Video`
  * node. */
class VideoView(node: RenderNode = Nodes.video()) extends Component(node) with MediaSupport {
  /** Filling the box and cut at its edges, or whole inside it. */
  lazy val cover: Prop[Boolean] = new Prop[Boolean](true, c => node.setMediaCover(c))

  override def kind: String = "VideoView(" + source() + ")"
}
