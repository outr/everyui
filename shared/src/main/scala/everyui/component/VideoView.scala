package everyui.component

import everyui.component.types.Prop
import everyui.render.{Nodes, RenderNode}

/** A video in its box: a file (`.mp4`) or a stream (`.m3u8`) from `source`, filling the box (`cover`, cut at the
  * edges) or shown whole inside it. It plays while `playing` is on, from the start again at the end with `loop`, and
  * stops for good once it has been taken off the screen. `events` hears `ready` (a first frame is up), `ended` and
  * `error`. The platform plays it: an Android MediaPlayer, a JavaFX MediaPlayer, the browser's `video` (an HLS stream
  * through hls.js where the browser plays none itself, or lists no tracks: CONSUMING.md), a Roku `Video` node, VLCKit
  * on Apple.
  *
  * Its captions are `captions` (MediaSupport): `TextChoice.TextFromDevice`, the device's own caption setting, until
  * the app chooses `TextOff` or `TextTrack(id)`. `textTrack` is the same choice as an id, where None is the DEVICE's
  * choice and not none: none is `Some("")`, or better `captions @= TextChoice.TextOff`. */
class VideoView(node: RenderNode = Nodes.video()) extends Component(node) with MediaSupport {
  /** Its player let go for good along with it (MediaSupport.release), and the thumbnails it kept. */
  override def dispose(): Unit = {
    release()
    trickPlay.use(ThumbnailSource.NoThumbnails)
    super.dispose()
  }

  /** Where its trick-play thumbnails are ([[ThumbnailSource]]: a BIF file, or tile sheets), for a scrubber of the app's
    * own to show the place it is at as it is moved: a [[TrickPlayThumb]] does, or [[thumbnail]] for the picture itself.
    * Set with the source, after [[headers]] (they go with each request). The same on every platform, and drawn by the
    * app rather than the player: a Roku's Video has its own trick-play bar off (`enableUI` false), as everywhere the
    * app's controls are its own. Where a frame cannot be fetched (a browser refused another origin's), none is
    * shown. */
  lazy val thumbnails: Prop[ThumbnailSource] =
    own(new Prop[ThumbnailSource](ThumbnailSource.NoThumbnails, s => trickPlay.use(s)))
  private lazy val trickPlay: everyui.trickplay.TrickPlay = new everyui.trickplay.TrickPlay(() => headers())

  /** The trick-play thumbnail for `seconds` in, to `show` once it is here ([[ThumbFrame]]: a picture, and which cell
    * of it): at once for a tile sheet's or a frame already fetched, when it arrives for a BIF's, and never where there
    * is none. Only the latest ask is answered, so a scrubber moved quickly is shown where it stopped. */
  def thumbnail(seconds: Double, show: ThumbFrame => Unit): Unit = trickPlay.frame(seconds, show)

  /** Filling the box and cut at its edges, or whole inside it. */
  lazy val cover: Prop[Boolean] = own(new Prop[Boolean](true, c => node.setMediaCover(c)))

  /** Handed to the display to draw on its own plane rather than drawn with the rest of the app: a film at its own
    * resolution, its HDR intact, scaled by the television rather than the app. It cannot then fade, turn or be cut
    * by a rounded box, so it is for the thing being watched, not a picture behind a screen. Only Android has the
    * choice; elsewhere a video is drawn the one way the platform has. */
  lazy val direct: Prop[Boolean] = own(new Prop[Boolean](false, d => node.setMediaDirect(d)))

  override def kind: String = "VideoView(" + source() + ")"
}
