package everyui.render

import android.content.Context
import android.graphics.{Canvas, Paint}
import android.widget.TextView

/** A TextView that can draw an edge round its letters (TextView.textOutline): the words drawn twice, as a stroke in
  * the edge's colour and then filled in their own, so the edge is outside every letter rather than eating into it.
  * Android's text has a shadow and no outline; this is the outline. */
final class OutlinedText(context: Context) extends TextView(context) {
  private var stroke = 0f
  private var edge = 0
  // the colour is swapped for the stroke's while it is drawn, and a TextView redraws itself whenever its colour is
  // set: that is held off for the one frame
  private var drawing = false

  /** An edge `width` pixels wide each side in `colour`, or none for a width of 0. */
  def outline(width: Float, colour: Int): Unit = {
    stroke = width
    edge = colour
    invalidate()
  }

  override def invalidate(): Unit = if (!drawing) super.invalidate()

  override def onDraw(canvas: Canvas): Unit =
    if (stroke <= 0f) super.onDraw(canvas)
    else {
      drawing = true
      val colours = getTextColors
      val paint = getPaint
      setTextColor(edge)
      paint.setStyle(Paint.Style.STROKE)
      paint.setStrokeJoin(Paint.Join.ROUND)
      paint.setStrokeWidth(stroke * 2f)
      super.onDraw(canvas)
      paint.setStyle(Paint.Style.FILL)
      paint.setStrokeWidth(0f)
      setTextColor(colours)
      super.onDraw(canvas)
      drawing = false
    }
}
