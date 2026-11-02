package everyui.render

import android.graphics.{Bitmap, Canvas, Paint, RectF}
import android.text.style.ReplacementSpan

/** A run's picture in place of its words (`TextRun.image`): a square `TextRun.pictureEm` times the letters' size, as
  * low under the line as the web sets it, so the lines are as tall before it loads as after; the picture is fitted in
  * it once `Host.bitmapThen` has it, and `loaded` draws the text again. */
class PictureSpan(uri: String, loaded: () => Unit) extends ReplacementSpan {
  private var bitmap: Option[Bitmap] = None
  Host.bitmapThen(uri) { b =>
    if (b != null) {
      bitmap = Some(b)
      loaded()
    }
  }

  private def side(paint: Paint): Float = (paint.getTextSize * everyui.text.TextRun.pictureEm).toFloat

  override def getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt): Int = {
    val s = side(paint)
    if (fm != null) {
      // the square's bottom 0.3 of the letters' size under the line, its top above the letters' where it is taller
      val descent = math.round(paint.getTextSize * 0.3f)
      val ascent = -(math.round(s) - descent)
      val own = paint.getFontMetricsInt
      fm.ascent = math.min(own.ascent, ascent)
      fm.top = math.min(own.top, ascent)
      fm.descent = math.max(own.descent, descent)
      fm.bottom = math.max(own.bottom, descent)
    }
    math.round(s)
  }

  override def draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int,
                    paint: Paint): Unit = bitmap.foreach { b =>
    val s = side(paint)
    val scale = math.min(s / b.getWidth, s / b.getHeight)
    val w = b.getWidth * scale
    val h = b.getHeight * scale
    val down = y + paint.getTextSize * 0.3f - s
    canvas.drawBitmap(b, null, new RectF(x + (s - w) / 2f, down + (s - h) / 2f, x + (s + w) / 2f, down + (s + h) / 2f),
      PictureSpan.smooth)
  }
}

object PictureSpan {
  private val smooth = new Paint(Paint.FILTER_BITMAP_FLAG)
}
