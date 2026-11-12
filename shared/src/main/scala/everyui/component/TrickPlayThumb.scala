package everyui.component

import everyui.component.types.Plain
import everyui.image.Image
import reactify.*

/** The trick-play thumbnail of `video` at `at` seconds, for a scrubber of the app's own: it follows `at` as the
  * scrubber moves, showing each frame as it arrives ([[VideoView.thumbnail]]) and the one before until it does, so it
  * never blinks empty mid-scrub. `width` by `height` to start with (the thumbnails' own proportions are the app's to
  * keep), the frame filling its box; [[showing]] says whether there is a frame in it yet, for the app to hide it while
  * there is none (no [[VideoView.thumbnails]], or none fetched yet).
  *
  * {{{
  *   video.thumbnails @= ThumbnailSource.BifFile(item.bifUrl)
  *   val thumb = new TrickPlayThumb(video, scrubbedTo, 320.0, 180.0)
  *   thumb.visible := thumb.showing() && scrubbing()
  * }}} */
class TrickPlayThumb(video: VideoView, at: Val[Double], width: Double, height: Double) extends Container {
  /** Whether a frame is in it. */
  val showing: Var[Boolean] = own(Plain[Boolean](false))
  /** The frame in it, if any. */
  val frame: Var[Option[ThumbFrame]] = own(Plain[Option[ThumbFrame]](None))

  private val picture = new ImageView
  picture.sizedByPicture @= false
  size.width @= width
  size.height @= height
  clipsToBox @= Some(true)
  children += picture

  at.attach(seconds => ask(seconds))
  video.thumbnails.attach { _ =>
    frame @= None
    showing @= false
    picture.image @= Image.empty
    ask(at())
  }
  frame.attach(_ => place())
  size.width.attach(_ => place())
  size.height.attach(_ => place())
  ask(at())

  private def ask(seconds: Double): Unit = video.thumbnail(seconds, f => {
    frame @= Some(f)
    showing @= true
  })

  // the picture as large as the box times its grid, moved so the cell wanted is in the box
  private def place(): Unit = frame().foreach { f =>
    val w = size.width()
    val h = size.height()
    val across = math.max(1, f.across)
    val down = math.max(1, f.down)
    picture.size.width @= w * across
    picture.size.height @= h * down
    picture.position.left @= -w * f.column
    picture.position.top @= -h * f.row
    if (picture.image().source != f.address) picture.image @= new Image(f.address, w * across, h * down)
  }

  override def kind: String = "TrickPlayThumb"
}
