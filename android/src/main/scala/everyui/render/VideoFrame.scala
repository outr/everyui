package everyui.render

import android.content.Context
import android.view.{View, ViewGroup}

/** The box a video is drawn in: one view the picture goes into (a `TextureView` or a `SurfaceView`, see
  * [[ExoMedia]]), laid out at the picture's own shape, about the box's centre — the whole of it inside the box, or
  * filling it with the box cutting the rest. Neither view keeps a picture's shape on its own: each stretches it over
  * whatever it is given. */
final class VideoFrame(context: Context) extends ViewGroup(context) {
  private var pictureWidth = 0.0
  private var pictureHeight = 0.0
  private var cover = true

  setClipChildren(true)

  /** The picture's shape as it is shown (its pixels by their aspect), and whether it fills the box. */
  def fit(width: Double, height: Double, cover: Boolean): Unit =
    if (width != pictureWidth || height != pictureHeight || cover != this.cover) {
      pictureWidth = width
      pictureHeight = height
      this.cover = cover
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

  override def onMeasure(widthSpec: Int, heightSpec: Int): Unit = {
    val w = View.getDefaultSize(0, widthSpec)
    val h = View.getDefaultSize(0, heightSpec)
    val (_, _, pw, ph) = place(w, h)
    var i = 0
    while (i < getChildCount) {
      getChildAt(i).measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
      i += 1
    }
    setMeasuredDimension(w, h)
  }

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    val (x, y, pw, ph) = place(r - l, b - t)
    var i = 0
    while (i < getChildCount) {
      getChildAt(i).layout(x, y, x + pw, y + ph)
      i += 1
    }
  }
}
