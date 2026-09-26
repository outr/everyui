package everyui.component

import everyui.component.types.Prop
import everyui.render.{Nodes, RenderNode}

/** A video in its box: a file (`.mp4`) or a stream (`.m3u8`) from `source`, filling the box (`cover`, cut at the
  * edges) or shown whole inside it. It plays while `playing` is on, from the start again at the end with `loop`, and
  * stops for good once it has been taken off the screen. `events` hears `ready` (a first frame is up), `ended` and
  * `error`. The platform plays it: an Android MediaPlayer, a JavaFX MediaPlayer, the browser's `video`, a Roku `Video`
  * node. */
class VideoView(node: RenderNode = Nodes.video()) extends Component(node) with MediaSupport {
  /** Its player let go for good along with it (MediaSupport.release). */
  override def dispose(): Unit = {
    release()
    super.dispose()
  }

  /** Filling the box and cut at its edges, or whole inside it. */
  lazy val cover: Prop[Boolean] = own(new Prop[Boolean](true, c => node.setMediaCover(c)))

  /** Handed to the display to draw on its own plane rather than drawn with the rest of the app: a film at its own
    * resolution, its HDR intact, scaled by the television rather than the app. It cannot then fade, turn or be cut
    * by a rounded box, so it is for the thing being watched, not a picture behind a screen. Only Android has the
    * choice; elsewhere a video is drawn the one way the platform has. */
  lazy val direct: Prop[Boolean] = own(new Prop[Boolean](false, d => node.setMediaDirect(d)))

  override def kind: String = "VideoView(" + source() + ")"
}
