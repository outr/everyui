package everyui.render

import android.content.Context
import android.graphics.Canvas
import android.widget.ImageView

/** A picture node's view: an `ImageView` that can draw itself blurred before Android 12, which has no RenderEffect
  * ([[SoftBlur]]). */
final class PictureView(context: Context) extends ImageView(context) {
  private var soft: SoftBlur = null

  def blur(sigma: Double): Unit = {
    if (soft == null) soft = new SoftBlur(this, c => drawSharp(c))
    soft.set(sigma)
  }

  private def drawSharp(canvas: Canvas): Unit = super.onDraw(canvas)

  override def onDraw(canvas: Canvas): Unit =
    if (soft != null && soft.on) soft.draw(canvas) else super.onDraw(canvas)

  // a new picture, tint or size is asked for through here, and the blurred one has to be taken again
  override def invalidate(): Unit = {
    if (soft != null && soft.on) soft.changed()
    super.invalidate()
  }
}
