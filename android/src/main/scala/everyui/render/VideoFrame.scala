package everyui.render

import android.content.Context
import android.view.{SurfaceView, View, ViewGroup}
import android.widget.ImageView
import androidx.media3.common.text.Cue

/** The box a video is drawn in: one view the picture goes into (a `TextureView` or a `SurfaceView`, see
  * [[ExoMedia]]), laid out at the picture's own shape, about the box's centre — the whole of it inside the box, or
  * filling it with the box cutting the rest. Neither view keeps a picture's shape on its own: each stretches it over
  * whatever it is given.
  *
  * Over the picture go the subtitles that are pictures themselves (a Blu-ray's PGS, a DVD's VobSub): each where the
  * file puts it, as a share of the picture, as media3's own painter places them. Text subtitles are the app's to draw;
  * these have nothing in them to draw but the image. */
final class VideoFrame(context: Context) extends ViewGroup(context) {
  private var pictureWidth = 0.0
  private var pictureHeight = 0.0
  private var cover = true
  private var picture: Option[View] = None
  private var bitmaps: List[(Cue, ImageView)] = Nil

  setClipChildren(true)

  /** The view the picture is drawn into, in place of the one before. A `SurfaceView` is laid out at its size in the
    * screen's pixels and shrunk back by the density the whole app is scaled up by (EveryUIActivity), so nothing above
    * it scales it. Android 11 places a `SurfaceView`'s plane twice, once from the view's own size and once from its
    * size on the screen, and the two disagree under a scaled parent: the last to land wins, and on a SHIELD that is
    * often the first, a 1080p picture on a quarter of a 4K panel with its upscaling off. */
  def hold(view: View): Unit = {
    picture.foreach(removeView)
    picture = Some(view)
    if (grown(view) != 1.0) {
      view.setPivotX(0f)
      view.setPivotY(0f)
      view.setScaleX((1.0 / grown(view)).toFloat)
      view.setScaleY((1.0 / grown(view)).toFloat)
    }
    // under the subtitles, whatever came first
    addView(view, 0)
  }

  /** The picture's shape as it is shown (its pixels by their aspect), and whether it fills the box. */
  def fit(width: Double, height: Double, cover: Boolean): Unit =
    if (width != pictureWidth || height != pictureHeight || cover != this.cover) {
      pictureWidth = width
      pictureHeight = height
      this.cover = cover
      requestLayout()
    }

  /** The subtitles that are pictures, as they are now: those of `cues` with a bitmap, the rest being text. */
  def cues(cues: List[Cue]): Unit = {
    bitmaps.foreach(b => removeView(b._2))
    bitmaps = cues.filter(_.bitmap != null).map { cue =>
      val view = new ImageView(context)
      view.setScaleType(ImageView.ScaleType.FIT_XY)
      view.setImageBitmap(cue.bitmap)
      addView(view)
      (cue, view)
    }
    requestLayout()
  }

  /** Where the picture goes in a box `w` by `h`: its left, top, width and height. The whole box until its shape is
    * known. */
  private def place(w: Int, h: Int): (Int, Int, Int, Int) =
    if (w <= 0 || h <= 0 || pictureWidth <= 0 || pictureHeight <= 0) (0, 0, w, h)
    else {
      val scale =
        if (cover) math.max(w / pictureWidth, h / pictureHeight)
        else math.min(w / pictureWidth, h / pictureHeight)
      val pw = math.round(pictureWidth * scale).toInt
      val ph = math.round(pictureHeight * scale).toInt
      ((w - pw) / 2, (h - ph) / 2, pw, ph)
    }

  /** Where a picture subtitle goes over a picture at `x`, `y`, `pw` by `ph`: its position and line are shares of the
    * picture, its size a share of the width, its height a share of the height or else the image's own shape; each
    * anchored at its start, middle or end. */
  private def spot(cue: Cue, x: Int, y: Int, pw: Int, ph: Int): (Int, Int, Int, Int) = {
    val across = if (cue.position == Cue.DIMEN_UNSET) 0.5f else cue.position
    val down = if (cue.line == Cue.DIMEN_UNSET) 0.9f else cue.line
    val w = math.round(pw * (if (cue.size == Cue.DIMEN_UNSET) 1.0f else cue.size))
    val h =
      if (cue.bitmapHeight != Cue.DIMEN_UNSET) math.round(ph * cue.bitmapHeight)
      else math.round(w * cue.bitmap.getHeight.toFloat / math.max(1, cue.bitmap.getWidth))
    val ax = x + pw * across
    val ay = y + ph * down
    val left = cue.positionAnchor match {
      case Cue.ANCHOR_TYPE_END => ax - w
      case Cue.ANCHOR_TYPE_MIDDLE => ax - w / 2
      case _ => ax
    }
    val top = cue.lineAnchor match {
      case Cue.ANCHOR_TYPE_END => ay - h
      case Cue.ANCHOR_TYPE_MIDDLE => ay - h / 2
      case _ => ay
    }
    (math.round(left), math.round(top), w, h)
  }

  override def onMeasure(widthSpec: Int, heightSpec: Int): Unit = {
    val w = View.getDefaultSize(0, widthSpec)
    val h = View.getDefaultSize(0, heightSpec)
    val (x, y, pw, ph) = place(w, h)
    picture.foreach(v => exactly(v, pixels(v, pw), pixels(v, ph)))
    bitmaps.foreach { case (cue, view) =>
      val (_, _, cw, ch) = spot(cue, x, y, pw, ph)
      exactly(view, cw, ch)
    }
    setMeasuredDimension(w, h)
  }

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    val (x, y, pw, ph) = place(r - l, b - t)
    picture.foreach(v => v.layout(x, y, x + pixels(v, pw), y + pixels(v, ph)))
    bitmaps.foreach { case (cue, view) =>
      val (cx, cy, cw, ch) = spot(cue, x, y, pw, ph)
      view.layout(cx, cy, cx + cw, cy + ch)
    }
  }

  /** How many times larger `view` is laid out than it is shown: the density for a `SurfaceView`, else 1. */
  private def grown(view: View): Double = view match {
    case _: SurfaceView if Host.scale > 0.0 => Host.scale
    case _ => 1.0
  }

  private def pixels(view: View, size: Int): Int = math.round(size * grown(view)).toInt

  private def exactly(view: View, w: Int, h: Int): Unit =
    view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
}
